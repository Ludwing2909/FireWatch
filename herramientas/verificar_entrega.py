"""Verifica integridad, exclusión de particiones y presencia del modelo en el APK."""
from pathlib import Path
import csv, hashlib, json, zipfile
ROOT=Path(__file__).resolve().parents[1]
def main():
    required=['dataset/procedencia.json','modelos/bosque_alerta.keras','modelos/bosque_alerta.tflite',
      'resultados/metricas.json','resultados/particiones.csv','cuadernos/01_exploracion.ipynb',
      'cuadernos/02_entrenamiento_evaluacion.ipynb','apk/entregables/BosqueAlerta-debug.apk',
      'informe/Informe_Bosque_Alerta.pdf','informe/Informe_Bosque_Alerta.md']
    for item in required:
        if not (ROOT/item).is_file(): raise RuntimeError('Falta '+item)
    with (ROOT/'resultados/particiones.csv').open(encoding='utf-8') as f: rows=list(csv.DictReader(f))
    hashes=[r['sha256_pixels'] for r in rows]
    assert len(hashes)==len(set(hashes)), 'Fuga de duplicados exactos'
    assert {r['split'] for r in rows}=={'train','validation','test'}
    for name in ['01_exploracion.ipynb','02_entrenamiento_evaluacion.ipynb']:
        nb=json.loads((ROOT/'cuadernos'/name).read_text(encoding='utf-8')); assert nb['nbformat']==4
    with zipfile.ZipFile(ROOT/'apk/entregables/BosqueAlerta-debug.apk') as z:
        assert z.read('assets/bosque_alerta.tflite')==(ROOT/'modelos/bosque_alerta.tflite').read_bytes()
    manifest={item:hashlib.sha256((ROOT/item).read_bytes()).hexdigest() for item in required}
    (ROOT/'MANIFIESTO_ENTREGA.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('Entrega verificada; modelo incluido en APK y particiones sin duplicados exactos.')
if __name__=='__main__': main()
