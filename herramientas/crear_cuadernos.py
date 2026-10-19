from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]
def md(text): return {'cell_type':'markdown','metadata':{},'source':text.splitlines(True)}
def code(text): return {'cell_type':'code','metadata':{},'execution_count':None,'outputs':[],'source':text.splitlines(True)}
def save(name,cells):
    d=ROOT/'cuadernos'; d.mkdir(exist_ok=True)
    for i,c in enumerate(cells): c['id']=f'cell-{i:03}'
    (d/name).write_text(json.dumps({'cells':cells,'metadata':{'kernelspec':{'display_name':'Python 3.12','language':'python','name':'python3'},'language_info':{'name':'python','version':'3.12'}},'nbformat':4,'nbformat_minor':5},indent=2,ensure_ascii=False),encoding='utf-8')
setup=code("from pathlib import Path\nimport os, sys, subprocess, json\nROOT=Path.cwd()\nif ROOT.name == 'cuadernos': ROOT=ROOT.parent\nassert (ROOT/'entrenamiento/entrenar.py').exists(), 'Ubicar el proyecto completo'\nos.chdir(ROOT)\n")
save('01_exploracion.ipynb',[
md('# Exploración del dataset de fuego\nEjecutar con Python 3.12 y requirements.txt. Revisar diversidad y posibles escenas repetidas antes de interpretar métricas.'),setup,
code("subprocess.run([sys.executable,'herramientas/descargar_dataset.py'],check=True)"),
code("from PIL import Image\nimport matplotlib.pyplot as plt\nfig,axes=plt.subplots(2,4,figsize=(12,6))\nfor row,name in enumerate(['non_fire_images','fire_images']):\n    paths=sorted((ROOT/'dataset/original/fire_dataset'/name).glob('*'))\n    print(name,len(paths))\n    for ax,path in zip(axes[row],paths[:4]):\n        with Image.open(path) as image: ax.imshow(image)\n        ax.set_title(path.name); ax.axis('off')\nplt.tight_layout()"),
md('## Revisión humana\n¿Hay humo sin llama? ¿Existen atardeceres o luces confundibles? ¿Se repite una misma escena? Registrar hallazgos y agrupar por escena si hay imágenes correlacionadas.')])
save('02_entrenamiento_evaluacion.ipynb',[
md('# Entrenamiento CNN y evaluación\nEl script guarda particiones, modelo, predicciones y métricas reales. Semilla 42; umbral predefinido 0.5. El conjunto test se reserva para la evaluación final.'),setup,
code("subprocess.run([sys.executable,'entrenamiento/entrenar.py','--epochs','25'],check=True)"),
code("metrics=json.loads((ROOT/'resultados/metricas.json').read_text())\nmetrics"),
code("from IPython.display import display, Image\ndisplay(Image(filename=str(ROOT/'resultados/evaluacion.png')))"),
code("import csv\nwith (ROOT/'resultados/predicciones_test.csv').open() as f:\n    errors=[r for r in csv.DictReader(f) if r['actual']!=r['predicted']]\nerrors[:20]"),
md('## Discusión\nExplicar falsos negativos, recall de fuego y sesgo del dataset. Contrastar conversión TFLite con Keras. El cuaderno conserva salidas vacías para que cada ejecución genere evidencia propia; consultar resultados/ para la ejecución entregada.')])
print('Dos cuadernos creados')
