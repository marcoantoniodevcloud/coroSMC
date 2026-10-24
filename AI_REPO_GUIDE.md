# OpenSongTablet: guia rapida para analisis con IA

Este documento resume la estructura y los puntos de entrada observados en el repositorio para evitar repetir una exploracion amplia al comenzar una tarea. Es una guia de orientacion, no una especificacion completa: confirma siempre el comportamiento en el codigo actual y actualiza este archivo cuando cambie la arquitectura.

## Resumen

- Correcciones/OCR Android: `songprocessing/ChordNotationCorrector` convierte ChordPro y filas OpenSong; `SongCorrections` ejecuta el lote con respaldos. `pdf/OcrChordLayout` asocia coordenadas ML Kit a ChordPro, utilizado por OCR individual y por lotes. `songmenu/SongReviewStatus` persiste revisado y consulta observaciones de `DefaultSongs/revision-cantos.json`; ese JSON no se instala como canto. Ver `docs/ANDROID_CORRECCIONES_OCR.md` para uso y diferencias con el OCR Python de OpenSongWeb.

- OpenSong es una aplicacion Android para gestionar canciones, letras/acordes y conjuntos (sets), y para usarlos en modos de interpretacion y presentacion.
- El proyecto Gradle incluye actualmente `:app` y `:wear`, segun [settings.gradle](settings.gradle). La app principal es mayoritariamente Java, con algunos archivos Kotlin; la app Wear OS usa Kotlin, Jetpack Compose y ofrece un metronomo.
- El paquete Android principal es `com.garethevans.church.opensongtablet`.
- La UI movil usa Android Views, Activities/Fragments, View Binding y Navigation Component; no asumir que sigue MVVM de forma uniforme. La actividad principal implementa coordinacion transversal y muchas funciones.
- Directorios `core`, `shared` y `web` pueden aparecer con salidas o restos de builds en el workspace, pero no estan incluidos como proyectos en `settings.gradle`. No tratarlos como modulos activos sin verificar cambios recientes.

## Mapa del repositorio

| Ruta | Responsabilidad |
| --- | --- |
| [app/src/main/java/com/garethevans/church/opensongtablet](app/src/main/java/com/garethevans/church/opensongtablet) | Codigo de la app principal, organizado por paquetes funcionales. |
| [app/src/main/res](app/src/main/res) | Layouts, navegacion, strings/localizaciones, temas, iconos y otros recursos Android. |
| [app/src/test](app/src/test) | Pruebas unitarias locales (JVM). |
| [app/src/androidTest](app/src/androidTest) | Pruebas instrumentadas en dispositivo/emulador. |
| [wear/src/main](wear/src/main) | App independiente para Wear OS; UI y logica de metronomo. |
| [gradle/wrapper](gradle/wrapper) | Version de Gradle Wrapper. |
| [run-app.ps1](run-app.ps1) | En Windows, busca SDK/ADB, arranca el AVD configurado, compila, instala y lanza la app. |

## Flujos y clases para empezar

- Arranque Android: [MainActivity.java](app/src/main/java/com/garethevans/church/opensongtablet/MainActivity.java), declarada como launcher en [AndroidManifest.xml](app/src/main/AndroidManifest.xml). Centraliza navegacion, integraciones del sistema y coordinacion con muchos dominios; para una funcionalidad concreta, sigue el paquete especializado antes de ampliar cambios en esta clase.
- Grafo de pantallas: [navigation.xml](app/src/main/res/navigation/navigation.xml). El destino inicial es `BootUpFragment`; desde ahi la navegacion llega, entre otros, a `PerformanceFragment` (modo de interpretacion) y `PresenterFragment` (presentacion).
- Canciones: [Song.java](app/src/main/java/com/garethevans/church/opensongtablet/songprocessing/Song.java) es el objeto de dominio/datos; enlaza los datos de la cancion con su representacion XML y campos usados por la app. [ProcessSong.java](app/src/main/java/com/garethevans/church/opensongtablet/songprocessing/ProcessSong.java) procesa XML y prepara contenido visual, incluyendo texto, acordes y presentacion. Los editores/conversores tambien viven en `songprocessing`.
- Persistencia SQLite: [SQLiteHelper.java](app/src/main/java/com/garethevans/church/opensongtablet/sqlite/SQLiteHelper.java), [CommonSQL.java](app/src/main/java/com/garethevans/church/opensongtablet/sqlite/CommonSQL.java) y `songprocessing/Song.java`. Al cambiar el formato de una cancion, busca sus conversiones, lectura/escritura de archivos y persistencia; no supongas que el modelo vive solo en SQLite.
- Sets: [CurrentSet.java](app/src/main/java/com/garethevans/church/opensongtablet/setprocessing/CurrentSet.java) mantiene el set activo y su estado; [SetActions.java](app/src/main/java/com/garethevans/church/opensongtablet/setprocessing/SetActions.java) concentra acciones; `SetObject.java` y `setmenu/SetItemInfo.java` representan datos relacionados. La UI del menu vive en `setmenu`.
- Lista/busqueda de canciones: paquete `songmenu` (`SongMenuFragment`, adaptadores e indexado).
- Configuracion: `preferences/Preferences.java` es un punto central para preferencias; las categorias y pantallas estan repartidas por `preferences`, `screensetup`, `controls` y otros paquetes.
- Almacenamiento e importacion/exportacion: `filemanagement`, `importsongs`, `backupandrestore` y `export`. Los formatos admitidos y filtros externos tambien se declaran en el manifest.
- Presentacion y pantallas: `presenter`, `performance`, `secondarydisplay` y `customslides`.
- Integraciones/features: `midi`, `nearby`, `openchords`, `beatbuddy`, `drummer`, `metronome`, `autoscroll`, `chorddetector`, `pdf`, `multitrack`.
- Servidor embebido: `webserver/KtorServer.kt` y clases vecinas; dependencias Ktor se declaran en `app/build.gradle`.
- Wear OS: [MainActivity.kt](wear/src/main/kotlin/com/garethevans/church/opensongtablet/MainActivity.kt), con UI Compose del metronomo; servicios, motor y preferencias tienen clases separadas en el mismo paquete.

## Estructura por paquetes de `app`

El codigo esta agrupado por feature, pero no hay una capa uniforme de dominio/datos/UI ni modulos Gradle por paquete. Algunos paquetes mezclan fragments, modelos, adaptadores y logica. Usa esta tabla como indice inicial:

| Paquete | Area principal |
| --- | --- |
| `appdata`, `preferences` | Inicio, informacion de app, preferencias y permisos. |
| `songprocessing`, `songmenu`, `filemanagement` | Modelo, transformacion/edicion, listado y archivos de canciones. |
| `setprocessing`, `setmenu` | Estado, operaciones y UI de conjuntos. |
| `presenter`, `performance`, `secondarydisplay`, `customslides` | Interpretacion y presentacion en pantallas. |
| `sqlite`, `backupandrestore`, `export` | Base de datos, respaldo, importacion de respaldos y exportaciones. |
| `importsongs`, `openchords`, `justchords` | Proveedores e importacion de contenido musical. |
| `customviews`, `interfaces`, `controls`, `screensetup` | Componentes compartidos, contratos, controles y apariencia. |
| `midi`, `nearby`, `webserver`, `multitrack`, `pdf`, `chorddetector` | Integraciones y herramientas especializadas. |
| `abcnotation`, `chords`, `drummer`, `beatbuddy`, `metronome`, `autoscroll`, `pads`, `voicelive` | Funcionalidades musicales/escenicas. |

## Build y desarrollo

- Android Gradle Plugin declarado en la raiz: `9.3.3`; `compileSdk` principal: `36`; toolchain Kotlin de `app`: JVM 17. Wear declara `compileSdk 36`, `minSdk 26` y Compose.
- Compilar app: `./gradlew :app:assembleDebug` (Windows: `gradlew.bat :app:assembleDebug`).
- Pruebas JVM app: `./gradlew :app:testDebugUnitTest`.
- Pruebas instrumentadas app (necesita dispositivo/emulador): `./gradlew :app:connectedDebugAndroidTest`.
- Compilar Wear: `./gradlew :wear:assembleDebug`.
- En Windows, `./run-app.ps1` es un flujo de build/install/launch, no solo un build. Comprueba el AVD `Medium_Phone_API_37.0` y requiere Android SDK, emulator y ADB.
- Estos comandos se deducen de la configuracion Gradle; no se ejecutaron como parte de la creacion de esta guia.

## Configuracion, recursos y convenciones

- [app/build.gradle](app/build.gradle) lee `local.properties` para firma release y `GOOGLE_FONTS_API_KEY`; no copiar valores locales ni secretos en codigo, issues o esta guia.
- `app` tiene variantes `debug`, `release` y `beta`. `versionCode`/`versionName` tambien tienen una nota que pide coordinar cambios con Wear.
- La app depende de AndroidX, Navigation, Material Components, Google Play Services (incluyendo Cast, Nearby y Wear), SQLite, Retrofit/OkHttp, Gson, Jsoup, Glide, ZXing, Ktor y otras librerias. Mira el `dependencies` del modulo antes de agregar una alternativa.
- Las pantallas y muchos dialogos usan XML en `app/src/main/res/layout`; el grafo esta en `res/navigation`; traducciones en `res/values-*`.
- El README pide traducir nuevos textos de `strings.xml` a los otros idiomas. Para preferencias nuevas, documentar en `Preferences.java` la clave, proposito y valor por defecto (confirmar si el nombre del archivo/clase cambia en la rama actual).
- Android storage/permissions, archivos abiertos por intents y compatibilidad antigua pueden afectar cambios; consultar `AndroidManifest.xml`, `filemanagement` y el flujo correspondiente.

## Pruebas y nivel de confianza

- Las pruebas visibles son muy escasas: `JavaUnitTest` es un esqueleto sin aserciones de comportamiento; la prueba Kotlin instrumentada solo comprueba el package name. No inferir cobertura amplia por las dependencias de testing.
- Prioriza pruebas nuevas cerca del comportamiento modificado y ejecuta el target Gradle mas estrecho posible. Para UI/permisos/SAF, la validacion puede requerir emulador o dispositivo.
- Este resumen se elaboro leyendo configuracion Gradle, manifest, grafo de navegacion, clases centrales y estructura de fuentes. No es una auditoria exhaustiva de todos los paquetes, flujos ni compatibilidad.
- Si un nombre/ruta de paquete parece inconsistente, usa el path real del filesystem y la declaracion `package`; algunas mayusculas pueden variar entre el nombre historico y el path.

## Cambios desde v7.0.0

### Alcance comprobado

- En este repositorio `v7.0.0` es el commit `1e5dd0d9d` (mensaje `v7.0.0 (501)`), no un tag Git llamado `v7.0.0`.
- Rango revisado: `1e5dd0d9d..ba9f976f9` en la rama `MaterialApp`, del 29 de septiembre al 13 de octubre de 2026. Son 4 commits no-merge, todos atribuidos a `degel`; el delta agregado es 98 archivos, aproximadamente 1048 inserciones y 3052 eliminaciones.
- Los commits M1/KMP (`:core`, `:shared`, `:web`) que pueden aparecer con `git log --all` no son ancestros de HEAD. No describen la arquitectura de esta linea; `settings.gradle` sigue incluyendo `:app` y `:wear`.

### Cronologia funcional

1. `deb045f2c` (`removing tutorial, change the logo`, 8-oct): elimina el sistema de tutoriales Showcase y sus llamadas; cambia el splash/logo por una imagen propia y un indicador animado, limpia referencias asociadas y agrega scripts Windows de build/instalacion. Importante: el mismo commit tambien elimina la funcion Bible (busqueda online/offline, descarga, integracion con sets, pantallas, navegacion y textos). Su alcance real es mucho mayor que el resumen del commit; no asumir que Bible era solo parte del tutorial.
2. `4587ce5c0` (`making access storage default`, 9-oct): en el primer arranque sin ubicacion SAF, abre automaticamente el selector de directorios empezando en `Documents` y entra al flujo de canciones cuando la ubicacion seleccionada es valida. Tambien separa el tema splash del tema normal. Es un cambio de onboarding/permisos, no solo de apariencia.
3. `1d87dd3c1` (`feat: Add liturgical season support to songs`, 11-oct): agrega temporadas/colores canonicos en `LiturgicalSeason`, con normalizacion de varios valores ingleses y espanoles; anade `Song.liturgicalSeason` con default `ordinary`; lee/escribe el valor en XML, lo edita en la ficha, lo muestra en encabezados/badges y lo integra con busqueda/filtros. SQLite sube de version 11 a 12 y migra con `ALTER TABLE ... ADD COLUMN ... DEFAULT 'ordinary'`. Nuevos strings aparecen en ingles base y espanol.
4. `ba9f976f9` (`fix: Handle empty chord pro lines...`, 13-oct): evita interpretar como encabezado una directiva ChordPro vacia en [ConvertChoPro.java](app/src/main/java/com/garethevans/church/opensongtablet/songprocessing/ConvertChoPro.java). El mismo commit amplia [PerformanceFragment.java](app/src/main/java/com/garethevans/church/opensongtablet/performance/PerformanceFragment.java): borde de pagina con color de temporada, inset del contenido y altura de pagina ajustada al contenido renderizado. Ese segundo cambio no se deduce del titulo del commit.

### Patrones que conviene reutilizar

- Para un nuevo campo de cancion, seguir la rebanada completa: modelo/copia, parseo y escritura XML, carga/importacion, editor, SQLite (constante, lectura/escritura, version y migracion), filtros/listados y presentacion. La temporada liturgica es el ejemplo mas cercano.
- Dar a los datos nuevos un default compatible con registros y archivos antiguos (`ordinary` en este caso), y normalizar valores importados antes de persistirlos. Si se amplia una API de filtros, el commit conserva la firma previa y delega a la nueva con filtros vacios.
- Para cambios de primer arranque, seguir todo el ciclo SAF: estado guardado, permisos, resultado del selector, validez del URI y destino posterior. Revisar tambien las variantes del tema de splash (base y `values-v31`).
- Para cambios visuales de la hoja de cancion, tocar la vista responsable (`PerformanceFragment`), considerar density, padding/margenes, escalado y altura real del contenido; comprobar canciones de varias columnas y distintas longitudes.
- Mantener strings en recursos y actualizar los idiomas requeridos por la funcionalidad. En el cambio de temporada se anadieron ingles/espanol, no traducciones nuevas a todos los idiomas.
- No se anadieron pruebas de comportamiento en estos cuatro commits. Para cambios similares, agregar cobertura de parseo/XML, migracion/filtro y/o renderizado donde sea viable; las pruebas existentes no cubren estos flujos.

### Cautelas al imitar la serie

- La retirada de Bible fue una eliminacion funcional extensa, no una limpieza interna. Confirmar el alcance de producto antes de eliminar pantallas o datos relacionados.
- Los commits pueden agrupar aspectos distintos (branding y eliminaciones; parser y layout). Para cambios futuros, separar commits por objetivo facilita revisar regresiones y revertir selectivamente.
- Las cifras de archivos y lineas son delta agregado desde la base, no una medida de complejidad; gran parte de las eliminaciones viene de la retirada de Bible y Showcase.
