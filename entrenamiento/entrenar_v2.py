"""CNN multietiqueta por transferencia. Casos del usuario excluidos del ajuste.

Entrada RGB [0,1], 160x160; salidas independientes [fuego,humo].
El backbone convolucional MobileNetV2 conserva pesos ImageNet; se entrena la cabeza.
"""
from pathlib import Path
import os
ROOT=Path(__file__).resolve().parents[1]
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
os.environ.setdefault('TF_ENABLE_ONEDNN_OPTS','0')
os.environ.setdefault('MPLCONFIGDIR',str(ROOT/'.tools/matplotlib'))
os.environ.setdefault('KERAS_HOME',str(ROOT/'.tools/keras'))
import argparse, csv, hashlib, json, shutil
import numpy as np
from PIL import Image, ImageOps
import tensorflow as tf
from sklearn.model_selection import GroupShuffleSplit
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score, precision_recall_curve

SIZE=160
SEED=142
OUT=ROOT/'resultados/v2'
CACHE=ROOT/'.tools/v2'

def load_image(path):
    with Image.open(path) as im:
        return np.asarray(ImageOps.exif_transpose(im).convert('RGB').resize((SIZE,SIZE),Image.Resampling.BILINEAR),dtype=np.float32)/255.

def scan():
    records=[]; seen={}; audit={'exact_duplicates':[], 'invalid':[]}
    c4=ROOT/'dataset/complementario/forest_fire_c4/original'
    # La categoría "fire" no garantiza ausencia de humo: su etiqueta de humo es desconocida.
    mapping={'fire':(1,-1),'nofire':(0,0),'smoke':(0,1),'smokefire':(1,1)}
    items=[]
    for p in sorted(c4.rglob('*')):
        if p.parent.name in mapping: items.append((p,'forest_fire_c4',mapping[p.parent.name],p.parent.name))
    for p in sorted((ROOT/'dataset/complementario/time_of_day/original').rglob('*')):
        if p.parent.name in ['daytime','nighttime','sunrise']: items.append((p,'time_of_day',(0,0),p.parent.name))
    for p in sorted((ROOT/'dataset/original').rglob('*')):
        if p.parent.name=='fire_images': items.append((p,'fire_dataset',(1,-1),'fire'))
        elif p.parent.name=='non_fire_images': items.append((p,'fire_dataset',(0,-1),'nofire'))
    for p,source,labels,category in items:
        if p.suffix.lower() not in ['.png','.jpg','.jpeg']: continue
        try:
            with Image.open(p) as im:
                im=ImageOps.exif_transpose(im).convert('RGB')
                digest=hashlib.sha256(str(im.size).encode()+im.tobytes()).hexdigest()
            if digest in seen:
                audit['exact_duplicates'].append({'path':str(p.relative_to(ROOT)),'kept':seen[digest]}); continue
            rel=p.relative_to(ROOT).as_posix()
            seen[digest]=rel
            records.append({'path':rel,'source':source,'category':category,'fire':labels[0],'smoke':labels[1],'sha256_pixels':digest})
        except OSError as e: audit['invalid'].append({'path':str(p),'error':str(e)})
    if len(records)<4000: raise RuntimeError('Descargar datasets complementarios primero')
    return records,audit

def backbone_model():
    base=tf.keras.applications.MobileNetV2(input_shape=(SIZE,SIZE,3),alpha=.5,include_top=False,weights=str(ROOT/'modelos/pesos/mobilenet_v2_0.5_160_no_top.h5'))
    base.trainable=False
    inp=tf.keras.Input((SIZE,SIZE,3),name='rgb_0_1')
    features=base(tf.keras.layers.Rescaling(2.,offset=-1.)(inp),training=False)
    pooled=tf.keras.layers.Concatenate()([tf.keras.layers.GlobalAveragePooling2D()(features),tf.keras.layers.GlobalMaxPooling2D()(features)])
    return tf.keras.Model(inp,pooled,name='mobilenet_features')

def extract(extractor,records):
    key=hashlib.sha256(json.dumps(records,sort_keys=True).encode()).hexdigest()[:16]
    target=CACHE/f'features_{key}.npy'
    if target.exists(): return np.load(target)
    # Las etiquetas no cambian los tensores de características; reutilizar la extracción
    # anterior cuando solo se ha corregido humo desconocido en la categoría fire.
    previous=[dict(r,smoke=0) if r['source']=='forest_fire_c4' and r['category']=='fire' else r for r in records]
    prior_key=hashlib.sha256(json.dumps(previous,sort_keys=True).encode()).hexdigest()[:16]
    prior=CACHE/f'features_{prior_key}.npy'
    if prior.exists():
        print('Reutilizando características; se corrigen etiquetas de humo, no píxeles.',flush=True)
        result=np.load(prior); np.save(target,result); return result
    result=[]
    for i in range(0,len(records),32):
        batch=np.stack([load_image(ROOT/r['path']) for r in records[i:i+32]])
        result.append(extractor(batch,training=False).numpy())
        if i%320==0: print(f'Características {i}/{len(records)}',flush=True)
    features=np.concatenate(result)
    np.save(target,features)
    return features

def group_features(features,reference_features):
    # Agrupar variantes muy próximas antes de dividir. No garantiza separación por evento.
    normalized=features/(np.linalg.norm(features,axis=1,keepdims=True)+1e-8)
    refs=reference_features/(np.linalg.norm(reference_features,axis=1,keepdims=True)+1e-8)
    exclude=(normalized@refs.T).max(axis=1)>=.97
    n=len(features); parents=list(range(n))
    def find(i):
        while parents[i]!=i: parents[i]=parents[parents[i]]; i=parents[i]
        return i
    for start in range(0,n,256):
        similarity=normalized[start:start+256]@normalized.T
        a,b=np.where(similarity>=.985)
        for i,j in zip(a+start,b):
            if i<j:
                ai,bj=find(int(i)),find(int(j))
                if ai!=bj: parents[bj]=ai
    groups=np.array([find(i) for i in range(n)])
    excluded_groups=set(groups[exclude])
    keep=np.array([g not in excluded_groups for g in groups])
    return groups,keep

def masked_loss(y_true,y_pred):
    mask=tf.cast(y_true>=0,tf.float32)
    target=tf.maximum(y_true,0.)
    loss=tf.keras.backend.binary_crossentropy(target,y_pred)*mask
    return tf.reduce_sum(loss,axis=-1)/tf.maximum(tf.reduce_sum(mask,axis=-1),1.)

def metrics_for(y,probs,thresholds):
    result={}
    for k,name in enumerate(['fuego','humo']):
        valid=y[:,k]>=0
        truth=y[valid,k].astype(int); p=probs[valid,k]; pred=(p>=thresholds[k]).astype(int)
        result[name]={'classification_report':classification_report(truth,pred,output_dict=True,zero_division=0),
          'confusion_matrix':confusion_matrix(truth,pred,labels=[0,1]).tolist(),'roc_auc':float(roc_auc_score(truth,p)), 'threshold':thresholds[k], 'samples':int(valid.sum())}
    return result

def main():
    p=argparse.ArgumentParser(); p.add_argument('--epochs',type=int,default=50); args=p.parse_args()
    tf.keras.utils.set_random_seed(SEED)
    tf.config.threading.set_intra_op_parallelism_threads(4)
    tf.config.threading.set_inter_op_parallelism_threads(2)
    OUT.mkdir(parents=True,exist_ok=True); CACHE.mkdir(parents=True,exist_ok=True)
    records,audit=scan()
    extractor=backbone_model()
    features=extract(extractor,records)
    refs=sorted((ROOT/'dataset/regresion_usuario').glob('*.png'))
    ref_features=extractor(np.stack([load_image(p) for p in refs]),training=False).numpy()
    groups,keep=group_features(features,ref_features)
    audit['reference_similar_excluded']=[r['path'] for r,k in zip(records,keep) if not k]
    records=[r for r,k in zip(records,keep) if k]; features=features[keep]; groups=groups[keep]
    y=np.array([[r['fire'],r['smoke']] for r in records],dtype=np.float32)
    trainval,test=next(GroupShuffleSplit(n_splits=1,test_size=.15,random_state=SEED).split(features,groups=groups))
    local_train,local_val=next(GroupShuffleSplit(n_splits=1,test_size=.15/.85,random_state=SEED).split(features[trainval],groups=groups[trainval]))
    train,val=trainval[local_train],trainval[local_val]
    split={int(i):name for name,ids in [('train',train),('validation',val),('test',test)] for i in ids}
    with (OUT/'particiones.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=list(records[0])+['group','split']); w.writeheader()
        w.writerows(dict(r,group=int(groups[i]),split=split[i]) for i,r in enumerate(records))
    audit.update({'valid_unique':len(records),'groups':len(set(groups.tolist())),'group_similarity':.985,'reference_exclusion_similarity':.97,
      'counts':{name:int(len(ids)) for name,ids in [('train',train),('validation',val),('test',test)]},'sources':{s:sum(r['source']==s for r in records) for s in set(r['source'] for r in records)}})
    (OUT/'auditoria.json').write_text(json.dumps(audit,indent=2),encoding='utf-8')
    head=tf.keras.Sequential([tf.keras.Input((features.shape[1],)),tf.keras.layers.LayerNormalization(),
      tf.keras.layers.Dense(128,activation='relu',kernel_regularizer=tf.keras.regularizers.l2(.001)),tf.keras.layers.Dropout(.4),tf.keras.layers.Dense(2,activation='sigmoid')],name='fire_smoke_head')
    head.compile(optimizer=tf.keras.optimizers.Adam(.001),loss=masked_loss)
    hist=head.fit(features[train],y[train],validation_data=(features[val],y[val]),epochs=args.epochs,batch_size=64,verbose=2,
      callbacks=[tf.keras.callbacks.EarlyStopping(monitor='val_loss',patience=7,restore_best_weights=True),tf.keras.callbacks.CSVLogger(str(OUT/'historial.csv'))])
    validation=head(features[val],training=False).numpy()
    # Umbrales fijados SOLO con validación. F1, con suelo 0.5 para evitar alertas por evidencia débil.
    thresholds=[]
    for k in range(2):
        valid=y[val,k]>=0
        candidates=np.arange(.50,.91,.025)
        scores=[]
        for threshold in candidates:
            truth=y[val[valid],k]; pred=validation[valid,k]>=threshold
            tp=np.sum((truth==1)&pred); fp=np.sum((truth==0)&pred); fn=np.sum((truth==1)&~pred)
            scores.append(2*tp/max(2*tp+fp+fn,1))
        thresholds.append(float(candidates[int(np.argmax(scores))]))
    model=tf.keras.Model(extractor.input,head(extractor.output),name='BosqueAlertaFireSmokeV2')
    stage=ROOT/'modelos/v2'; stage.mkdir(exist_ok=True)
    model.save(stage/'bosque_alerta.keras')
    probabilities=head(features[test],training=False).numpy()
    report={'version':'2.0','seed':SEED,'size':SIZE,'architecture':'MobileNetV2 alpha 0.5 ImageNet frozen + GAP/GMP + Dense128 + two sigmoid outputs',
      'parameters':model.count_params(),'trainable_parameters':head.count_params(),'epochs':len(hist.history['loss']),
      'split_counts':audit['counts'],'thresholds':thresholds,'validation':metrics_for(y[val],validation,thresholds),'test':metrics_for(y[test],probabilities,thresholds)}
    # Medir por separado los falsos positivos de amanecer/atardecer.
    for label,ids in [('test',test),('validation',val)]:
        sun=np.array([i for i in ids if records[i]['source']=='time_of_day' and records[i]['category']=='sunrise'],dtype=int)
        if len(sun):
            p_sun=head(features[sun],training=False).numpy()
            report[label+'_sunset']={'samples':len(sun),'fire_false_positives':int(np.sum(p_sun[:,0]>=thresholds[0])),'smoke_false_positives':int(np.sum(p_sun[:,1]>=thresholds[1]))}
    @tf.function(input_signature=[tf.TensorSpec([1,SIZE,SIZE,3],tf.float32)])
    def serve(images): return model(images,training=False)
    from tensorflow.python.framework.convert_to_constants import convert_variables_to_constants_v2
    frozen=convert_variables_to_constants_v2(serve.get_concrete_function())
    converter=tf.lite.TFLiteConverter.from_concrete_functions([frozen])
    blob=converter.convert(); (stage/'bosque_alerta.tflite').write_bytes(blob)
    engine=tf.lite.Interpreter(model_content=blob,num_threads=4); engine.allocate_tensors()
    inp=engine.get_input_details()[0]; output=engine.get_output_details()[0]
    def lite(image):
        engine.set_tensor(inp['index'],image[None].astype(np.float32)); engine.invoke(); return engine.get_tensor(output['index'])[0].copy()
    # Comprobar todas las entradas test, no solo tres ejemplos.
    lite_predictions=[]
    for j,i in enumerate(test):
        lite_predictions.append(lite(load_image(ROOT/records[i]['path'])))
        if j%200==0: print(f'TFLite test {j}/{len(test)}',flush=True)
    lite_predictions=np.array(lite_predictions)
    report['tflite_max_absolute_error']=float(np.max(np.abs(lite_predictions-probabilities)))
    report['tflite_label_agreement']=float(np.mean((lite_predictions>=thresholds)==(probabilities>=thresholds)))
    regression=[]
    # Solo ahora se consultan las predicciones de los ejemplos del usuario.
    baseline=ROOT/'modelos/v1/bosque_alerta.tflite'
    if not baseline.exists(): baseline=ROOT/'.tools/respaldo_v1_5/modelos/bosque_alerta.tflite'
    previous=tf.lite.Interpreter(model_path=str(baseline)); previous.allocate_tensors()
    for ref in refs:
        im=load_image(ref); pred=lite(im)
        with Image.open(ref) as image: old=np.asarray(image.convert('RGB').resize((96,96),Image.Resampling.BILINEAR),np.float32)[None]/255.
        previous.set_tensor(previous.get_input_details()[0]['index'],old); previous.invoke()
        prior=float(previous.get_tensor(previous.get_output_details()[0]['index'])[0,0])
        regression.append({'file':ref.name,'old_fire_score':prior,'new_fire_score':float(pred[0]),'new_smoke_score':float(pred[1]),
          'expected_fire':ref.name!='atardecer.png','expected_smoke':ref.name!='atardecer.png','fire_result':bool(pred[0]>=thresholds[0]),'smoke_result':bool(pred[1]>=thresholds[1])})
    report['user_regression']=regression
    (OUT/'metricas.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    with (OUT/'predicciones_test.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.writer(f); w.writerow(['path','source','fire_actual','smoke_actual','fire_score','smoke_score'])
        w.writerows((records[i]['path'],records[i]['source'],int(y[i,0]),int(y[i,1]),float(p[0]),float(p[1])) for i,p in zip(test,lite_predictions))
    contract={'version':'2.0','input':[1,SIZE,SIZE,3],'dtype':'float32','normalization':'RGB / 255.0; rescaling [-1,1] inside model',
      'outputs':['fuego','humo'],'thresholds':thresholds,'uncertain_margin':.12,'scope':'independent visual scores, not severity or calibrated probabilities',
      'regional_analysis':'four overlapping 65 percent crops, experimental; not used for official global test metrics'}
    (stage/'contrato.json').write_text(json.dumps(contract,indent=2),encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(1,3,figsize=(13,4))
    axes[0].plot(hist.history['loss'],label='Entrenamiento'); axes[0].plot(hist.history['val_loss'],label='Validación'); axes[0].legend(); axes[0].set_title('Pérdida binaria en etiquetas conocidas')
    for k,name in enumerate(['fuego','humo']):
        cm=report['test'][name]['confusion_matrix']; axes[k+1].imshow(cm,cmap='Oranges'); axes[k+1].set_title(name.capitalize()); axes[k+1].set(xlabel='Predicción',ylabel='Real',xticks=[0,1],yticks=[0,1])
        for a in range(2):
            for b in range(2): axes[k+1].text(b,a,str(cm[a][b]),ha='center')
    fig.tight_layout(); fig.savefig(OUT/'evaluacion.png',dpi=160); plt.close(fig)
    print(json.dumps(report,indent=2),flush=True)

if __name__=='__main__': main()
