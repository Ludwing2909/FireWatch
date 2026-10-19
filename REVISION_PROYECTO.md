# Revisión del proyecto — 19 de octubre de 2026

## Evaluación de la estructura

La organización general es adecuada para una entrega académica: separa datos, entrenamiento, cuadernos, modelos, resultados, aplicación e informe. No necesita backend para el funcionamiento actual. Se conservó esta estructura.

En Android se extrajo `FireClassifier.java`: carga, preprocesamiento RGB, normalización y ejecución TFLite compartidos por fotografía y cámara. Cada actividad posee su propio intérprete y lo cierra después de sus tareas. `WindowInsetsHelper.java` concentra la adaptación a barras del sistema. Los textos estáticos de las pantallas están en `res/values/strings.xml`, y la tipografía y botones en `styles.xml`.

## Diseño aplicado

Referencia: carpeta `Diseño`, especialmente `igniscare/DESIGN.md` e `inicio_dashboard_educativo/code.html`. Se adaptaron fondo marfil, superficies redondeadas, cabecera editorial, tarjetas, acento ámbar y verde azulado. Se conserva el nombre Bosque Alerta. Se utiliza la tipografía nativa de Android para funcionar sin descargar fuentes.

La portada presenta la tarea principal, selección de imagen, resultado, acceso a cámara en vivo y explicación de alcance/privacidad. El contenido se desplaza para admitir pantallas pequeñas y fuentes grandes. No se incorporan temperatura, riesgo ambiental ni porcentajes de protección ficticios de la referencia: el proyecto no dispone de esos datos.

Icono vectorial adaptativo original: escudo, árbol y llama; versiones normal, redonda y monocromática. Los vectores permiten escalado sin pérdida y no requieren imágenes remotas.

## Correcciones

- Eliminados READ_MEDIA_IMAGES y READ_EXTERNAL_STORAGE: ACTION_OPEN_DOCUMENT concede acceso al archivo elegido.
- Cámara solicitada al usarla; explicación y acceso a ajustes tras denegación permanente. Sin permiso de ubicación, micrófono, contactos o Internet.
- Ruta de captura pendiente conservada al recrearse la actividad.
- Botones de análisis deshabilitados si el modelo falla o hay una operación en curso.
- Cierre del intérprete en vivo en su ejecutor, después de las inferencias pendientes.
- Coordenadas de cajas convertidas de celdas a píxeles; antes el overlay recibía valores de 0 a 4 como si fueran píxeles.
- Cajas y textos identificados como estimaciones experimentales. Un clasificador de imágenes no equivale a un detector validado.

- Runtime Android actualizado a LiteRT 1.4.2; se corrigió la advertencia de compatibilidad con páginas de 16 KB.

## Pendientes para la entrega académica

El dataset original y su ZIP no están presentes en esta copia. Para reproducir el entrenamiento hay que descargarlos con `herramientas/descargar_dataset.py`. El modelo entrenado y sus resultados sí están incluidos.

1. Completar nombres, docente y fecha en la portada del informe y regenerar sus versiones si corresponde.
2. Ejecutar y registrar las pruebas de `apk/PRUEBAS_MANUALES.md` en teléfono, incluyendo denegación de permisos, cámara y accesibilidad.
3. Validar imágenes externas y revisar escenas casi duplicadas entre particiones. El control existente solo excluye duplicados exactos.
4. Mantener diferenciadas las métricas de clasificación de imágenes y la exploración por recortes en vivo; no hay métricas de localización.

## Antes de una distribución pública

Definir responsable de firma y resguardar la clave de producción, preparar política de privacidad y verificar compatibilidad en dispositivos reales. El APK de depuración sirve para evaluación académica. La licencia del dataset no determina automáticamente la licencia del código; el equipo debe decidirla.

## Verificación

Consultar `VALIDACION_ENTREGA.md` para el resultado de compilación y comprobaciones de esta revisión. Las pruebas no ejecutadas se mantienen expresamente pendientes.
