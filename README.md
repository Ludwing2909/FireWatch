# Bosque Alerta

Proyecto académico de clasificación de imágenes con una red neuronal convolucional para el caso de incendios forestales. Equipo de tres integrantes. La salida distingue **fuego** y **sin fuego**; no localiza llamas, no estima superficie quemada ni acredita que la escena sea forestal.

## Organización

| Carpeta | Contenido |
|---|---|
| `dataset/` | ZIP original, imágenes extraídas y procedencia |
| `entrenamiento/` | Código Python de entrenamiento y predicción |
| `cuadernos/` | Cuadernos Jupyter para explorar y ejecutar el flujo |
| `modelos/` | Modelo Keras, TensorFlow Lite y contrato de entrada |
| `resultados/` | Particiones, auditoría, métricas, curvas y predicciones |
| `apk/` | Proyecto Android, instrucciones y APK compilado |
| `informe/` | Informe académico y guía de exposición |
| `herramientas/` | Descarga y verificación |

## Reproducir el entrenamiento

Requiere Python 3.12 de 64 bits. El Python 3.14 de este equipo no es compatible con TensorFlow 2.16.1.

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe herramientas/descargar_dataset.py
.\.venv\Scripts\python.exe entrenamiento/entrenar.py --epochs 25
.\.venv\Scripts\python.exe entrenamiento/predecir.py ruta/a/foto.jpg
```

La semilla es 42 y la división estratificada es aproximadamente 70/15/15. Se eliminan duplicados exactos de píxeles antes de dividir. Imágenes casi iguales de una misma secuencia aún podrían cruzar particiones: revisar manualmente y agrupar por escena antes de afirmar generalización a nuevos incendios. El conjunto de prueba no se usa para elegir épocas ni el umbral, fijado en 0.5.

## Android

Abrir `apk/` en Android Studio, configurar SDK y compilar `assembleDebug`. El modelo se incorpora en `app/src/main/assets/` al entrenar. Android 9 o posterior; seleccionar fotografía mediante el selector del sistema. La aplicación no necesita Internet y no envía imágenes. El APK de depuración sirve para evaluación académica; una distribución pública necesita firma de producción y validación adicional en dispositivos.

El instalador está en `apk/entregables/BosqueAlerta-debug.apk`. Copiarlo al teléfono, abrirlo y autorizar la instalación desde esa fuente si Android lo solicita. Seleccionar una fotografía y esperar el resultado. También se puede instalar con `adb install -r apk/entregables/BosqueAlerta-debug.apk`.

Para compilar en PowerShell: `./apk/compilar.ps1`. Configurar `JAVA_HOME` con el JDK de Android Studio y `ANDROID_HOME` con el SDK local, o abrir el proyecto desde Android Studio. Gradle Wrapper está incluido. No se incluye local.properties porque las rutas dependen de cada equipo.

## Resultados de la ejecución entregada

Tras excluir 38 duplicados exactos quedaron 961 imágenes: 672 para entrenamiento, 144 para validación y 145 para prueba. Se ejecutaron 13 épocas con parada temprana y restauración del mejor modelo. Exactitud: **95.17 %**; precisión de fuego: **99.03 %**; recall de fuego: **94.44 %**. Matriz de confusión: 36 verdaderos negativos, 1 falso positivo, 6 falsos negativos y 102 verdaderos positivos. TFLite coincidió con las etiquetas de Keras en el 100 % del test. Son resultados internos del dataset; falta validación externa.

El informe está en `informe/Informe_Bosque_Alerta.pdf`, con fuente editable en `.md`. Para regenerarlo, instalar `python-docx` y `reportlab` y ejecutar `informe/generar_informe.py`; usa las métricas actuales. Completar los datos de portada antes de entregar.

La compilación del APK y la integridad del modelo se verificaron. Las pruebas de uso en un teléfono están pendientes y tienen una lista explícita en `apk/PRUEBAS_MANUALES.md`.

## Dataset y alcance

Ahmed Saied, FIRE Dataset, versión 1: https://www.kaggle.com/datasets/phylake1337/fire-dataset . Licencia declarada por Kaggle: CC0. Se conservaron la fuente y el ZIP. Las 999 imágenes originales están desbalanceadas (755 fuego y 244 sin fuego). El dataset no proporciona anotaciones de región ni garantiza representatividad de Puno. El puntaje de la CNN no es una probabilidad calibrada.

## Distribución sugerida del trabajo

1. Integrante 1: auditoría de datos, particiones, exploración y revisión de escenas similares.
2. Integrante 2: arquitectura CNN, entrenamiento, métricas y comparación de errores.
3. Integrante 3: Android, pruebas en teléfono y demostración.

Los tres revisan el informe, entienden las limitaciones y realizan la exposición. Completar nombres, docente, curso y fecha de entrega en el informe.

## Revisión de interfaz 1.5

Diseño adaptado de la referencia IgnisCare, icono vectorial propio y pantallas desplazables. La cámara solicita permiso al utilizarla; la galería usa el selector del sistema sin acceso global a imágenes. La inferencia compartida reside en `FireClassifier.java`.

Ver `REVISION_PROYECTO.md` para la evaluación de estructura y pendientes, `VALIDACION_ENTREGA.md` para comprobaciones y `apk/PRUEBAS_MANUALES.md` para pruebas en dispositivo. Las cajas del modo en vivo son una exploración experimental de recortes, no localización validada de llamas.
