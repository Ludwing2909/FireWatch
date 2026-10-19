# Cómo correr FireWatch (Bosque Alerta) y obtener la APK

> **Proyecto:** clasificador de imágenes (fuego / sin fuego) con CNN + TensorFlow,
> desplegado como app **Android nativa en Java** con TensorFlow Lite.
> **No usa** Flutter, React Native, ni Capacitor. **No necesita backend ni Internet** para funcionar.

---

## 0. Qué es y qué contiene

| Parte | Tecnología | Carpeta |
|---|---|---|
| Entrenamiento del modelo | Python 3.12 + TensorFlow 2.16.1 | `entrenamiento/`, `cuadernos/` |
| Modelo entrenado | Keras + TFLite (96×96×3) | `modelos/` |
| App móvil | Android nativo (Java 17) + TFLite 2.17.0 | `apk/` |
| APK ya compilada (incluida) | — | `apk/entregables/BosqueAlerta-debug.apk` |

> **Importante:** el modelo `apk/app/src/main/assets/bosque_alerta.tflite` **ya está en el repo**,
> así que **no es obligatorio re-entrenar** para obtener la APK. Solo entrena si quieres regenerar el modelo.

---

## 1. Requisitos previos (equipo Linux)

| Herramienta | Versión necesaria | Para qué |
|---|---|---|
| JDK | 17 o superior (probado con 21) | Compilar Gradle |
| Android SDK | Platform **android-36** + build-tools | Compilar la app |
| Internet | Solo en el 1er build | Descargar Gradle 9.5 y dependencias Maven |
| `adb` (opcional) | cualquiera | Instalar la APK en el teléfono |
| Python 3.12 (opcional) | 3.12 de 64 bits | Solo si vas a re-entrenar |

Verifica en tu terminal:

```bash
java -version                # debe ser 17+
ls ~/Android/Sdk/platforms   # debe aparecer android-36
```

---

## 2. Configurar el entorno (una sola vez)

Abre una terminal en la carpeta del proyecto. **Ojo: la ruta tiene espacios**, siempre entrecomíllala.

```bash
cd "/home/ccunurana/Documentos/Aprendizaje Profundo/ Proyecto Primera Unidad/FireWatch"

# JAVA_HOME → JDK 17 o superior
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# Android SDK (es donde ya tienes la plataforma android-36)
export ANDROID_HOME="$HOME/Android/Sdk"

# El gradlew del repo no tiene permiso de ejecución: dáselo
chmod +x apk/gradlew
```

> En Windows solo ejecuta `.\apk\compilar.ps1` (usa `gradlew.bat` y copia la APK automáticamente).

---

## 3. Compilar la APK

```bash
apk/gradlew -p apk assembleDebug
```

- **Primera vez:** tarda varios minutos porque descarga Gradle 9.5 (~130 MB) y las dependencias.
- Al final debe aparecer **`BUILD SUCCESSFUL`**.
- Si falla con *"Modelo ausente"*, significa que no existe
  `apk/app/src/main/assets/bosque_alerta.tflite` → ve a la sección 6 para entrenar.

### Resultado

La APK queda en:

```
apk/app/build/outputs/apk/debug/app-debug.apk
```

Cópiala al lugar de entrega (como hace el script de Windows):

```bash
cp apk/app/build/outputs/apk/debug/app-debug.apk apk/entregables/BosqueAlerta-debug.apk
```

> **APK de release firmada:** el proyecto **no tiene** `signingConfigs`, así que
> `assembleRelease` produciría un APK sin firmar. Para distribuir en producción hay que
> crear un keystore y añadir la firma en `apk/app/build.gradle` (el README lo advierte).
> Para evaluación académica la **debug autofirmada** es suficiente.

### Alternativa gráfica (sin terminal)

1. Abre **Android Studio** → *Open* → selecciona la carpeta `apk/`.
2. Android Studio crea solo `local.properties` con la ruta del SDK.
3. Panel *Gradle* → `:app` → `Tasks` → `build` → doble clic en **`assembleDebug`**.
4. La APK queda en la misma ruta (`app/build/outputs/apk/debug/`).

> Si prefieres CLI sin exportar variables, crea el archivo `apk/local.properties`
> (no está en el repo, está en `.gitignore`):
> `echo "sdk.dir=$HOME/Android/Sdk" > apk/local.properties`

---

## 4. Instalar y correr en el teléfono

**Opción A — con cable USB (recomendada):**

1. En el celular activa *Opciones de desarrollador* → **Depuración USB**.
2. Conecta el teléfono y acepta el aviso.
```bash
adb devices                                  # debe listar tu equipo
adb install -r apk/entregables/BosqueAlerta-debug.apk
```
3. Abre la app **"Bosque Alerta"** en el celular.

**Opción B — sin cable:** copia el archivo `BosqueAlerta-debug.apk` al teléfono
(por USB, correo, nube...) y tócalo para instalar, autorizando
*"Instalar apps de orígenes desconocidos"*.

**Requisito del celular:** Android 9 (API 28) o superior.

**Uso:** toca el botón → el selector del sistema abre → elige una foto →
la app muestra *fuego* o *sin fuego* (umbral 0.5). Funciona en modo avión.

**Pruebas manuales:** sigue la lista de `apk/PRUEBAS_MANUALES.md`.

---

## 5. (Opcional) Verificar la entrega

```bash
python herramientas/verificar_entrega.py
```

Comprueba que el `.tflite` embebido en el APK es idéntico al de `modelos/`
y regenera los checksums de `MANIFIESTO_ENTREGA.json`.

---

## 6. (Opcional) Re-entrenar el modelo desde cero

Solo si quieres regenerar el modelo. **Requiere Python 3.12** (el Python 3.14
de este equipo NO es compatible con TensorFlow 2.16.1):

```bash
cd "/home/ccunurana/Documentos/Aprendizaje Profundo/ Proyecto Primera Unidad/FireWatch"

# 1. Entorno virtual con Python 3.12
python3.12 -m venv .venv
source .venv/bin/activate

# 2. Dependencias
pip install -r requirements.txt

# 3. Descargar el dataset (Kaggle, ~1 min; requiere Internet)
python herramientas/descargar_dataset.py

# 4. Entrenar (semilla 42, parada temprana; copia el .tflite al APK automáticamente)
python entrenamiento/entrenar.py --epochs 25

# 5. Probar una imagen
python entrenamiento/predecir.py ruta/a/foto.jpg

# 6. Volver a compilar la APK (ahora con el modelo nuevo)
deactivate
apk/gradlew -p apk assembleDebug
cp apk/app/build/outputs/apk/debug/app-debug.apk apk/entregables/BosqueAlerta-debug.apk
```

> En Windows usa `py -3.12` y `.\.venv\Scripts\python.exe` en lugar de `python3.12` y `python`.

---

## 7. Resumen rápido (copiar y pegar)

```bash
cd "/home/ccunurana/Documentos/Aprendizaje Profundo/ Proyecto Primera Unidad/FireWatch"
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME="$HOME/Android/Sdk"
chmod +x apk/gradlew

apk/gradlew -p apk assembleDebug
cp apk/app/build/outputs/apk/debug/app-debug.apk apk/entregables/BosqueAlerta-debug.apk
adb install -r apk/entregables/BosqueAlerta-debug.apk
```

**La APK final:** `apk/entregables/BosqueAlerta-debug.apk` (~20 MB).

---

## Solución de problemas

| Problema | Solución |
|---|---|
| `Modelo ausente. Ejecutar entrenamiento/entrenar.py` | El `.tflite` no está en `apk/app/src/main/assets/`; entrena o restaura el archivo de `modelos/` |
| `JAVA_HOME is not set` / error de versión Java | `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk` (JDK 17+) |
| `SDK location not found` | `export ANDROID_HOME="$HOME/Android/Sdk"` o crea `apk/local.properties` con `sdk.dir=...` |
| `Failed to find target with hash string android-36` | Instala la plataforma: Android Studio → SDK Manager → marca *Android 16 (API 36)* |
| Primer build muy lento / fallo de red | Es normal: descarga Gradle y Maven; revisa tu conexión |
| `Permission denied` en `gradlew` | `chmod +x apk/gradlew` o invócalo con `sh apk/gradlew` |
| `adb: device not found` | Activa depuración USB y acepta el aviso en el teléfono |
| Ruta con espacios da error en scripts | Siempre entrecomilla la ruta completa |

## Compilar la revisión 1.5 en Windows

Requiere un JDK completo (recomendado 21; debe incluir `jlink`) y Android SDK 36.

```powershell
./apk/compilar.ps1 -Jdk "C:/ruta/al/jdk-21" -AndroidSdk "C:/ruta/al/Android/Sdk"
python herramientas/verificar_entrega.py
python herramientas/empaquetar.py
```

El script ejecuta compilación y lint antes de reemplazar el APK. El runtime Android actual es LiteRT 1.4.2, con la API Interpreter compatible con el modelo TFLite incluido. Ver `VALIDACION_ENTREGA.md` para las pruebas realizadas.
