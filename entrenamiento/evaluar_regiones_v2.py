"""Evaluación del flujo completo (global + recortes) sin ajustar reglas al test."""
from pathlib import Path
import os
ROOT=Path(__file__).resolve().parents[1]
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
os.environ.setdefault('MPLCONFIGDIR',str(ROOT/'.tools/matplotlib'))
import csv,json
import numpy as np
from PIL import Image
from sklearn.metrics import classification_report,confusion_matrix
from politica_v2 import Predictor

def main():
    predictor=Predictor(ROOT/'modelos/v2/bosque_alerta.tflite',ROOT/'modelos/v2/contrato.json')
    with (ROOT/'resultados/v2/particiones.csv').open(encoding='utf-8') as f:
        rows=[r for r in csv.DictReader(f) if r['split']=='test']
    output=[]
    for i,row in enumerate(rows):
        with Image.open(ROOT/row['path']) as im: result=predictor.analyze(im)
        output.append(dict(row,**result))
        if i%100==0: print(f'Flujo completo {i}/{len(rows)}',flush=True)
    report={}
    # Evitar colisión de etiqueta original y decisión.
    for k,name in [(0,'fire'),(1,'smoke')]:
        valid=[(original,result) for original,result in zip(rows,output) if int(original[name])>=0]
        truth=[int(o[name]) for o,r in valid]; pred=[int(r[name]) for o,r in valid]
        report[name]={'classification_report':classification_report(truth,pred,output_dict=True,zero_division=0),
          'confusion_matrix':confusion_matrix(truth,pred,labels=[0,1]).tolist()}
    suns=[r for original,r in zip(rows,output) if original['source']=='time_of_day' and original['category']=='sunrise']
    report['sunset']={'samples':len(suns),'fire_false_positives':sum(r['fire'] for r in suns),'smoke_false_positives':sum(r['smoke'] for r in suns)}
    report['user_regression']=[]
    for path in sorted((ROOT/'dataset/regresion_usuario').glob('*.png')):
        with Image.open(path) as im: result=predictor.analyze(im)
        report['user_regression'].append({'file':path.name,**result})
    (ROOT/'resultados/v2/evaluacion_regiones.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    # Conservar etiqueta y predicción por separado en evidencia legible.
    saved=[]
    for original,result in zip(rows,output):
        saved.append({'path':original['path'],'fire_actual':int(original['fire']),'smoke_actual':int(original['smoke']),
          'fire_predicted':result['fire'],'smoke_predicted':result['smoke'],'fire_score':result['fire_score'],
          'smoke_score':result['smoke_score'],'fire_regions':result['fire_regions'],'uncertain':result['uncertain']})
    with (ROOT/'resultados/v2/predicciones_regiones.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=list(saved[0])); w.writeheader(); w.writerows(saved)
    print(json.dumps(report,indent=2),flush=True)

if __name__=='__main__': main()
