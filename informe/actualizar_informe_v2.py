"""Anexo técnico de la revisión actual, calculado desde evidencia guardada."""
from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[1]

def main():
    metrics=json.loads((ROOT/'resultados/v2/metricas.json').read_text())
    regional=json.loads((ROOT/'resultados/v2/evaluacion_regiones.json').read_text())
    audit=json.loads((ROOT/'resultados/v2/auditoria.json').read_text())
    text=['# Actualización del modelo de fuego y humo versión 2\n',
      'Este anexo sustituye las descripciones del modelo binario y los resultados de la versión 1 para el APK actual. El informe original conserva valor como línea base histórica.\n',
      '## Problema y cambio implementado\n',
      'El modelo binario confundía un atardecer con fuego y no reconocía llamas laterales. Se reemplazó por una CNN MobileNetV2 alpha 0.5 con pesos ImageNet congelados, entrada de 160 × 160 píxeles y una cabeza multietiqueta de dos salidas sigmoides independientes. Se entrenó la cabeza con características promedio y máximas, capa densa de 128 unidades y dropout. No se entrenó un detector de objetos ni un estimador de gravedad.\n',
      '## Datos y evaluación\n',
      f'Se utilizaron {audit["valid_unique"]} imágenes únicas válidas. La auditoría registró {len(audit["exact_duplicates"])} duplicados exactos eliminados y {len(audit["reference_similar_excluded"])} exclusiones por proximidad a las referencias del usuario. La división por grupos de características produjo {audit["counts"]["train"]} imágenes de entrenamiento, {audit["counts"]["validation"]} de validación y {audit["counts"]["test"]} de prueba. Se ejecutaron {metrics["epochs"]} épocas.\n',
      'Fuentes: FIRE Dataset v1 (Ahmed Saied, CC0), Forest Fire C4 v2 (Obuli Sai Naren, CC BY-NC-SA 4.0) y Time Of Day v2 (Aymen Khouja; la ficha declara Unsplash License). Las escenas del último conjunto se usan como negativos débiles y requieren auditoría de contenido. La licencia del conjunto ampliado no es CC0.\n',
      'El humo desconocido en el dataset original y en la categoría fire de C4 se enmascara en la pérdida, sin inventar etiquetas negativas. Los umbrales se seleccionan únicamente con validación. Las tres imágenes del usuario se consultaron durante el desarrollo como regresión; no son una evaluación independiente.\n',
      'La agrupación de variantes muy próximas por coseno >= 0.985 reduce repetición, pero no demuestra independencia por evento. El dataset C4 ya incluye aumentos; hace falta revisar escenas manualmente y validar con nuevos incendios externos.\n',
      '## Resultados de clasificación global\n',
      '| Señal | Precisión positiva | Recall positivo | F1 positivo | Matriz TN FP FN TP |\n|---|---:|---:|---:|---|']
    for name in ['fuego','humo']:
        r=metrics['test'][name]; pos=r['classification_report']['1']; cm=r['confusion_matrix']
        text.append(f'| {name} | {pos["precision"]:.4f} | {pos["recall"]:.4f} | {pos["f1-score"]:.4f} | {cm[0][0]} / {cm[0][1]} / {cm[1][0]} / {cm[1][1]} |')
    text.extend(['\n## Resultados del flujo de galería con recortes\n',
      'Además de la imagen completa, se analizan cuatro recortes superpuestos del 65 % del ancho y alto. Se requiere el umbral global o al menos dos recortes con puntaje de fuego sobre un umbral más estricto. El resultado localizado describe evidencia en recortes, sin medir superficie ni gravedad. El humo se determina con la imagen completa.\n',
      '| Señal | Precisión positiva | Recall positivo | F1 positivo | Matriz TN FP FN TP |\n|---|---:|---:|---:|---|'])
    for key in ['fire','smoke']:
        r=regional[key]; pos=r['classification_report']['1']; cm=r['confusion_matrix']
        spanish={'fire':'fuego','smoke':'humo'}[key]
        text.append(f'| {spanish} | {pos["precision"]:.4f} | {pos["recall"]:.4f} | {pos["f1-score"]:.4f} | {cm[0][0]} / {cm[0][1]} / {cm[1][0]} / {cm[1][1]} |')
    sun=regional['sunset']
    text.extend(['\n',f'En {sun["samples"]} amaneceres/atardeceres reservados hubo {sun["fire_false_positives"]} falsas detecciones de fuego y {sun["smoke_false_positives"]} de humo. Estos errores se conservan en el informe; no se afirma que todos los atardeceres estén resueltos.\n',
      f'La comparación Keras/TFLite obtuvo un error absoluto máximo de {metrics["tflite_max_absolute_error"]:.8f} y coincidencia de etiquetas de {metrics["tflite_label_agreement"]:.2%} con los mismos tensores. Python y Android pueden diferir ligeramente por interpolación; las pruebas instrumentadas verifican los casos reportados dentro del runtime Android.\n',
      '## Casos del usuario\n',
      '| Imagen | Fuego | Humo | Recortes con señales de fuego |\n|---|---|---|---:|'])
    for r in regional['user_regression']:
        text.append(f'| {r["file"]} | {"Indicios" if r["fire"] else "Sin indicios claros"} | {"Posible humo" if r["smoke"] else "Evidencia insuficiente"} | {r["fire_regions"]} |')
    text.extend(['\n## Comunicación y límites\n',
      'La revisión de interfaz 2.1 muestra los puntajes independientes de fuego y humo como porcentajes con un decimal. Son puntajes de la imagen completa; no miden área quemada, intensidad ni certeza y pueden sumar más de 100 %. Los resultados distinguen fuego, posible humo, señales localizadas y evidencia no concluyente. El estado sin indicios no certifica seguridad.\n',
      'Las salidas describen imágenes, no confirman incendios forestales ni su ubicación. El humo puede confundirse con niebla o nubes; fuentes luminosas y reflejos siguen siendo negativos difíciles. Se requieren pruebas físicas de cámara y validación en escenas forestales de Puno. Las métricas internas no son una certificación operativa.\n',
      '## Reproducibilidad\n',
      'Consultar MEJORA_V2.md. La evidencia está en resultados/v2/, el modelo y contrato activos en modelos/, y las pruebas Android en apk/app/src/androidTest/. El cuaderno 03_mejora_fuego_humo.ipynb ejecuta el entrenamiento y la evaluación.\n'])
    (ROOT/'informe/Actualizacion_Bosque_Alerta_v2.md').write_text('\n'.join(text),encoding='utf-8')
    print('Anexo v2 actualizado con métricas reales')

if __name__=='__main__': main()
