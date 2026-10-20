"""CNN reproducible. Ejecutar desde cualquier carpeta; resultados reales en resultados/."""
from pathlib import Path
import os
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL', '2')
os.environ.setdefault('TF_ENABLE_ONEDNN_OPTS', '0')
os.environ.setdefault('MPLCONFIGDIR', str(Path(__file__).resolve().parents[1]/'.tools/matplotlib'))
os.environ.setdefault('KERAS_HOME', str(Path(__file__).resolve().parents[1]/'.tools/keras'))
import argparse, csv, hashlib, json, random, shutil
import numpy as np
from PIL import Image, ImageOps
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score

ROOT = Path(__file__).resolve().parents[1]
SIZE = 96
SEED = 42

def load_data():
    records, seen, duplicates, invalid = [], {}, [], []
    for path in sorted((ROOT / 'dataset/original').rglob('*')):
        if path.suffix.lower() not in ('.png', '.jpg', '.jpeg', '.bmp'):
            continue
        label = 0 if 'non_fire_images' in path.parts else 1 if 'fire_images' in path.parts else None
        if label is None:
            continue
        try:
            with Image.open(path) as im:
                im = ImageOps.exif_transpose(im).convert('RGB')
                digest = hashlib.sha256(str(im.size).encode() + im.tobytes()).hexdigest()
                array = np.asarray(im.resize((SIZE, SIZE), Image.Resampling.BILINEAR), dtype=np.float32) / 255.
            if digest in seen:
                if seen[digest]['label'] != label:
                    raise ValueError('Duplicado con etiquetas contradictorias')
                duplicates.append(str(path.relative_to(ROOT)))
                continue
            record = {'path': str(path.relative_to(ROOT)), 'label': label, 'sha256_pixels': digest}
            seen[digest] = record
            records.append((record, array))
        except (OSError, Image.DecompressionBombError) as error:
            invalid.append({'path': str(path), 'error': str(error)})
    if len(records) < 20:
        raise RuntimeError('Dataset ausente o insuficiente: ejecutar herramientas/descargar_dataset.py')
    return records, duplicates, invalid

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--epochs', type=int, default=25)
    args = parser.parse_args()
    tf.keras.utils.set_random_seed(SEED)
    tf.config.experimental.enable_op_determinism()
    tf.config.threading.set_intra_op_parallelism_threads(4)
    tf.config.threading.set_inter_op_parallelism_threads(2)
    out = ROOT / 'resultados/v1_reentrenamiento'
    out.mkdir(parents=True,exist_ok=True)
    records, duplicates, invalid = load_data()
    x = np.stack([r[1] for r in records])
    y = np.array([r[0]['label'] for r in records])
    train, test = train_test_split(np.arange(len(y)), test_size=.15, stratify=y, random_state=SEED)
    train, val = train_test_split(train, test_size=.15/.85, stratify=y[train], random_state=SEED)
    splits = {int(i): name for name, indices in [('train', train), ('validation', val), ('test', test)] for i in indices}
    with (out / 'particiones.csv').open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=['path','label','sha256_pixels','split'])
        writer.writeheader()
        writer.writerows(dict(r[0], split=splits[i]) for i, r in enumerate(records))
    (out / 'auditoria_dataset.json').write_text(json.dumps({'valid_unique':len(y), 'duplicates_excluded':duplicates, 'invalid':invalid,
        'classes':{'sin_fuego':int(sum(y==0)), 'fuego':int(sum(y==1))}}, indent=2), encoding='utf-8')
    model = tf.keras.Sequential([
        tf.keras.layers.Input((SIZE,SIZE,3)),
        tf.keras.layers.RandomFlip('horizontal', seed=SEED),
        tf.keras.layers.RandomRotation(.05, seed=SEED),
        tf.keras.layers.Conv2D(16,3,activation='relu',padding='same'), tf.keras.layers.MaxPooling2D(),
        tf.keras.layers.Conv2D(32,3,activation='relu',padding='same'), tf.keras.layers.MaxPooling2D(),
        tf.keras.layers.Conv2D(64,3,activation='relu',padding='same'), tf.keras.layers.MaxPooling2D(),
        tf.keras.layers.Flatten(), tf.keras.layers.Dense(64,activation='relu'), tf.keras.layers.Dropout(.4, seed=SEED),
        tf.keras.layers.Dense(1,activation='sigmoid')], name='BosqueAlertaCNN')
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3), loss='binary_crossentropy', metrics=['accuracy'])
    weights = {k:len(train)/(2*sum(y[train]==k)) for k in [0,1]}
    history = model.fit(x[train],y[train], validation_data=(x[val],y[val]), batch_size=32,
        epochs=args.epochs, class_weight=weights, callbacks=[
            tf.keras.callbacks.EarlyStopping(monitor='val_loss',patience=5,restore_best_weights=True),
            tf.keras.callbacks.CSVLogger(str(out/'historial.csv'))], verbose=2)
    models = ROOT / 'modelos/v1_reentrenamiento'
    models.mkdir(parents=True,exist_ok=True)
    model.save(models/'bosque_alerta.keras')
    probabilities = model.predict(x[test], verbose=0).ravel()
    predictions = (probabilities >= .5).astype(int)
    report = classification_report(y[test],predictions,target_names=['sin_fuego','fuego'],output_dict=True,zero_division=0)
    report.update({'confusion_matrix':confusion_matrix(y[test],predictions).tolist(), 'roc_auc':float(roc_auc_score(y[test], probabilities)),
        'threshold':.5, 'seed':SEED, 'size':SIZE, 'split_counts':{'train':len(train),'validation':len(val),'test':len(test)},
        'epochs_executed':len(history.history['loss']), 'tensorflow':tf.__version__, 'parameters':model.count_params()})
    # Conversión mediante función concreta para evitar incompatibilidad de variables Keras 3.
    @tf.function(input_signature=[tf.TensorSpec([1,SIZE,SIZE,3],tf.float32)])
    def serve(images):
        return model(images,training=False)
    from tensorflow.python.framework.convert_to_constants import convert_variables_to_constants_v2
    frozen = convert_variables_to_constants_v2(serve.get_concrete_function())
    converter = tf.lite.TFLiteConverter.from_concrete_functions([frozen])
    blob = converter.convert()
    (models/'bosque_alerta.tflite').write_bytes(blob)
    interpreter = tf.lite.Interpreter(model_content=blob)
    interpreter.allocate_tensors()
    inp, output = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    lite = []
    for sample in x[test]:
        interpreter.set_tensor(inp['index'], sample[None].astype('float32'))
        interpreter.invoke()
        lite.append(float(interpreter.get_tensor(output['index'])[0,0]))
    report['tflite_max_absolute_error'] = float(np.max(np.abs(probabilities-np.array(lite))))
    report['tflite_prediction_agreement'] = float(np.mean((np.array(lite)>=.5)==predictions))
    (out/'metricas.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    with (out/'predicciones_test.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.writer(f); w.writerow(['path','actual','probability_fire','predicted'])
        w.writerows((records[i][0]['path'],int(y[i]),float(p),int(v)) for i,p,v in zip(test,probabilities,predictions))
    # La versión binaria es una línea base histórica; no reemplaza el modelo Android v2.
    (models/'contrato.json').write_text(json.dumps({'input':[1,SIZE,SIZE,3], 'dtype':'float32','normalization':'RGB / 255.0',
       'resize':'bilinear stretch','output':'P(fuego), sigmoid','threshold':.5,'labels':{'0':'sin_fuego','1':'fuego'}},indent=2),encoding='utf-8')
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(1,2,figsize=(10,4))
    axes[0].plot(history.history['loss'],label='Entrenamiento'); axes[0].plot(history.history['val_loss'],label='Validación')
    axes[0].set(xlabel='Época (desde 0)',ylabel='Entropía cruzada'); axes[0].legend()
    axes[1].imshow(report['confusion_matrix'],cmap='Oranges')
    for a in range(2):
        for b in range(2): axes[1].text(b,a,str(report['confusion_matrix'][a][b]),ha='center')
    axes[1].set(xticks=[0,1],yticks=[0,1],xticklabels=['Sin fuego','Fuego'],yticklabels=['Sin fuego','Fuego'],xlabel='Predicción',ylabel='Real')
    fig.tight_layout(); fig.savefig(out/'evaluacion.png',dpi=160); plt.close(fig)
    print(json.dumps(report,indent=2))

if __name__ == '__main__': main()
