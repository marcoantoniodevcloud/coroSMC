> Actualización de arquitectura: la fuente canónica se trasladó a `../../Cantoral/shared/song-view` (ruta relativa a este documento). Android legado y web la empaquetan desde esa ubicación; Cantoral la integra con controles Compose nativos. Los nombres `shared/song-view` usados abajo describen el componente, no una copia en esta carpeta.

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

La pantalla de interpretación muestra directamente la vista compartida para los cantos de texto. Se eliminó el botón y la ventana aparte. La página incluye el título completo, autor/tono y controles de acordes, melodía, tamaño y tema claro/oscuro. Web carga exactamente la misma página dentro de su vista previa. Ambos empaquetan RobotoMono.ttf y usan una base de 20 px. Las preferencias se recuerdan localmente, sin sincronización. PDF e imágenes conservan su visor nativo. Presentación y pantalla secundaria conservan su implementación anterior. Los gestos, autoscroll y anotaciones del visor nativo no están integrados en la nueva vista de texto; las anotaciones nativas no se superponen porque sus coordenadas no corresponden a esta hoja.

Los cantos nativos se convierten en memoria a ChordPro para mostrarlos; abrir la vista no escribe archivos. El conversor ahora conserva las marcas de melodía en los comentarios, mientras que mantiene el saneamiento previo de los ángulos en el resto del texto. La WebView solo carga assets locales, bloquea la red y no expone interfaces Java a JavaScript.

## Web

La vista previa carga `/song-view/index.html`, la misma página empaquetada en Android. El CSS de la aplicación web queda fuera de la página de lectura, evitando que colores de tema oscuro sobrescriban el texto del tema claro. Conserva las funciones de edición, corrección, OCR y archivos existentes. Con el mismo contenido, ancho de hoja y preferencias, ambos usan la misma composición; distintos anchos cambian los saltos. Los colores nativos personalizados no se aplican a esta vista; sus temas son compartidos.

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

## Verificación de la vista principal (25 de octubre de 2026)

- 9 pruebas del motor JavaScript, 10 pruebas JVM Android y 22 pruebas Java web aprobadas.
- APK debug y JAR generados.
- Prueba instrumentada `UnifiedSongViewInstrumentedTest` aprobada en Android 17/API 37: conversión de melodía, título, contraste claro/oscuro y los tres modos de melodía.
- Navegador Edge: carga de la página compartida y del iframe dentro de la web en puerto 8081, sin errores JavaScript; ejemplo Entrada con 113 marcas de nota y fuente incluida cargada. Capturas en `docs/pruebas-melodia/vista-compartida-clara.png` y `web-vista-principal.png`.
- La prueba instrumentada monta el componente compartido; no automatiza la navegación completa entre cantos, presentación ni herramientas nativas.
