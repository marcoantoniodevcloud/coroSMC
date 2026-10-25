# Prueba de melodía textual en ChordPro

Fuente: imagen proporcionada por el usuario de Entrada, Misa Guelaguetza.

- `Misa Guelaguetza - Entrada.cho`: canto completo con estribillo y tres estrofas. Se transcriben las doce líneas de melodía visibles. La tercera estrofa no tiene notas en la fuente.
- `vista-web.png`: captura de la prueba en Cantoral Studio.
- La web incluye **Probar ejemplo de melodía** en la vista previa. Abre un borrador sin escribir en la biblioteca; solo Guardar crea el canto.

## Convención de la prueba

```chordpro
[G]CELEBRAMOS LA FE EN EL SEÑOR
{comment: melodia: <si>CE<do>LE<re>BRA<mi>MOS <fa#>LA <sol>FE <fa#>EN <mi>EL <re>SEÑOR}
```

El comentario debe ir inmediatamente después del verso. Al retirar las marcas `<nota>`, su texto debe coincidir exactamente con la letra del verso sin acordes, incluyendo espacios y puntuación. Si no coincide, la web muestra una advertencia y conserva el comentario visible para corregirlo. Se permiten varias notas en un anclaje, por ejemplo `<do re>`.

Esto es una convención propia dentro de comentarios ChordPro válidos, no una sintaxis estándar de melodía. Otros lectores pueden mostrarla como un comentario. No se usan espacios para determinar la alineación. Cada fragmento de texto comparte bloque con sus acordes y notas; el salto de línea se hace entre palabras. La vista permite ocultar acordes, ocultar melodía o mostrar las notas en un panel separado por verso.

Los anclajes se aproximaron a partir de la imagen y requieren comprobación musical. Los puntos de prolongación se conservaron como `sol ···`, sin inventar duraciones ni octavas. Ocultar capas cambia solo la presentación. La prueba inicial no incluía reproducción ni transposición melódica. El renderizado Android se añadió posteriormente mediante la vista compartida descrita en `../HOMOLOGACION_WEB_ANDROID.md`.

## Validación

```powershell
node --test OpenSongWeb/src/test/js/melody.test.cjs
.\gradlew.bat -p OpenSongWeb test bootJar
```

Seis pruebas JavaScript cubren anclajes compartidos, discrepancias, varias notas, las doce líneas del ejemplo, creación de capas con texto seguro y separación por palabras. Las 22 pruebas Java existentes también pasan. Se comprobaron las tres vistas de melodía en el navegador.

## Cantos añadidos el 24-10-2026

Se añadieron ocho cantos a la biblioteca compartida de assets, cada uno con su `.cho` y su imagen `.png` original:

| Carpeta | Canto |
| --- | --- |
| Piedad | Piedad - Misa Guelaguetza |
| Gloria | Gloria - Misa Guelaguetza |
| Aleluya | Aleluya - Misa Guelaguetza |
| ofertorio | Ofertorio - Misa Guelaguetza |
| santo | Santo - Misa Guelaguetza |
| CristoConElyEnEl | Por Cristo con Él y en Él - Misa Guelaguetza |
| Oracion | Padre nuestro - Misa Guelaguetza |
| cordero | Cordero - Misa Guelaguetza |

`Piedad` ya existía; se reutilizó. `CristoConElyEnEl` se creó. Los ocho cantos quedan pendientes con observaciones en `revision-cantos.json`. Las 66 líneas de melodía (incluyendo introducciones) coinciden textualmente con sus versos al retirar las marcas, pero la correspondencia musical es aproximada. Los grupos de notas al final de un verso y los superíndices se conservan para revisión; no se asignan duraciones a las líneas subrayadas. No se copió melodía a estrofas que no la incluyen en la imagen. El script de carga rechaza archivos existentes para evitar sobrescrituras. El respaldo del control anterior se conserva junto a este documento.

La web en modo `desktopRun` lee esta biblioteca directamente. Estos assets también son la fuente de la biblioteca predeterminada Android; no se actualizó una APK ni se modificaron bibliotecas ya importadas en dispositivos.
