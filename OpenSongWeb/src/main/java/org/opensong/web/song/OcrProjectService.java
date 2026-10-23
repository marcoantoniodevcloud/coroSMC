package org.opensong.web.song;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;

@Service
public class OcrProjectService {

    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(15);
    private static final Pattern PAGE_NUMBER = Pattern.compile("_page(\\d+)\\.cho$", Pattern.CASE_INSENSITIVE);

    private final String configuredPython;

    @Autowired
    public OcrProjectService(@Value("${opensongweb.python-command:}") String configuredPython) {
        this.configuredPython = configuredPython == null ? "" : configuredPython.trim();
    }

    public OcrProjectService() {
        this("");
    }

    public String recognize(Path inputFile) {
        Path workingDirectory = null;
        try {
            workingDirectory = Files.createTempDirectory("opensongweb-ocr-");
            Path script = workingDirectory.resolve("cantoral_ocr.py");
            try (var source = new ClassPathResource("ocr_project/cantoral_ocr.py").getInputStream()) {
                Files.copy(source, script);
            }
            Path outputDirectory = Files.createDirectory(workingDirectory.resolve("output"));
            Path logFile = workingDirectory.resolve("process.log");
            List<String> command = new java.util.ArrayList<>(List.of(pythonExecutable(),
                    script.toString(), inputFile.toString(), "-o", outputDirectory.toString()));
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile());
            // Windows may otherwise encode Python's Spanish status output using the
            // active legacy code page while Java always reads this log as UTF-8.
            processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
            processBuilder.environment().put("PYTHONUTF8", "1");
            Process process = processBuilder.start();
            try {
                if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    throw new SongLibraryException(HttpStatus.GATEWAY_TIMEOUT,
                            "El proceso OCR excedió el límite de 15 minutos.");
                }
                String log = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
                if (process.exitValue() != 0) {
                    throw new SongLibraryException(HttpStatus.BAD_GATEWAY,
                            "OCR_PROJECT no pudo procesar el archivo. " + tail(log));
                }
                List<Path> pages;
                try (Stream<Path> files = Files.list(outputDirectory)) {
                    pages = files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cho"))
                            .sorted(Comparator.comparingInt(OcrProjectService::pageNumber)
                                    .thenComparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                            .toList();
                }
                if (pages.isEmpty()) {
                    throw new SongLibraryException(HttpStatus.BAD_GATEWAY,
                            "OCR_PROJECT terminó sin generar texto para este archivo. " + tail(log));
                }
                return pages.stream().map(path -> {
                    try {
                        return Files.readString(path, StandardCharsets.UTF_8).strip();
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }).filter(content -> !content.isBlank()).collect(java.util.stream.Collectors.joining("\n\n"));
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new SongLibraryException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Se interrumpió el proceso OCR.", e);
            } catch (IOException e) {
                process.destroyForcibly();
                throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo leer el resultado de OCR_PROJECT: " + e.getMessage(), e);
            } catch (java.io.UncheckedIOException e) {
                throw new SongLibraryException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo leer una página generada por OCR_PROJECT.", e.getCause());
            }
        } catch (SongLibraryException e) {
            throw e;
        } catch (IOException e) {
            throw new SongLibraryException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo iniciar Python o extraer el OCR incluido en OpenSongWeb. Revisa OPENSONGWEB_PYTHON.", e);
        } finally {
            deleteTemporaryDirectory(workingDirectory);
        }
    }

    private String pythonExecutable() {
        if (!configuredPython.isBlank()) {
            return configuredPython;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String executable = windows ? "Scripts/python.exe" : "bin/python";
        Path workingDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path candidate : List.of(workingDirectory.resolve("OpenSongWeb/.venv").resolve(executable),
                workingDirectory.resolve(".venv").resolve(executable))) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return windows ? "python" : "python3";
    }

    private static int pageNumber(Path path) {
        Matcher matcher = PAGE_NUMBER.matcher(path.getFileName().toString());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    private static String tail(String value) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.length() > 1_800) {
            trimmed = trimmed.substring(trimmed.length() - 1_800);
        }
        return trimmed.isBlank() ? "" : "Detalle: " + trimmed;
    }

    private static void deleteTemporaryDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary files are best-effort cleanup after each run.
                }
            });
        } catch (IOException ignored) {
            // Temporary files are best-effort cleanup after each run.
        }
    }
}
