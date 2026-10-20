# Validación de Bosque Alerta 2.1

Fecha: 19 de octubre de 2026. Aplicación `pe.edu.bosquealerta`, versionCode 8.

La revisión 2.1 recupera los porcentajes visibles de fuego y humo en fotografías y cámara en vivo. Muestra un decimal y aclara que son puntajes independientes de la imagen completa, sin medir área ni gravedad. Se conservan el modelo, los umbrales y las decisiones de la revisión 2.0. Las siete pruebas instrumentadas descritas más abajo corresponden a esa revisión del clasificador; no se repitieron para este cambio de presentación.

## Modelo y datos

Se sustituyó la CNN binaria por MobileNetV2 alpha 0.5, con pesos ImageNet congelados y cabeza entrenada para puntajes independientes de fuego y humo. Entrada 160 × 160 RGB float32 en [0,1]. El contrato de preprocesamiento y los umbrales se incluyen en el APK y se verifican contra los artefactos de modelos/.

Se utilizaron 8383 imágenes, con 5864 de entrenamiento, 1258 de validación y 1261 de prueba. La agrupación antes de dividir reduce duplicados muy próximos; no garantiza independencia por incendio ni por evento. Los umbrales se eligieron con validación: fuego 0.875 y humo 0.575. Las referencias del usuario y una imagen próxima se excluyeron del ajuste. Las referencias son regresiones de desarrollo, no un benchmark externo.

## Métricas del flujo de galería

Se evaluó la decisión completa, que combina la imagen global con cuatro recortes superpuestos:

| Señal | Precisión positiva | Recall positivo | F1 positivo | TN FP FN TP |
|---|---:|---:|---:|---|
| Fuego | 95.79 % | 95.79 % | 95.79 % | 766 / 20 / 20 / 455 |
| Humo | 95.04 % | 97.18 % | 96.10 % | 555 / 18 / 10 / 345 |

Las métricas de humo excluyen etiquetas desconocidas: 928 imágenes de prueba tienen etiqueta de humo utilizable. En 127 amaneceres/atardeceres reservados hubo 3 falsos positivos de fuego y ninguno de humo. No se presenta el problema de atardeceres como completamente resuelto.

La conversión Keras/TFLite se comprobó en todas las imágenes de prueba, con error máximo de 0.00001186 y coincidencia de etiquetas del 100 %. Los resultados completos y las predicciones individuales están en resultados/v2/.

## Comprobaciones Android realizadas

- `assembleDebug`, `assembleDebugAndroidTest` y `lintDebug`: correctos. Lint reportó 0 errores y 17 advertencias de mantenimiento.
- Se conserva el runtime LiteRT 1.4.2 y las funciones de cámara, galería y cámara en vivo.
- Instalación de app y APK de pruebas en AVD Medium Phone API 36.1, x86_64, iniciado temporalmente en modo de solo lectura.
- Ejecución real de `AndroidJUnitRunner` en la revisión 2.0 del clasificador: **OK (7 tests)**, duración 21.315 segundos. Evidencia en resultados/v2/android_instrumentation.txt.
- Pruebas con las tres imágenes del usuario usando Bitmap de Android y el intérprete incorporado: fuego lateral con humo, fuego extenso con humo, atardecer sin fuego ni humo.
- Prueba adicional con una imagen etiquetada de humo sin llamas; se verificó la categoría de humo sin atribuir fuego.
- Pruebas de mensajes para evidencia ambigua, humo independiente y señales regionales sin afirmar gravedad.
- La firma del APK fue verificada y el modelo/contrato incluidos coinciden byte a byte con los activos en modelos/.
- Manifest de la app: cámara y permiso interno de AndroidX. Sin permiso INTERNET ni lectura global de fotos.
- Sintaxis Python y de los tres cuadernos comprobada. Manifiesto SHA-256 regenerado con el verificador de entrega.

## Instalación

El APK está en `apk/entregables/BosqueAlerta-debug.apk`. La firma de depuración actual difiere de la del APK 1.5 que estaba en la carpeta al iniciar esta revisión. Si Android rechaza la actualización por conflicto de firmas, desinstalar esa versión anterior y luego instalar la nueva. Esto elimina permisos y cachés locales de esa instalación.

## Pendientes y límites

No se realizaron nuevas pruebas físicas de cámara, rendimiento o consumo de batería en un teléfono. Las cajas en vivo siguen siendo exploración de recortes con suavizado temporal y no un detector de objetos entrenado. No se mide superficie quemada ni gravedad. Niebla, nubes, luces y reflejos requieren más ejemplos y validación externa. Las condiciones del dataset C4 son CC BY-NC-SA 4.0; no tratar el conjunto ampliado como CC0.

La evidencia de la revisión previa se conservó en resultados/v1/VALIDACION_ENTREGA_v1_5.md. El informe principal original corresponde al modelo binario; el anexo vigente es informe/Actualizacion_Bosque_Alerta_v2.md.
