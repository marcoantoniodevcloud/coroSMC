# OpenSongWeb

Editor web local para crear canciones CHORDPRO, organizarlas en carpetas y exportar una biblioteca ZIP compatible con el flujo de OpenSong Tablet.

Consulta [FEATURES.md](./FEATURES.md) para la investigación del almacenamiento Android, el alcance del MVP y el plan de integración.

## Requisitos

- JDK 21
- El Gradle Wrapper del repositorio OpenSongTablet

## Ejecutar en escritorio

Desde la raíz del repositorio:

```powershell
.\gradlew.bat -p .\OpenSongWeb desktopRun
```

Esto inicia Spring Boot en `http://localhost:8080` y abre la interfaz en el navegador predeterminado. La biblioteca se guarda en `%USERPROFILE%\.opensongweb\Songs`.

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

En OpenSongWeb, guarda las canciones y descarga el ZIP. Extrae la carpeta `Songs` del ZIP dentro de la carpeta `OpenSong` que utiliza OpenSong Tablet. Conserva los subdirectorios y solicita un reindexado en la app. OpenSong Tablet reconoce los archivos `.pro`, los convierte a XML y crea/actualiza su propio índice SQLite.

La descarga e instalación directa desde Android todavía no está implementada; está descrita como la Iteración 2 en el plan de features.

## Pruebas

Desde la raíz del repositorio:

```powershell
.\gradlew.bat -p .\OpenSongWeb test
```
