# Mejora del análisis de fuego y humo

La revisión 2.0 responde a los fallos reportados al probar llamas laterales y un atardecer. Cambia el modelo y la forma de comunicar el resultado. El APK mantiene cámara, galería, modo en vivo, identidad visual y procesamiento sin conexión.

## Comportamiento

- **Indicios de fuego** o **Indicios de fuego y humo**: evidencia visual suficiente en la CNN.
- **Indicios localizados de fuego**: señales en una parte de los recortes explorados. No mide superficie quemada ni demuestra que un incendio sea leve.
- **Posible presencia de humo**: la salida de humo supera su umbral y la de fuego no lo hace.
- **Resultado no concluyente**: señales cercanas al umbral, sin decisión suficientemente fuerte.
- **Sin indicios claros de fuego ni humo**: evidencia insuficiente; no certifica seguridad.

La revisión 2.1 recupera los porcentajes visibles de **fuego** y **humo**, con un decimal, en galería, fotografías de cámara y modo en vivo. Son puntajes independientes de la imagen completa y pueden sumar más de 100 %. No representan porcentaje de área quemada, gravedad ni probabilidades calibradas. La decisión también puede incorporar recortes aunque el puntaje global sea bajo.

## Modelo

MobileNetV2 alpha 0.5 con pesos ImageNet congelados, entrada RGB float32 160 × 160 en [0,1]. El reescalado a [-1,1] está dentro de la red. Se concatenan pooling promedio y máximo, seguidos de una capa densa de 128 unidades, dropout y dos salidas sigmoides independientes: fuego y humo. Entrenamiento por transferencia: se ajusta la cabeza, no el backbone. Sigue siendo una CNN.

Las etiquetas de humo del dataset original y de la categoría `fire` del C4 son desconocidas y no se inventan: se ignoran en la función de pérdida, porque esas imágenes pueden mostrar humo además de llamas. Las categorías `smoke` y `smokefire` aportan positivos explícitos de humo; `nofire` aporta negativos. Los umbrales se eligen solo con validación. Las tres fotos del usuario no se utilizan para ajustar pesos ni umbrales: son casos de regresión consultados durante el desarrollo, no una validación independiente de generalización.

La galería analiza la imagen completa y cuatro recortes superpuestos del 65 % del ancho y alto. La decisión de fuego combina el puntaje global con al menos dos regiones sobre un umbral más estricto. El humo se decide con la imagen completa. La cámara usa cuatro celdas por cuadro y seguimiento temporal de recortes; las cajas no son anotaciones de objetos ni mediciones de tamaño real.

## Datos y licencias declaradas

1. Ahmed Saied, FIRE Dataset v1, Kaggle, CC0. Se conserva el dataset original.
2. Obuli Sai Naren, Forest Fire Image Classification Dataset v2, Kaggle, **CC BY-NC-SA 4.0**. Clases fuego, sin fuego, humo y humo con fuego. Contiene aumentos ya realizados; no asumir escenas independientes.
3. Aymen Khouja, Time Of Day Dataset v2, Kaggle. La ficha declara imágenes procedentes de Unsplash bajo Unsplash License. Se incorporan escenas diurnas, nocturnas y amaneceres/atardeceres como negativos débiles: estas etiquetas describen hora del día, no una anotación exhaustiva de ausencia de fuego. Revisar etiquetas antes de usos más amplios.
4. Pesos MobileNetV2 de la distribución oficial de Keras, preentrenados en ImageNet. Se conservan en `modelos/pesos/`.

Esta combinación es para la entrega académica. Conservar atribución y las condiciones no comerciales y de compartir igual del dataset C4. No presentar toda la colección ampliada como CC0.

Las tres imágenes del usuario están en `dataset/regresion_usuario/` y fuera del entrenamiento. Se excluyen también imágenes del dataset con características muy próximas a esas referencias. Se eliminan duplicados exactos y se agrupan variantes con similitud coseno >= 0.985 antes de dividir. Ese procedimiento reduce repetición, pero no garantiza independencia por evento: falta una agrupación manual de todas las escenas. No confundir exactitud interna con validación operativa en Puno.

## Reproducir

Con Python 3.12 y las dependencias del proyecto:

```powershell
python herramientas/descargar_mejora.py
python herramientas/descargar_mejora.py --ref aymenkhouja/timeofdaydataset --version 2 --slug time_of_day
python entrenamiento/entrenar_v2.py --epochs 50
python entrenamiento/evaluar_regiones_v2.py
python herramientas/activar_modelo_v2.py
./apk/compilar.ps1 -Jdk "C:/ruta/al/jdk" -AndroidSdk "C:/ruta/al/Android/Sdk"
```

En este equipo usar `.venv/Scripts/python.exe` para los scripts de entrenamiento. Descargar los pesos oficiales indicados en `modelos/pesos/procedencia.json` si no están incluidos. Las imágenes de regresión son necesarias para verificar la corrección específica y se excluyen automáticamente del ajuste.

## Evidencia

`resultados/v2/metricas.json` contiene métricas globales de fuego/humo y comparación Keras/TFLite; `evaluacion_regiones.json` contiene la evaluación del flujo de galería completo, incluyendo los recortes. `particiones.csv` registra fuente, etiquetas, hash, grupo y subconjunto. Las etiquetas de humo -1 se excluyen de las métricas de humo. Los resultados históricos de la versión binaria permanecen en `resultados/` y el modelo anterior en `modelos/v1/`.

Las pruebas de regresión Android están en `apk/app/src/androidTest/`. Evalúan las tres imágenes reportadas y la semántica de los mensajes. No demuestran que todos los atardeceres o incendios se clasifiquen correctamente. Las pruebas de cámara en un teléfono físico siguen siendo necesarias.

El anexo actualizado del informe está en `informe/Actualizacion_Bosque_Alerta_v2.md`. Los resultados con recortes fueron: recall de fuego **95.79 %**, precisión de fuego **95.79 %**, recall de humo **97.18 %** y precisión de humo **95.04 %**. Sobre 127 amaneceres/atardeceres reservados persistieron 3 falsos positivos de fuego y ninguno de humo. Son medidas internas sobre esta partición, con las limitaciones de repetición y etiquetado descritas arriba.

Para instalar: abrir `apk/entregables/BosqueAlerta-debug.apk` en el teléfono. La firma de depuración de esta compilación puede diferir de la instalación anterior; si Android indica conflicto de firmas, desinstalar primero la versión anterior y volver a instalar. Eso elimina los permisos y cachés locales de esa instalación. No hay cuentas ni datos enviados a un servidor.

Fuentes: https://www.kaggle.com/datasets/obulisainaren/forest-fire-c4 ; https://www.kaggle.com/datasets/aymenkhouja/timeofdaydataset ; https://www.tensorflow.org/api_docs/python/tf/keras/applications/MobileNetV2
