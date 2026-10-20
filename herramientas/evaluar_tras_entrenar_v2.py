"""Precalienta dependencias y espera una ejecución nueva antes de evaluar recortes."""
from pathlib import Path
import os,sys,time
ROOT=Path(__file__).resolve().parents[1]
target=ROOT/'resultados/v2/metricas.json'
initial=target.stat().st_mtime_ns if target.exists() else 0
os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL','2')
os.environ.setdefault('MPLCONFIGDIR',str(ROOT/'.tools/matplotlib'))
import tensorflow
deadline=time.monotonic()+1800
while not target.exists() or target.stat().st_mtime_ns<=initial:
    if time.monotonic()>deadline: raise TimeoutError('No se encontró una ejecución nueva del entrenamiento')
    time.sleep(2)
sys.path.insert(0,str(ROOT/'entrenamiento'))
from evaluar_regiones_v2 import main
main()
