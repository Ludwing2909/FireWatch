"""Promueve artefactos evaluados; impide mezclar contrato y modelo incompatibles."""
from pathlib import Path
import json,shutil
ROOT=Path(__file__).resolve().parents[1]
def main():
    report=json.loads((ROOT/'resultados/v2/evaluacion_regiones.json').read_text())
    regression={r['file']:r for r in report['user_regression']}
    assert regression['fuego_lateral.png']['fire'], 'No se corrigió el caso lateral'
    assert regression['fuego_extenso.png']['fire'], 'Regresión en fuego extenso'
    assert not regression['atardecer.png']['fire'], 'Sigue el falso positivo del atardecer'
    assert not regression['atardecer.png']['smoke'], 'Atardecer confundido con humo'
    contract=json.loads((ROOT/'modelos/v2/contrato.json').read_text())
    assert contract['input']==[1,160,160,3] and contract['outputs']==['fuego','humo']
    baseline=ROOT/'modelos/v1'; baseline.mkdir(exist_ok=True)
    for name in ['bosque_alerta.keras','bosque_alerta.tflite','contrato.json']:
        source=ROOT/'.tools/respaldo_v1_5/modelos'/name
        if source.exists() and not (baseline/name).exists(): shutil.copy2(source,baseline/name)
    assets=ROOT/'apk/app/src/main/assets'
    for name in ['bosque_alerta.keras','bosque_alerta.tflite','contrato.json']:
        shutil.copy2(ROOT/'modelos/v2'/name,ROOT/'modelos'/name)
        if name.endswith(('.tflite','.json')): shutil.copy2(ROOT/'modelos/v2'/name,assets/name)
    print('Modelo v2 y contrato activados. Recompilar APK.')
if __name__=='__main__': main()
