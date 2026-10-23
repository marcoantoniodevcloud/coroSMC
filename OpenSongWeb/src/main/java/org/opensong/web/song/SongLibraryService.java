package org.opensong.web.song;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SongLibraryService {

    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_AUTHOR_LENGTH = 160;
    private static final int MAX_CHORDPRO_LENGTH = 1_000_000;
    private static final Pattern TITLE_DIRECTIVE =
            Pattern.compile("(?im)^\\s*\\{(?:title|t):\\s*([^}\\r\\n]*)}.*$");
    private static final Pattern ARTIST_DIRECTIVE =
            Pattern.compile("(?im)^\\s*\\{(?:artist|author):\\s*([^}\\r\\n]*)}.*$");
    private static final Pattern COPYRIGHT_DIRECTIVE =
            Pattern.compile("(?im)^\\s*\\{copyright:\\s*([^}\\r\\n]*)}.*$");
    private static final Pattern MANAGED_DIRECTIVE =
            Pattern.compile("(?im)^\\s*\\{(?:title|t|artist|author|copyright):[^\\r\\n]*}\\s*(?:\\R|$)");
    private static final Set<String> WINDOWS_RESERVED_NAMES =
            Set.of("CON", "PRN", "AUX", "NUL");

    private final Path songsRoot;
    private final Path reviewStatusFile;

    @Autowired
    public SongLibraryService(@Value("${opensongweb.library-path}") String libraryPath,
                              @Value("${opensongweb.songs-path:}") String configuredSongsPath) {
        songsRoot = configuredSongsPath.isBlank()
                ? Path.of(libraryPath).toAbsolutePath().normalize().resolve("Songs").normalize()
                : Path.of(configuredSongsPath).toAbsolutePath().normalize();
        reviewStatusFile = Path.of(libraryPath).toAbsolutePath().normalize().resolve("review-status.txt");
        try {
            Files.createDirectories(songsRoot);
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo crear la carpeta de canciones.", e);
        }
    }

    public SongLibraryService(String libraryPath) {
        this(libraryPath, "");
    }

    public List<SongSummary> listSongs() {
        try (var paths = Files.walk(songsRoot)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pro"))
                    .map(this::readSummary)
                    .sorted(Comparator.comparing(SongSummary::folder, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(SongSummary::title, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer la biblioteca de canciones.", e);
        } catch (UncheckedIOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer uno de los archivos de la biblioteca.", e.getCause());
        }
    }

    public List<LibrarySong> listLibrary() {
        Set<String> reviewedSongs = readReviewedSongs();
        try (var paths = Files.walk(songsRoot)) {
            Map<String, LibraryGroup> groups = new LinkedHashMap<>();
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> libraryKind(extension(path)) != null)
                    .sorted(Comparator.comparing(path -> songsRoot.relativize(path).toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> {
                        String relative = songsRoot.relativize(path).toString().replace('\\', '/');
                        String filename = path.getFileName().toString();
                        String title = filename.substring(0, filename.lastIndexOf('.'));
                        String folder = songsRoot.relativize(path.getParent()).toString().replace('\\', '/');
                        if (folder.equals(".")) {
                            folder = "";
                        }
                        String groupFolder = folder;
                        String key = normalizeGroupPart(groupFolder) + "/" + normalizeGroupPart(title);
                        LibraryGroup group = groups.computeIfAbsent(key,
                                ignored -> new LibraryGroup(title, groupFolder, reviewedSongs.contains(reviewKey(
                                        groupFolder, title))));
                        String ext = extension(path);
                        group.files.add(new LibraryFile(relative, filename, ext, libraryKind(ext)));
                    });
            return groups.values().stream()
                    .map(group -> new LibrarySong(group.files.get(0).path(), group.title,
                            group.folder, List.copyOf(group.files), group.reviewed))
                    .toList();
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer la biblioteca de canciones.", e);
        } catch (UncheckedIOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer uno de los archivos de la biblioteca.", e.getCause());
        }
    }

    public synchronized void setSongReviewed(ReviewSongRequest request) {
        if (request == null || request.path() == null || request.reviewed() == null) {
            throw badRequest("No se recibieron los datos del estado de revisión.");
        }
        Path path = resolveLibraryFile(request.path(), Set.of("cho", "pro", "pdf", "png", "jpg", "jpeg", "webp"));
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new SongLibraryException(HttpStatus.NOT_FOUND, "No se encontró el canto.");
        }

        String filename = path.getFileName().toString();
        String title = filename.substring(0, filename.lastIndexOf('.'));
        String folder = songsRoot.relativize(path.getParent()).toString().replace('\\', '/');
        if (folder.equals(".")) {
            folder = "";
        }

        Set<String> reviewedSongs = readReviewedSongs();
        String key = reviewKey(folder, title);
        if (request.reviewed()) {
            reviewedSongs.add(key);
        } else {
            reviewedSongs.remove(key);
        }
        writeReviewedSongs(reviewedSongs);
    }

    public ChordProFile getChordProFile(String id) {
        Path path = resolveLibraryFile(id, Set.of("cho", "pro"));
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new SongLibraryException(HttpStatus.NOT_FOUND, "No se encontró el archivo ChordPro.");
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return new ChordProFile(id, content, revision(content));
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer el archivo ChordPro.", e);
        }
    }

    public ChordProFile saveChordProFile(SaveChordProRequest request) {
        if (request == null || request.content() == null || request.path() == null) {
            throw badRequest("No se recibieron los datos del archivo ChordPro.");
        }
        if (request.content().length() > MAX_CHORDPRO_LENGTH) {
            throw badRequest("El archivo ChordPro supera el tamaño máximo permitido.");
        }
        Path path = resolveLibraryFile(request.path(), Set.of("cho", "pro"));
        String currentRevision = null;
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new SongLibraryException(HttpStatus.CONFLICT,
                        "La ruta de destino ya no es un archivo regular.");
            }
            try {
                currentRevision = revision(Files.readString(path, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo comprobar el archivo ChordPro.", e);
            }
        }
        if (!java.util.Objects.equals(request.revision(), currentRevision)) {
            throw new SongLibraryException(HttpStatus.CONFLICT,
                    "El archivo cambió desde que lo abriste. Recarga la canción antes de guardar.");
        }
        writeLibraryFileAtomically(path, request.content());
        return new ChordProFile(request.path(), request.content(), revision(request.content()));
    }

    public byte[] getMedia(String id) {
        Path path = resolveLibraryFile(id, Set.of("pdf", "png", "jpg", "jpeg", "webp"));
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new SongLibraryException(HttpStatus.NOT_FOUND, "No se encontró el archivo de referencia.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer el archivo de referencia.", e);
        }
    }

    public void deleteLibraryFile(String id) {
        Path path = resolveLibraryFile(id, Set.of("cho", "pro", "pdf", "png", "jpg", "jpeg", "webp"));
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new SongLibraryException(HttpStatus.NOT_FOUND, "No se encontró el archivo seleccionado.");
        }
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo eliminar el archivo seleccionado.", e);
        }
    }

    public SongDocument getSong(String id) {
        Path path = resolveSongPath(id);
        if (!Files.isRegularFile(path)) {
            throw new SongLibraryException(HttpStatus.NOT_FOUND, "No se encontró la canción.");
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return toDocument(path, content);
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo leer la canción.", e);
        }
    }

    public SongDocument saveSong(SaveSongRequest request) {
        if (request == null) {
            throw badRequest("No se recibieron datos de la canción.");
        }

        String title = validateDirectiveValue(
                requiredText(request.title(), "El título es obligatorio.", MAX_TITLE_LENGTH));
        String author = validateDirectiveValue(
                optionalText(request.author(), MAX_AUTHOR_LENGTH, "El autor es demasiado largo."));
        String copyright = validateDirectiveValue(
                optionalText(request.copyright(), MAX_AUTHOR_LENGTH, "El copyright/álbum es demasiado largo."));
        String chordPro = requiredText(request.chordPro(), "La letra CHORDPRO es obligatoria.",
                MAX_CHORDPRO_LENGTH);
        String folder = normalizeFolder(request.folder());

        Path previousPath = request.id() == null || request.id().isBlank()
                ? null : resolveSongPath(request.id());
        if (previousPath != null && !Files.isRegularFile(previousPath)) {
            throw new SongLibraryException(HttpStatus.NOT_FOUND, "No se encontró la canción que se quiere editar.");
        }

        String filename = toFilename(title) + ".pro";
        Path targetPath = songsRoot.resolve(folder).resolve(filename).normalize();
        ensureInsideSongsRoot(targetPath);
        if (!targetPath.equals(previousPath) && Files.exists(targetPath)) {
            throw new SongLibraryException(HttpStatus.CONFLICT,
                    "Ya existe una canción con ese título en la carpeta seleccionada.");
        }

        String storedContent = withMetadata(title, author, copyright, chordPro);
        writeAtomically(targetPath, storedContent);

        if (previousPath != null && !targetPath.equals(previousPath)) {
            try {
                Files.delete(previousPath);
                removeEmptyParents(previousPath.getParent());
            } catch (IOException e) {
                throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "La canción se guardó con su nueva ruta, pero no se pudo eliminar el archivo anterior.", e);
            }
        }
        return toDocument(targetPath, storedContent);
    }

    public void deleteSong(String id) {
        Path path = resolveSongPath(id);
        if (!Files.isRegularFile(path)) {
            throw new SongLibraryException(HttpStatus.NOT_FOUND, "No se encontró la canción.");
        }
        try {
            Files.delete(path);
            removeEmptyParents(path.getParent());
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo eliminar la canción.", e);
        }
    }

    public byte[] exportZip() {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8);
             var paths = Files.walk(songsRoot)) {
            zip.putNextEntry(new ZipEntry("Songs/"));
            zip.closeEntry();
            List<Path> songs = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pro"))
                    .sorted()
                    .toList();
            for (Path song : songs) {
                String relativePath = songsRoot.relativize(song).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry("Songs/" + relativePath));
                Files.copy(song, zip);
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("IMPORTAR.txt"));
            zip.write(("Biblioteca OpenSongWeb en formato CHORDPRO.\n"
                    + "Extrae la carpeta Songs dentro de la carpeta OpenSong de tu dispositivo.\n"
                    + "Después vuelve a indexar las canciones desde OpenSong Tablet.\n")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo crear el ZIP de la biblioteca.", e);
        }
    }

    private SongSummary readSummary(Path path) {
        try {
            return toSummary(path, Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo " + path.getFileName(), e);
        }
    }

    private SongDocument toDocument(Path path, String content) {
        SongSummary summary = toSummary(path, content);
        return new SongDocument(summary.id(), summary.title(), summary.author(), summary.copyright(),
                summary.folder(), content);
    }

    private SongSummary toSummary(Path path, String content) {
        String id = songsRoot.relativize(path).toString().replace('\\', '/');
        String folder = songsRoot.relativize(path.getParent()).toString().replace('\\', '/');
        if (folder.equals(".")) {
            folder = "";
        }
        String title = findDirective(TITLE_DIRECTIVE, content);
        if (title.isBlank()) {
            title = removeProExtension(path.getFileName().toString());
        }
        return new SongSummary(id, title, findDirective(ARTIST_DIRECTIVE, content),
                findDirective(COPYRIGHT_DIRECTIVE, content), folder);
    }

    private String withMetadata(String title, String author, String copyright, String chordPro) {
        String body = MANAGED_DIRECTIVE.matcher(chordPro).replaceAll("").strip();
        StringBuilder result = new StringBuilder()
                .append("{title: ").append(title).append("}\n");
        if (!author.isBlank()) {
            result.append("{artist: ").append(author).append("}\n");
        }
        if (!copyright.isBlank()) {
            result.append("{copyright: ").append(copyright).append("}\n");
        }
        if (!body.isBlank()) {
            result.append('\n').append(body);
        }
        return result.append('\n').toString();
    }

    private String findDirective(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "";
        }
        String normalized = folder.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw badRequest("La carpeta debe ser una ruta relativa.");
        }

        List<String> segments = new ArrayList<>();
        for (String rawSegment : normalized.split("/")) {
            String segment = rawSegment.trim();
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || !segment.matches("[\\p{L}\\p{N} _().-]{1,80}")
                    || segment.endsWith(".") || isWindowsReservedName(segment)) {
                throw badRequest("La ruta de carpeta contiene un nombre no permitido.");
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private String toFilename(String title) {
        String slug = Normalizer.normalize(title, Normalizer.Form.NFC)
                .replaceAll("[^\\p{L}\\p{N}._ -]", "-")
                .trim()
                .replaceAll("[ .]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^[.-]+|[.-]+$", "");
        if (slug.isBlank()) {
            throw badRequest("El título no contiene caracteres válidos para crear el archivo.");
        }
        if (slug.length() > 120) {
            slug = slug.substring(0, 120).replaceAll("[.-]+$", "");
        }
        if (slug.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")) {
            slug = "_" + slug;
        }
        return slug;
    }

    private Path resolveSongPath(String id) {
        if (id == null || id.isBlank() || id.startsWith("/") || id.startsWith("\\")
                || id.matches("^[A-Za-z]:.*")) {
            throw badRequest("La ruta de canción no es válida.");
        }
        String[] segments = id.replace('\\', '/').split("/", -1);
        if (segments.length == 0) {
            throw badRequest("La ruta de canción no es válida.");
        }
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            boolean valid = index == segments.length - 1
                    ? segment.matches("[\\p{L}\\p{N}._-]{1,124}\\.pro")
                    : segment.matches("[\\p{L}\\p{N} _().-]{1,80}");
            if (!valid || segment.equals(".") || segment.equals("..")
                    || segment.endsWith(".") || isWindowsReservedName(segment)) {
                throw badRequest("La ruta de canción no es válida.");
            }
        }
        Path path = songsRoot.resolve(String.join("/", segments)).normalize();
        ensureInsideSongsRoot(path);
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pro")) {
            throw badRequest("Solo se admiten archivos CHORDPRO .pro.");
        }
        try {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(path)
                    || !path.toRealPath().startsWith(songsRoot.toRealPath()))) {
                throw badRequest("La ruta de canción atraviesa un enlace simbólico no permitido.");
            }
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo comprobar la ruta de la canción.", e);
        }
        return path;
    }

    private Path resolveLibraryFile(String id, Set<String> allowedExtensions) {
        if (id == null || id.isBlank() || id.startsWith("/") || id.startsWith("\\")
                || id.matches("^[A-Za-z]:.*")) {
            throw badRequest("La ruta del archivo no es válida.");
        }
        String normalizedId = id.replace('\\', '/');
        String[] segments = normalizedId.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")
                    || segment.endsWith(".") || segment.endsWith(" ")
                    || segment.matches(".*[<>:\"|?*\\p{Cntrl}].*")) {
                throw badRequest("La ruta del archivo no es válida.");
            }
        }
        Path path = songsRoot.resolve(normalizedId).normalize();
        ensureInsideSongsRoot(path);
        String extension = extension(path);
        if (!allowedExtensions.contains(extension)) {
            throw badRequest("El tipo de archivo no está permitido.");
        }
        try {
            Path current = songsRoot;
            for (String segment : segments) {
                current = current.resolve(segment);
                if (Files.isSymbolicLink(current)) {
                    throw badRequest("No se permiten enlaces simbólicos en la biblioteca.");
                }
            }
            if (Files.exists(path.getParent())
                    && !path.getParent().toRealPath().startsWith(songsRoot.toRealPath())) {
                throw badRequest("La ruta del archivo está fuera de la biblioteca.");
            }
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo comprobar la ruta del archivo.", e);
        }
        return path;
    }

    private void writeLibraryFileAtomically(Path target, String content) {
        try {
            if (!Files.isDirectory(target.getParent())) {
                throw new SongLibraryException(HttpStatus.NOT_FOUND,
                        "La carpeta de destino ya no existe.");
            }
            Path tempFile = Files.createTempFile(target.getParent(), ".opensongweb-", ".tmp");
            try {
                Files.writeString(tempFile, content, StandardCharsets.UTF_8);
                try {
                    Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el archivo ChordPro.", e);
        }
    }

    private String extension(Path path) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String libraryKind(String extension) {
        return switch (extension) {
            case "cho", "pro" -> "chordpro";
            case "pdf" -> "pdf";
            case "png", "jpg", "jpeg", "webp" -> "image";
            default -> null;
        };
    }

    private String normalizeGroupPart(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String reviewKey(String folder, String title) {
        return revision(normalizeGroupPart(folder) + "/" + normalizeGroupPart(title));
    }

    private Set<String> readReviewedSongs() {
        if (!Files.exists(reviewStatusFile)) {
            return new HashSet<>();
        }
        try {
            Set<String> reviewedSongs = new HashSet<>();
            for (String key : Files.readAllLines(reviewStatusFile, StandardCharsets.UTF_8)) {
                if (!key.matches("[a-f0-9]{64}")) {
                    throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "El archivo de estados de revisión contiene datos no válidos.");
                }
                reviewedSongs.add(key);
            }
            return reviewedSongs;
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudieron leer los estados de revisión.", e);
        }
    }

    private void writeReviewedSongs(Set<String> reviewedSongs) {
        try {
            Files.createDirectories(reviewStatusFile.getParent());
            Path tempFile = Files.createTempFile(reviewStatusFile.getParent(), ".opensongweb-review-", ".tmp");
            try {
                Files.write(tempFile, reviewedSongs.stream().sorted().toList(), StandardCharsets.UTF_8);
                try {
                    Files.move(tempFile, reviewStatusFile, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, reviewStatusFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el estado de revisión.", e);
        }
    }

    private String revision(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static final class LibraryGroup {
        private final String title;
        private final String folder;
        private final boolean reviewed;
        private final List<LibraryFile> files = new ArrayList<>();

        private LibraryGroup(String title, String folder, boolean reviewed) {
            this.title = title;
            this.folder = folder;
            this.reviewed = reviewed;
        }
    }

    private void ensureInsideSongsRoot(Path path) {
        if (!path.startsWith(songsRoot) || path.equals(songsRoot)) {
            throw badRequest("La ruta solicitada está fuera de la biblioteca.");
        }
    }

    private String requiredText(String value, String message, int maxLength) {
        String text = value == null ? "" : value.strip();
        if (text.isBlank()) {
            throw badRequest(message);
        }
        if (text.length() > maxLength) {
            throw badRequest("Uno de los campos supera el tamaño máximo permitido.");
        }
        return text;
    }

    private String optionalText(String value, int maxLength, String message) {
        String text = value == null ? "" : value.strip();
        if (text.length() > maxLength) {
            throw badRequest(message);
        }
        return text;
    }

    private String validateDirectiveValue(String value) {
        if (value.contains("\n") || value.contains("\r") || value.contains("{") || value.contains("}")) {
            throw badRequest("El título y los metadatos deben ser texto de una sola línea.");
        }
        return value;
    }

    private void writeAtomically(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            if (Files.isSymbolicLink(target)
                    || !target.getParent().toRealPath().startsWith(songsRoot.toRealPath())) {
                throw badRequest("La ruta de canción atraviesa un enlace simbólico no permitido.");
            }
            Path tempFile = Files.createTempFile(target.getParent(), ".opensongweb-", ".tmp");
            try {
                Files.writeString(tempFile, content, StandardCharsets.UTF_8);
                try {
                    Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar la canción en disco.", e);
        }
    }

    private void removeEmptyParents(Path directory) throws IOException {
        Path current = directory;
        while (current != null && current.startsWith(songsRoot) && !current.equals(songsRoot)) {
            try (var children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private String removeProExtension(String filename) {
        return filename.substring(0, filename.length() - ".pro".length());
    }

    private boolean isWindowsReservedName(String name) {
        String baseName = name.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        return WINDOWS_RESERVED_NAMES.contains(baseName)
                || baseName.matches("(?:COM|LPT)[1-9]");
    }

    private SongLibraryException badRequest(String message) {
        return new SongLibraryException(HttpStatus.BAD_REQUEST, message);
    }
}
