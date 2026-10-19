# Pruebas en teléfono

Registrar dispositivo, versión Android, fecha y resultado. Estas pruebas requieren ejecución real; no marcarlas como aprobadas sin hacerlas.

| Prueba | Resultado esperado | Estado |
|---|---|---|
| Instalar APK en Android 9 o superior | Instala y abre | Pendiente |
| Seleccionar imagen de fuego | Muestra imagen, clase y puntaje | Pendiente |
| Seleccionar imagen sin fuego | Muestra resultado sin bloqueo | Pendiente |
| Cancelar selector | Mantiene la pantalla utilizable | Pendiente |
| Archivo no decodificable | Mensaje de error y permite reintentar | Pendiente |
| Modo avión | Clasifica sin Internet | Pendiente |
| Imagen vertical con orientación EXIF | Imagen orientada correctamente | Pendiente |
| Diez selecciones consecutivas | No se cierra ni bloquea | Pendiente |

Registrar los errores del clasificador como errores, incluso si la aplicación funciona. Un caso clasificado bien no demuestra precisión global.

## Rediseño 1.5

Estas pruebas siguen pendientes hasta ejecutarlas en un dispositivo o emulador.

| Prueba | Resultado esperado | Estado |
|---|---|---|
| Primera apertura | No pide permisos; icono de escudo, bosque y llama | Pendiente |
| Elegir galería en Android 9, 13 y 15+ | Selector de archivos, sin permiso global de imágenes | Pendiente |
| Cámara: aceptar permiso | Abre fotografía o vista en vivo según el botón | Pendiente |
| Cámara: negar y negar permanentemente | Galería utilizable; ofrece ajustes al volver a intentar | Pendiente |
| Cerrar cámara durante análisis, diez veces | Sin cierre inesperado del intérprete ni de la app | Pendiente |
| Regresar de cámara tras recreación de actividad | Conserva ruta de la captura pendiente | Pendiente |
| Fuente al 200 %, pantalla pequeña | Contenido desplazable y acciones accesibles | Pendiente |
| Android 15+, gestos y navegación con botones | Controles fuera de barras del sistema | Pendiente |
| TalkBack | Botones con nombre; resultado anunciado | Pendiente |
| Cuadrícula en vivo | Cajas alineadas con recortes; resultado marcado experimental | Pendiente |
| Acerca de y privacidad | Explica permisos, procesamiento local y limitaciones | Pendiente |

## Evidencia de emulador

El 19/10/2026 se verificaron arranque, galería sin permisos globales, denegación inicial y concesión de cámara, inferencia de PNG, cámara emulada y fuente al 200 % en Android 15. Detalles y limitaciones en `../VALIDACION_ENTREGA.md`. Las filas anteriores siguen pendientes de prueba física.
