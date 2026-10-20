# Validación de la revisión 1.5

Fecha: 19 de octubre de 2026. Aplicación: `pe.edu.bosquealerta`, versionCode 6.

## Comprobaciones realizadas

- `assembleDebug` y `lintDebug`: correctos con Microsoft OpenJDK 21, Gradle Wrapper 9.5.0 y Android SDK 36. Sin errores de lint; quedan 13 advertencias de mantenimiento.
- Runtime de inferencia actualizado a LiteRT 1.4.2 (API Interpreter). Desaparecieron las advertencias de alineación de 16 KB. No se probó ejecución en un dispositivo de páginas de 16 KB.
- `python herramientas/verificar_entrega.py`: correcto. Modelo incluido en el APK idéntico al exportado; particiones sin duplicados exactos; manifiesto SHA-256 regenerado.
- `git diff --check`: correcto.
- Instalación y arranque real en emulador AOSP Android 15, API 35, x86_64, perfil Pixel 6, 1080 × 2400.
- Pantalla inicial sin solicitud de permisos; permiso de cámara al pulsar su botón. Denegación inicial, explicación posterior y concesión verificadas.
- Selector de archivos sin permiso global de fotos; selección y decodificación de PNG, inferencia y resultado verificados.
- Cámara emulada: preview, inferencia continua y cajas visibles, aproximadamente 7 cuadros/s en esta máquina. Cierre mediante botón sin cierre inesperado observado. Esto no mide rendimiento en teléfono.
- Escala de fuente 200 %: botones crecen y permanecen accesibles mediante desplazamiento.
- Manifest del APK: CAMERA y permiso interno de receptor no exportado de AndroidX; sin INTERNET ni permisos de lectura global de fotos.
- Capturas reales en `capturas/redisenio/`.

## Hallazgo de precisión

La captura de pantalla usada como entrada de prueba obtuvo **90.4 % de puntaje de fuego**. Es un falso positivo fuera del dominio de entrenamiento; se registró como tal. La cámara emulada también produjo zonas sobre su patrón sintético. Estas pruebas verifican ejecución, no exactitud. No se reentrenó ni se modificó el modelo.

## Pendientes

Pruebas en teléfono físico, Android 9/13, denegación permanente, recuperación tras muerte del proceso, uso prolongado, TalkBack, orientación y dispositivo de 16 KB. Falta validar el modelo con imágenes externas. Consultar `apk/PRUEBAS_MANUALES.md`.

Advertencias restantes de lint: DataExtractionRules, DiscouragedApi, LockedOrientationActivity, ObsoleteSdkInt, Overdraw, SetTextI18n, UnusedResources. Incluyen orientación fija heredada, reglas de extracción de datos, recursos no usados, sobrepintado y textos dinámicos pendientes de externalizar. No se presenta lint como libre de advertencias.

## Integridad

APK: `apk/entregables/BosqueAlerta-debug.apk` (42.4 MB).

SHA-256: `0c14169461e725698b9dd33c830a916b25a32b5185015203131d8d6f9e6d7c01`.

Es una compilación de depuración firmada para evaluación académica. La distribución pública requiere firma de producción y las validaciones pendientes.

Referencia de la actualización: [LiteRT para Android, versiones de la API Interpreter](https://developers.google.com/edge/litert/android).
