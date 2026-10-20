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
        contract=json.loads((ROOT/'modelos/contrato.json').read_text(encoding='utf-8'))
        if contract.get('version')=='2.0':
            assert z.read('assets/contrato.json')==(ROOT/'modelos/contrato.json').read_bytes()
    if contract.get('version')=='2.0':
        extra=['MEJORA_V2.md','modelos/contrato.json','resultados/v2/metricas.json',
          'resultados/v2/evaluacion_regiones.json','resultados/v2/particiones.csv','cuadernos/03_mejora_fuego_humo.ipynb',
          'informe/Actualizacion_Bosque_Alerta_v2.md','VALIDACION_ENTREGA.md','resultados/v2/android_instrumentation.txt']
        for item in extra:
            assert (ROOT/item).is_file(),item
        required.extend(extra)
        with (ROOT/'resultados/v2/particiones.csv').open(encoding='utf-8') as f: v2=list(csv.DictReader(f))
        groups={}
        for r in v2:
            assert 'regresion_usuario' not in r['path']
            groups.setdefault(r['group'],set()).add(r['split'])
        assert all(len(splits)==1 for splits in groups.values()),'Fuga entre grupos'
        assert len({r['sha256_pixels'] for r in v2})==len(v2),'Duplicados exactos v2'
        r=json.loads((ROOT/'resultados/v2/evaluacion_regiones.json').read_text())
        reference={x['file']:x for x in r['user_regression']}
        assert reference['fuego_lateral.png']['fire'] and reference['fuego_extenso.png']['fire']
        assert not reference['atardecer.png']['fire'] and not reference['atardecer.png']['smoke']
        assert 'OK (7 tests)' in (ROOT/'resultados/v2/android_instrumentation.txt').read_text(encoding='utf-8-sig')
    manifest={item:hashlib.sha256((ROOT/item).read_bytes()).hexdigest() for item in required}
    (ROOT/'MANIFIESTO_ENTREGA.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('Entrega verificada; modelo incluido en APK y particiones sin duplicados exactos.')
if __name__=='__main__': main()
