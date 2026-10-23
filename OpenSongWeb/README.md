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
`desktopRun` usa por defecto `app\src\main\assets\DefaultSongs` del repositorio, sin mover ni copiar sus archivos.

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

## Pruebas

Desde la raíz del repositorio:

```powershell
.\gradlew.bat -p .\OpenSongWeb test
```
