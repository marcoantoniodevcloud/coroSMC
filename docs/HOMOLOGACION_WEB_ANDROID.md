# Vista de cantos compartida: web y Android

## Implementado

El motor `shared/song-view/melody.js` y la hoja `shared/song-view/song-view.css` se empaquetan directamente en ambos clientes. No hay copias mantenidas por separado. Web los incorpora en `processResources`; Android registra `shared` como carpeta de assets.

- Misma interpretación de letra, acordes y comentarios `melodia:`.
- Notas y acordes anclados al mismo fragmento de letra, con salto entre palabras.
- Acordes visibles/ocultos; melodía debajo, oculta o en panel separado por verso.
- Ajuste de tamaño y preferencias de vista conservadas localmente.
- Contenido tratado como texto, sin ejecutar HTML proporcionado en el canto.
- Aviso si la letra del comentario difiere del verso. Solo se toleran diferencias de espacios agregados por la conversión OpenSong.
- Compatibilidad con los paréntesis de notas que producía el conversor Android anterior; se conservan repeticiones como `(2)`.

## Android

En la pantalla de interpretación, el botón **Letra / acordes / melodía** abre la vista compartida. Usa los colores de fondo, texto, acordes y comentarios, y el tamaño configurado en la app móvil. Es una vista de lectura: cerrar devuelve a la hoja nativa existente y sus herramientas. No sustituye los modos de presentación, las anotaciones, el autoscroll ni la pantalla secundaria. No integra esas herramientas dentro de la vista compartida.

Los cantos nativos se convierten en memoria a ChordPro para mostrarlos; abrir la vista no escribe archivos. El conversor ahora conserva las marcas de melodía en los comentarios, mientras que mantiene el saneamiento previo de los ángulos en el resto del texto. La WebView solo carga assets locales, bloquea la red y no expone interfaces Java a JavaScript.

## Web

La vista previa usa el mismo motor y añade selección de hoja clara u oscura y tamaño. Conserva las funciones de edición, corrección, OCR y archivos existentes. La apariencia de la hoja comparte estructura y roles de color con móvil; los valores personalizados de Android no se sincronizan al navegador.

## Límites y diferencias que siguen presentes

Esta entrega homologa la vista de letra/acordes/melodía, no toda la aplicación. Los menús nativos, MIDI, anotaciones y presentación siguen siendo propios de Android. La carga/reemplazo conjunto de referencias y el renombrado agrupado siguen siendo funciones de la web. El OCR de Android usa ML Kit; la web usa su motor Python. Bibliotecas, preferencias y estados de revisión no se sincronizan automáticamente. La melodía textual no se transpone automáticamente; revisar las notas al cambiar de tono. Las alineaciones de los cantos transcritos siguen siendo aproximadas.

Actualizar la APK no reemplaza bibliotecas ya importadas en el dispositivo. Los nuevos cantos están incluidos como assets para instalación/importación, pero una biblioteca existente debe recibir los archivos actualizados de forma explícita.

## Validación

- 9 pruebas JavaScript del motor y los cantos de Misa Guelaguetza.
- 22 pruebas Java web y generación del JAR.
- 10 pruebas JVM Android y generación de APK debug.
- 8 pruebas instrumentadas en Android 17/API 37; la nueva prueba verifica conversión nativa, carga de assets sin red, anclajes y cambios de vista.

```powershell
node --test OpenSongWeb/src/test/js/melody.test.cjs
.\gradlew.bat -p OpenSongWeb test bootJar
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```
