# OpenSongWeb: alcance, propuesta y plan

## Objetivo

Crear y organizar canciones desde un computador en una interfaz web sencilla, conservar su letra en formato CHORDPRO y preparar una biblioteca que pueda incorporarse a OpenSong Tablet sin importar canciones una por una.

## Investigación de OpenSong Tablet

- Las canciones no viven solamente en SQLite. La app administra archivos dentro de `OpenSong/Songs`; las canciones OpenSong se escriben como XML y también reconoce archivos CHORDPRO, incluidos `.cho`, `.crd`, `.chopro`, `.chordpro` y `.pro`.
- Al indexar un archivo CHORDPRO, OpenSong lo convierte a su formato XML y actualiza su propio índice.
- `Songs.db` es un índice local de la app con columnas de carpeta, nombre, título y otros metadatos. El identificador práctico es `carpeta/nombre-de-archivo`; cada canción también puede tener un UUID en su XML, usado para referencias persistentes y analítica.
- El importador masivo de la app convierte varios archivos CHORDPRO, pero los coloca en la carpeta `Imported`; no conserva por sí solo la jerarquía de subcarpetas de un ZIP.
- Por eso OpenSongWeb no debe crear ni modificar `Songs.db`. La fuente de verdad será un archivo CHORDPRO por canción, con su ruta relativa como identidad. OpenSong Tablet seguirá creando y manteniendo su propia base SQLite.

## Propuesta tecnológica

- **Backend:** Java 21 y Spring Boot; el servidor local sirve la interfaz web y una API REST.
- **Interfaz:** HTML, CSS y JavaScript nativos para que la primera versión sea fácil de mantener desde Java y no requiera Node.js.
- **Web y escritorio:** la misma interfaz adaptable funciona en un navegador. En escritorio se inicia el servidor solo en `localhost` y se abre el navegador; la interfaz puede instalarse como PWA desde un navegador compatible. Para publicar la web en una red o en Internet hará falta HTTPS y autenticación.
- **Persistencia:** directorios y archivos CHORDPRO, sin una segunda SQLite. Se guardan en `~/.opensongweb/Songs` por defecto; `OPENSONGWEB_LIBRARY_PATH` permite cambiar la ubicación.
- **Intercambio:** ZIP descargable cuya raíz es `Songs/` y cuyas entradas conservan las carpetas. Los archivos terminan en `.pro`, extensión que OpenSong Tablet reconoce.

## MVP de esta primera iteración

- Crear, editar y eliminar canciones.
- Capturar título, autor, copyright/álbum, carpeta y letra con acordes CHORDPRO.
- Insertar o actualizar las directivas `{title:}`, `{artist:}` y `{copyright:}` al guardar.
- Explorar y buscar canciones por título, autor o carpeta.
- Ver una previsualización sencilla de la letra y los acordes.
- Guardar cada canción como archivo CHORDPRO en su subcarpeta.
- Descargar toda la biblioteca como ZIP con las subcarpetas intactas.
- Ejecutar en modo escritorio local o servir la misma interfaz desde Spring Boot.

### Cómo pasar la biblioteca a OpenSong Tablet en este MVP

1. Descargar el ZIP desde OpenSongWeb.
2. Extraer la carpeta `Songs/` dentro del directorio `OpenSong` que usa la app, conservando las subcarpetas.
3. En OpenSong Tablet, volver a indexar la biblioteca. La app reconocerá los `.pro`, los convertirá a XML y actualizará `Songs.db`.

El importador masivo actual es una alternativa para archivos sueltos, pero los agrupa en `Imported`; no usarlo si se necesita conservar exactamente la estructura de carpetas.

## Plan de producto

### Iteración 1 — MVP local

Editor, organización por carpetas, almacenamiento CHORDPRO y descarga ZIP. Es la versión implementada en este proyecto.

### Iteración 2 — Sincronización desde Android

- Agregar en OpenSong Tablet una acción para conectar con OpenSongWeb y descargar el ZIP.
- Elegir si la conexión será a un servidor HTTPS o a un servidor local emparejado en la misma red.
- Extraer en el espacio `OpenSong/Songs` usando las APIs SAF de Android, crear subdirectorios y mostrar conflictos antes de reemplazar.
- Ejecutar/requerir el reindexado de la app; no importar ni compartir la SQLite del servidor.
- Añadir identificadores/versiones de exportación y una opción de solo agregar o actualizar para evitar sobrescrituras inesperadas.

### Iteración 3 — Edición y distribución

- Incorporar vista de árbol de carpetas y edición de metadatos adicionales si se necesitan.
- Mejorar la previsualización para aproximarla al renderizado de OpenSong.
- Añadir respaldo/versionado y validación CHORDPRO más completa.
- Preparar empaquetado nativo de escritorio si la PWA no cubre la experiencia deseada.

### Antes de exponerlo a Internet

El MVP no incluye cuentas, autenticación, autorización ni transporte seguro para despliegue público. No se debe publicar el servidor de desarrollo en una red accesible sin añadir HTTPS, autenticación, límites de acceso y política de respaldo.

## Fuera del MVP

- Sincronizar o escribir directamente en SQLite de OpenSong Tablet.
- Editar XML OpenSong, PDF, imágenes, audio, MIDI o listas de sets.
- Edición simultánea de varios usuarios.
- Importación del ZIP directamente dentro de Android (queda para la Iteración 2).
