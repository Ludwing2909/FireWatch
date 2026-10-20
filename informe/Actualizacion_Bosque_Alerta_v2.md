# Actualización del modelo de fuego y humo versión 2

Este anexo sustituye las descripciones del modelo binario y los resultados de la versión 1 para el APK actual. El informe original conserva valor como línea base histórica.

## Problema y cambio implementado

El modelo binario confundía un atardecer con fuego y no reconocía llamas laterales. Se reemplazó por una CNN MobileNetV2 alpha 0.5 con pesos ImageNet congelados, entrada de 160 × 160 píxeles y una cabeza multietiqueta de dos salidas sigmoides independientes. Se entrenó la cabeza con características promedio y máximas, capa densa de 128 unidades y dropout. No se entrenó un detector de objetos ni un estimador de gravedad.

## Datos y evaluación

Se utilizaron 8383 imágenes únicas válidas. La auditoría registró 76 duplicados exactos eliminados y 1 exclusiones por proximidad a las referencias del usuario. La división por grupos de características produjo 5864 imágenes de entrenamiento, 1258 de validación y 1261 de prueba. Se ejecutaron 16 épocas.

Fuentes: FIRE Dataset v1 (Ahmed Saied, CC0), Forest Fire C4 v2 (Obuli Sai Naren, CC BY-NC-SA 4.0) y Time Of Day v2 (Aymen Khouja; la ficha declara Unsplash License). Las escenas del último conjunto se usan como negativos débiles y requieren auditoría de contenido. La licencia del conjunto ampliado no es CC0.

El humo desconocido en el dataset original y en la categoría fire de C4 se enmascara en la pérdida, sin inventar etiquetas negativas. Los umbrales se seleccionan únicamente con validación. Las tres imágenes del usuario se consultaron durante el desarrollo como regresión; no son una evaluación independiente.

La agrupación de variantes muy próximas por coseno >= 0.985 reduce repetición, pero no demuestra independencia por evento. El dataset C4 ya incluye aumentos; hace falta revisar escenas manualmente y validar con nuevos incendios externos.

## Resultados de clasificación global

| Señal | Precisión positiva | Recall positivo | F1 positivo | Matriz TN FP FN TP |
|---|---:|---:|---:|---|
| fuego | 0.9654 | 0.9411 | 0.9531 | 770 / 16 / 28 / 447 |
| humo | 0.9504 | 0.9718 | 0.9610 | 555 / 18 / 10 / 345 |

## Resultados del flujo de galería con recortes

Además de la imagen completa, se analizan cuatro recortes superpuestos del 65 % del ancho y alto. Se requiere el umbral global o al menos dos recortes con puntaje de fuego sobre un umbral más estricto. El resultado localizado describe evidencia en recortes, sin medir superficie ni gravedad. El humo se determina con la imagen completa.

| Señal | Precisión positiva | Recall positivo | F1 positivo | Matriz TN FP FN TP |
|---|---:|---:|---:|---|
| fuego | 0.9579 | 0.9579 | 0.9579 | 766 / 20 / 20 / 455 |
| humo | 0.9504 | 0.9718 | 0.9610 | 555 / 18 / 10 / 345 |


En 127 amaneceres/atardeceres reservados hubo 3 falsas detecciones de fuego y 0 de humo. Estos errores se conservan en el informe; no se afirma que todos los atardeceres estén resueltos.

La comparación Keras/TFLite obtuvo un error absoluto máximo de 0.00001186 y coincidencia de etiquetas de 100.00% con los mismos tensores. Python y Android pueden diferir ligeramente por interpolación; las pruebas instrumentadas verifican los casos reportados dentro del runtime Android.

## Casos del usuario

| Imagen | Fuego | Humo | Recortes con señales de fuego |
|---|---|---|---:|
| atardecer.png | Sin indicios claros | Evidencia insuficiente | 0 |
| fuego_extenso.png | Indicios | Posible humo | 4 |
| fuego_lateral.png | Indicios | Posible humo | 4 |

## Comunicación y límites

La revisión de interfaz 2.1 muestra los puntajes independientes de fuego y humo como porcentajes con un decimal. Son puntajes de la imagen completa; no miden área quemada, intensidad ni certeza y pueden sumar más de 100 %. Los resultados distinguen fuego, posible humo, señales localizadas y evidencia no concluyente. El estado sin indicios no certifica seguridad.

Las salidas describen imágenes, no confirman incendios forestales ni su ubicación. El humo puede confundirse con niebla o nubes; fuentes luminosas y reflejos siguen siendo negativos difíciles. Se requieren pruebas físicas de cámara y validación en escenas forestales de Puno. Las métricas internas no son una certificación operativa.

## Reproducibilidad

Consultar MEJORA_V2.md. La evidencia está en resultados/v2/, el modelo y contrato activos en modelos/, y las pruebas Android en apk/app/src/androidTest/. El cuaderno 03_mejora_fuego_humo.ipynb ejecuta el entrenamiento y la evaluación.
