# OpenSongWeb

Editor web local para explorar y editar cantos ChordPro junto a sus archivos de referencia.
La biblioteca muestra una sola entrada por nombre base y permite abrir imágenes y PDF relacionados.

Consulta [FEATURES.md](./FEATURES.md) para la investigación del almacenamiento Android, el alcance del MVP y el plan de integración.

## Requisitos

- JDK 21
- El Gradle Wrapper del repositorio OpenSongTablet

## Ejecutar en escritorio

`OpenSongWeb` es un proyecto Gradle independiente del proyecto Android y no forma parte de la APK.
Se prueba en el computador como una aplicación web local, sin instalar dependencias de Node.js.

Desde la raíz del repositorio:

```powershell
.\gradlew.bat -p .\OpenSongWeb desktopRun
```

Este comando compila e inicia la aplicación web y abre `http://localhost:8080` en el navegador predeterminado.
La biblioteca aparece con su jerarquía de carpetas; puedes expandir cada carpeta para navegar sus cantos.
Para crear un canto, selecciona primero la carpeta de destino y después escribe su nombre. La papelera
de cada canto permite escoger y eliminar un archivo individual (`.cho`, `.pro`, imagen o PDF), sin
eliminar los demás archivos relacionados.
La vista previa en vivo coloca los acordes sobre la posición correspondiente de la letra e interpreta
los encabezados ChordPro de coro, estrofa y puente.
Cada canto inicia pendiente y puede marcarse como revisado desde el editor, incluso si solo tiene un
PDF o imagen. El estado se guarda en `review-status.txt`, junto a los datos locales de OpenSongWeb,
sin modificar los archivos del cantoral. Las carpetas con cantos pendientes aparecen en amarillo;
cuando todos los cantos de una carpeta están revisados, el indicador cambia a verde.
Si la carpeta de cantos contiene `revision-cantos.json`, los casos con `needsAttention: true`
aparecen con punto rojo y muestran la observación en el editor. Las carpetas también señalan
si contienen casos rojos. Marcar un canto como revisado oculta el aviso; quitar ese estado
vuelve a mostrarlo. El informe es un control de la auditoría, no una validación musical automática.
`desktopRun` usa por defecto `app\src\main\assets\DefaultSongs` del repositorio, sin mover ni copiar sus archivos.

## Correcciones

**Corregir…**, en el editor o en cada canto, abre una lista de acciones ampliable.
La primera acción convierte acordes a notación americana: Do/Re/Mi/Fa/Sol/La/Si →
C/D/E/F/G/A/B, conservando variantes y bajos (por ejemplo, `Sol#m7` → `G#m7`,
`Re/Fa#` → `D/F#`). Se convierten etiquetas entre corchetes y las directivas
`key`, `define` y `chord`; la letra, los comentarios y otros metadatos se conservan.
El resultado individual queda en el editor hasta pulsar **Guardar**.

**Corregir todos…** procesa todos los archivos `.cho`/`.pro`, incluidos los ya
revisados y los que no coincidan con la búsqueda actual. Guarda los cambios y
respalda cada archivo modificado en `correction-backups`, en la carpeta de datos
de OpenSongWeb. El lote comprueba la revisión de cada archivo antes de guardarlo,
permite detenerse después del archivo actual e informa los errores por archivo.
Los estados de revisión no cambian. Primero guarda o descarta cualquier edición
pendiente para iniciar el lote.

## OCR_PROJECT

La biblioteca ofrece un botón **OCR** en cada canto y otro para procesar en lote los cantos pendientes que tengan una imagen o PDF relacionado. El botón individual usa la referencia seleccionada en el panel derecho; el lote procesa el primer archivo de referencia asociado a cada canto. El proceso reemplaza el contenido ChordPro actual, conserva título/autor/copyright y deja el canto pendiente para revisión. Los cantos sin imagen o PDF se omiten. Antes de reemplazar una letra existente, se guarda una copia en `ocr-backups` dentro de la carpeta de datos de OpenSongWeb.

El código de OCR_PROJECT, sus dependencias y el modelo de reconocimiento para escritura latina están incluidos en `OpenSongWeb/src/main/resources/ocr_project`; ya no se requiere la carpeta externa `cantoral_chordpro_ocr`. En Windows, ejecuta `OpenSongWeb/run-app.bat` para iniciar la biblioteca. Al iniciar `desktopRun` o `bootRun`, Gradle crea `OpenSongWeb/.venv` e instala automáticamente las dependencias la primera vez, o cuando cambia `requirements.txt`. Esto requiere Python 3.12 disponible como `py -3` en Windows o `python3` en Linux/macOS y conexión a Internet durante la instalación inicial. El entorno queda dentro del repositorio y se reutiliza en los siguientes arranques. Para una instalación de Python en otra ruta, se puede definir `OPENSONGWEB_PYTHON` con el ejecutable.

Para usar otra carpeta existente, configura su ruta antes de iniciar la aplicación:

```powershell
$env:OPENSONGWEB_SONGS_PATH = 'D:\MisCantos'
.\gradlew.bat -p .\OpenSongWeb desktopRun
```

`OPENSONGWEB_SONGS_PATH` apunta directamente a la carpeta de cantos y tiene prioridad sobre
`OPENSONGWEB_LIBRARY_PATH`. `bootRun` sin esa variable conserva la biblioteca predeterminada
`%USERPROFILE%\.opensongweb\Songs`. La aplicación no copia ni mueve los archivos de la carpeta elegida.

Para iniciar el servidor sin abrir automáticamente el navegador:

```powershell
.\gradlew.bat -p .\OpenSongWeb bootRun
```

Luego abre `http://localhost:8080`.

Para cambiar la carpeta de datos en PowerShell:

```powershell
$env:OPENSONGWEB_LIBRARY_PATH = 'D:\OpenSongWebData'
.\gradlew.bat -p .\OpenSongWeb desktopRun
```

## Flujo con OpenSong Tablet

Los archivos `.cho` y `.pro` se editan en su ubicación original. Antes de guardar se comprueba si otro programa modificó el archivo. Las imágenes y PDF con el mismo nombre base se muestran como referencias del canto. Se conserva el editor de biblioteca `.pro` y la exportación ZIP existente.

La descarga e instalación directa desde Android todavía no está implementada; está descrita como la Iteración 2 en el plan de features.

Cada ejecución OCR también conserva el JSON de revisión más reciente en `ocr-reviews`, junto a los datos de OpenSongWeb, usando la misma estructura de carpetas que la biblioteca. El JSON contiene las detecciones originales, coordenadas, confianza y posiciones calculadas de acordes. Sirve para diagnosticar y comparar correcciones; no es una entrada que el OCR necesite para generar el `.cho`.

## Pruebas

Desde la raíz del repositorio:

```powershell
.\gradlew.bat -p .\OpenSongWeb test
```

### Renombrar archivos de un canto

Selecciona un canto y pulsa **Renombrar archivos…**. Escribe el nombre sin extensión; se renombran juntos todos los archivos de ese canto en su carpeta (letra, PDF e imágenes), conservando las extensiones, el contenido, el estado de revisión y los enlaces del control de observaciones. El título interno de la letra no se modifica. Primero guarda o descarta los cambios del editor. Se rechazan nombres inválidos y colisiones con otro canto; si una operación falla se intenta restaurar los nombres y metadatos originales.

Android ya tiene un campo de nombre de archivo en el editor. Ese flujo no ofrece las mismas garantías para los archivos asociados, las anotaciones y el estado de revisión, por lo que esta acción conjunta se implementa por ahora solo en web.

### Subir o reemplazar referencias

En el panel **Referencia**, usa **Subir imagen o PDF…** o arrastra un archivo para añadirlo al canto seleccionado. Para un canto nuevo, primero pulsa Guardar. Se admiten PDF, PNG, JPG/JPEG y WebP, hasta 30 MB. El archivo adopta el nombre del canto y conserva su formato.

Selecciona una referencia y pulsa **Reemplazar seleccionado…** para sustituirla por un archivo del mismo formato (JPG/JPEG son intercambiables). El original se respalda en `media-backups` dentro del directorio de datos de OpenSongWeb. Para otro formato, usa Subir como referencia adicional. No se sobrescribe una referencia existente mediante Subir. Las letras no cambian ni se ejecuta OCR automáticamente; el canto queda pendiente de revisión.

### Prueba de melodía en ChordPro

En Vista previa, **Probar ejemplo de melodía** abre Entrada de Misa Guelaguetza como borrador. Los comentarios `melodia:` inmediatamente posteriores al verso incluyen notas ancladas mediante `<nota>`. El selector permite verlas debajo de la letra, ocultarlas o mostrarlas en un panel separado; Acordes controla la otra capa. Es una extensión propia dentro de comentarios ChordPro. La transcripción y la alineación del ejemplo son aproximadas. Véase `../docs/pruebas-melodia/README.md` para la convención y las limitaciones.

La vista de cantos comparte ahora el motor y CSS con Android en `../shared/song-view`; la descripción de alcance y diferencias está en `../docs/HOMOLOGACION_WEB_ANDROID.md`. Las pruebas JavaScript se ejecutan con `node --test src/test/js/melody.test.cjs` desde OpenSongWeb.
