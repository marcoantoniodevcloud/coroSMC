# Correcciones, OCR y revisión en Android

Estas funciones se compilan en `app`, dentro de la APK. OpenSongWeb sigue siendo una aplicación independiente.

## Corregir acordes

- En **Editar canto → Letra/acordes**, el botón de corrección (icono de ajustes) abre la lista de acciones. La primera convierte Do/Re/Mi/Fa/Sol/La/Si a C/D/E/F/G/A/B. El cambio queda en el editor, admite Deshacer y se conserva al guardar.
- En el **menú de cantos → Corregir todos los cantos**, la misma acción recorre los archivos ChordPro y OpenSong, incluidos los XML sin extensión. Conserva los metadatos, las letras y las posiciones de los acordes en las filas OpenSong. Los cambios se respaldan en `OpenSong/Backups/Corrections` antes de escribir y el lote puede detenerse.
- Se reconocen alteraciones, menores, séptimas, extensiones y bajos, como `Sol#m7` → `G#m7` y `Re/Fa#` → `D/F#`. No se intentan convertir palabras de la letra ni etiquetas desconocidas.

## OCR nativo con posiciones de acordes

Android reconoce las imágenes con **ML Kit**, que ya estaba integrado. El procesamiento nuevo asocia los acordes con la línea de letra siguiente a partir de las coordenadas de cada palabra y genera ChordPro antes de convertirlo a OpenSong. Conserva versos cortos con palabras como «Mi» y «La», acordes aislados y columnas cuando se identifica un espacio vacío claro entre ellas.

Este es un procesamiento nativo para Android; **no ejecuta Python ni el modelo RapidOCR/ONNX de OpenSongWeb**. No necesita el servidor web. Las posiciones OCR son estimaciones y deben revisarse. Un PDF con varios cantos no se separa automáticamente en archivos: conserva el flujo de revisión de resultados de la app.

- Individual: botón OCR del editor, tanto para PDF/imágenes como para cantos con una referencia del mismo nombre.
- General: **menú de cantos → Aplicar OCR a PDF e imágenes**. Procesa todas las páginas, conserva las referencias y omite los archivos que ya tienen letra asociada.
- El resultado individual se presenta para revisar antes de guardar. Los resultados vacíos o fallidos no reemplazan las letras.
- Las letras guardadas desde OCR quedan pendientes de revisión. Un reemplazo individual de un canto existente crea respaldo en `OpenSong/Backups/OCR`.

## Revisado y observaciones

El marcador de la lista permite alternar revisado/pendiente y persiste en el dispositivo. Los casos con observaciones del control incluido en la biblioteca predeterminada aparecen en rojo. Mantener pulsado el marcador rojo abre la observación; marcarlo revisado oculta el aviso.

Los estados del servidor web y los del dispositivo son independientes; no hay sincronización automática. Actualizar la APK tampoco reemplaza bibliotecas que el usuario ya haya importado.

## Validación

Las pruebas JVM cubren conversión ChordPro/OpenSong/XML, conservación de metadatos y posiciones, claves de revisión y asociación geométrica OCR. Las pruebas de dispositivo ejercitan ML Kit con una imagen generada y comprueban la persistencia del estado de revisión.

Compilar y ejecutar las pruebas locales:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Con un dispositivo o emulador conectado:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

APK de desarrollo: `app/build/outputs/apk/debug/app-debug.apk`.


## Secuencias de acordes con guiones

Se admiten grupos ChordPro como `[Rem-Lam]`, `[La#-Sol#-Sol]` y
`[Do/Sol–Rem/Fa]`. Los guiones `-`, `–` y `—` separan acordes únicamente
cuando todos los miembros son acordes válidos; se mantienen los modificadores
como `C-7`, las alteraciones y los bajos con `/`. Web y Android conservan el
grupo en la vista compartida. Android identifica sus miembros individualmente
para los diagramas y transpone cada raíz/bajo mediante el motor existente.
El OCR de ambos clientes también reúne grupos cuando el guion tiene su propia
caja. Los archivos ya guardados sin guiones necesitan revisión o reprocesamiento:
no se deducen secuencias nuevas a partir de acordes independientes.


## Lectura sin modificar los originales en Android

Abrir o indexar canciones convierte ChordPro/OnSong/TXT solo en el objeto Song
en memoria, conservando nombre, carpeta y tipo de archivo. La recuperación de
campos XML tampoco escribe el archivo. La indexación mantiene UUID/fechas en
la base de datos y no los inserta en los originales. El arranque no mueve
archivos a Import, no abre reparación automática ni muestra el aviso de versión.
Los documentos web y metadatos de la carpeta se ignoran; WebP se reconoce como
imagen. La biblioteca incluida se instala solo sobre una biblioteca vacía y
nunca sustituye ni borra archivos existentes durante una actualización.
Las operaciones explícitas de edición, importación y mantenimiento siguen siendo
acciones de escritura; la lectura no las invoca.
