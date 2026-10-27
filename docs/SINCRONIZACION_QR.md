# Sincronización Android ↔ Cantoral Studio por QR

## Uso

1. Ejecutar la web en la computadora y abrir `http://localhost:8080`.
2. Pulsar el icono ⇄ de la barra superior. Seleccionar la IP de la red Wi-Fi y generar un QR.
3. En Android: menú de tres puntos → Sincronizar biblioteca → Escanear QR.
   También se puede copiar el enlace mostrado junto al QR y pegarlo en Android.
4. Descargar toda la biblioteca la primera vez; después usar Descargar cambios.
5. En la lista de sincronización abrir un canto pendiente, corregir ChordPro,
   usar Tonalidad y etiquetas, comprobar Vista previa y Guardar borrador.
6. Enviar cambios para aprobación cuando ambos equipos estén conectados.
7. En web: ⇄ → Actualizar → Ver cambios → Aprobar e integrar o Rechazar.
8. En Android: Descargar cambios incorpora lo aprobado y conserva los borradores pendientes.

La computadora debe estar encendida y ambos equipos en la misma red. Si no conecta,
comprobar la IP seleccionada, que la red no aísle clientes y que Java tenga permiso
para conexiones entrantes en la red privada de Windows. No se necesita un servicio externo.
El servidor escucha en todas las interfaces para permitir la sincronización;
`OPENSONGWEB_BIND_ADDRESS=127.0.0.1` permite volver al uso exclusivo en la computadora.
La administración web se abre desde localhost; los celulares solo acceden a las rutas
específicas de sincronización con una credencial. El QR caduca a los cinco minutos,
es de un solo uso y se puede revocar cada celular desde la web. Es un flujo HTTP para
la red local, sin publicación ni acceso por internet.

## Archivos y conflictos

- Se descargan ChordPro `.cho`/`.pro` y sus referencias PDF/PNG/JPG/JPEG/WebP.
- El editor de sincronización conserva ChordPro; no convierte los archivos a XML.
- Los borradores y versiones de origen se guardan en almacenamiento privado, separados
  por identidad del servidor. Si hay una carpeta de canciones configurada, también
  se copian a `Songs/WebSync/<servidor>/...` para usarlos en la biblioteca principal.
- Los cambios de esos archivos ChordPro detectados fuera del editor se recogen antes
  de sincronizar. Si un editor externo los convirtió a XML, se detiene el envío y
  se conserva el archivo para revisión. Para este flujo se recomienda el editor
  disponible dentro de Sincronizar biblioteca.
- Descargar no elimina archivos locales ni descarta borradores. No se propagan borrados.
- Una propuesta guarda el contenido de origen, su SHA-256, la corrección y la solicitud
  de revisión. La web verifica la revisión otra vez al aprobar; nunca usa «el último gana».
- Ante un conflicto Android conserva origen, versión nueva y borrador. El usuario
  compara y edita antes de enviar otra propuesta; no hay una combinación automática.
- La marca revisado oficial se aplica solo al aprobar. Editar el contenido oficial
  desde la web vuelve a dejarlo pendiente de revisión.
- Los identificadores se guardan aparte de los cantos y acompañan los renombrados
  realizados desde la web. No se insertan UUID ni otros caracteres en los originales.
- Esta etapa envía cambios a cantos descargados; no importa automáticamente una
  biblioteca ajena ni crea, mueve o elimina cantos oficiales desde el celular.

## Persistencia y API

En `opensongweb.library-path/sync/`: `state.json` (credenciales con hash y propuestas),
`identities.json` (identidades permanentes), `approved/` (respaldo anterior a cada aprobación).
La vinculación en curso solo vive en memoria y caduca también al reiniciar.
`/api/sync/admin/*` requiere acceso local. `/api/sync/pair` intercambia el código temporal;
`manifest`, `file` y `proposals` requieren `Authorization: Bearer …`.
Las rutas de edición antiguas quedan limitadas a la computadora, incluso al escuchar en LAN.
No colocar los archivos de sincronización ni credenciales dentro de Songs o en Git.

## Validación

Pruebas JVM de web: propuestas sin escritura, aprobación/rechazo, deduplicación,
conflictos, revisión de contenido, identidad tras renombrar, persistencia,
caducidad y revocación. Pruebas HTTP de bloqueo de administración desde LAN,
otro origen y sin credenciales. Prueba Android de vinculación y descarga, borrador,
envío, aprobación web y recepción de la versión aprobada con ChordPro intacto.
El escaneo con cámara física requiere comprobarse en el celular; el emulador valida
el intercambio del contenido del QR y todo el flujo posterior.
