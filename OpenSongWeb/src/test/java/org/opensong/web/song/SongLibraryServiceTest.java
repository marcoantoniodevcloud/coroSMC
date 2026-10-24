package org.opensong.web.song;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class SongLibraryServiceTest {

    @TempDir
    Path temporaryDirectory;

    private SongLibraryService library;

    @BeforeEach
    void setUp() {
        library = new SongLibraryService(temporaryDirectory.toString());
    }

    @Test
    void uploadsAndReplacesBinaryReferencesThroughHttpController() throws Exception {
        Files.writeString(temporaryDirectory.resolve("Songs/Canto.cho"), "Letra");
        var http = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new org.opensong.web.api.SongController(library))
                .setControllerAdvice(new org.opensong.web.api.ApiExceptionHandler()).build();
        byte[] original = "%PDF-1.4 original".getBytes(StandardCharsets.US_ASCII);
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/library/media")
                .param("path", "Canto.cho").param("filename", "scan.pdf")
                .contentType("application/octet-stream").content(original))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.path").value("Canto.pdf"));
        byte[] replacement = "%PDF-1.7 replacement".getBytes(StandardCharsets.US_ASCII);
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/library/media")
                .param("path", "Canto.cho").param("filename", "crop.pdf").param("replace", "Canto.pdf")
                .contentType("application/octet-stream").content(replacement))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/library/media").param("path", "Canto.pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(replacement));
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/library/media")
                .param("path", "Canto.cho").param("filename", "fake.png")
                .contentType("application/octet-stream").content(original))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void uploadsReferenceAndReplacesWithBackupWithoutChangingLyrics() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.writeString(songs.resolve("Canto.cho"), "[C]Letra original");
        library.setSongReviewed(new ReviewSongRequest("Canto.cho", true));
        byte[] original = "%PDF-1.4 original".getBytes(StandardCharsets.US_ASCII);
        LibraryFile file = library.uploadMedia("Canto.cho", null, "mi documento.pdf", original);
        assertEquals("Canto.pdf", file.path());
        assertEquals(2, library.listLibrary().getFirst().files().size());
        assertFalse(library.listLibrary().getFirst().reviewed());
        assertThrows(SongLibraryException.class,
                () -> library.uploadMedia("Canto.cho", null, "otra.pdf", original));
        byte[] replacement = "%PDF-1.7 replacement".getBytes(StandardCharsets.US_ASCII);
        library.uploadMedia("Canto.cho", "Canto.pdf", "recortado.pdf", replacement);
        assertEquals("%PDF-1.7 replacement", Files.readString(songs.resolve("Canto.pdf")));
        assertEquals("[C]Letra original", Files.readString(songs.resolve("Canto.cho")));
        try (var paths = Files.walk(temporaryDirectory.resolve("media-backups"))) {
            List<Path> backups = paths.filter(Files::isRegularFile).toList();
            assertEquals(1, backups.size());
            assertEquals("%PDF-1.4 original", Files.readString(backups.getFirst()));
        }
    }

    @Test
    void rejectsInvalidMediaAndReplacementOfAnotherSong() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.writeString(songs.resolve("Canto.cho"), "Letra");
        Files.writeString(songs.resolve("Otro.pdf"), "%PDF-1.4 original");
        byte[] pdf = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
        assertThrows(SongLibraryException.class, () -> library.uploadMedia("Canto.cho", null, "fake.png", pdf));
        assertThrows(SongLibraryException.class, () -> library.uploadMedia("Canto.cho", null, "../test.pdf", pdf));
        assertThrows(SongLibraryException.class, () -> library.uploadMedia("Canto.cho", "Otro.pdf", "test.pdf", pdf));
        assertThrows(SongLibraryException.class, () -> library.uploadMedia("Canto.cho", null, "test.pdf", new byte[0]));
        assertThrows(SongLibraryException.class, () -> library.uploadMedia("missing.cho", null, "test.pdf", pdf));
        assertEquals("%PDF-1.4 original", Files.readString(songs.resolve("Otro.pdf")));
        assertFalse(Files.exists(songs.resolve("Canto.pdf")));
    }

    @Test
    void acceptsSupportedImageSignaturesAndRejectsWrongReplacementFormat() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.writeString(songs.resolve("Canto.cho"), "Letra");
        byte[] png = new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10, 0};
        library.uploadMedia("Canto.cho", null, "scan.png", png);
        library.uploadMedia("Canto.cho", "Canto.png", "crop.png", png);
        byte[] jpg = new byte[]{(byte)255, (byte)216, (byte)255, 0};
        assertThrows(SongLibraryException.class, () -> library.uploadMedia("Canto.cho", "Canto.png", "crop.jpg", jpg));
        library.uploadMedia("Canto.cho", null, "scan.jpg", jpg);
        library.uploadMedia("Canto.cho", "Canto.jpg", "scan.jpeg", jpg);
        library.uploadMedia("Canto.cho", null, "scan.webp", "RIFF0000WEBPtest".getBytes(StandardCharsets.US_ASCII));
        assertEquals(4, library.listLibrary().getFirst().files().size());
    }

    @Test
    void renamesAllCompanionsAndPreservesReviewNotesAndContent() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.createDirectories(songs.resolve("Entrada"));
        String content = "{title: Título interno}\n[Do]Letra";
        Files.writeString(songs.resolve("Entrada/Canto.cho"), content);
        Files.write(songs.resolve("Entrada/Canto.pdf"), new byte[]{0, 1, 2, 3});
        Files.writeString(songs.resolve("revision-cantos.json"), """
                {"songs":[{"needsAttention":true,"notes":"Comprobar acordes",
                  "files":["Entrada/Canto.pdf"]}]}
                """);
        library.setSongReviewed(new ReviewSongRequest("Entrada/Canto.cho", true));
        LibrarySong renamed = library.renameSong(new RenameSongRequest("Entrada/Canto.pdf", "Nuevo nombre"));
        assertEquals(2, renamed.files().size());
        assertEquals("Nuevo nombre", renamed.title());
        assertTrue(renamed.reviewed());
        assertFalse(Files.exists(songs.resolve("Entrada/Canto.cho")));
        assertFalse(Files.exists(songs.resolve("Entrada/Canto.pdf")));
        assertEquals(content, Files.readString(songs.resolve("Entrada/Nuevo nombre.cho")));
        assertEquals(4, Files.size(songs.resolve("Entrada/Nuevo nombre.pdf")));
        library.setSongReviewed(new ReviewSongRequest("Entrada/Nuevo nombre.cho", false));
        LibrarySong pending = library.listLibrary().getFirst();
        assertTrue(pending.needsAttention());
        assertEquals("Comprobar acordes", pending.reviewNotes());
    }

    @Test
    void rejectsInvalidAndCollidingNamesWithoutChangingFiles() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.writeString(songs.resolve("Canto.cho"), "original");
        Files.writeString(songs.resolve("Otro.pdf"), "reference");
        for (String name : List.of("../Fuera", "CON", "NUL.txt", "COM1", "a/b", "a\\b", "Final.", " Otro", "", "Canto", "Ótro")) {
            if (name.equals("Canto")) continue;
            assertThrows(SongLibraryException.class,
                    () -> library.renameSong(new RenameSongRequest("Canto.cho", name)), name);
        }
        assertThrows(SongLibraryException.class,
                () -> library.renameSong(new RenameSongRequest("missing.cho", "Nuevo")));
        assertEquals("original", Files.readString(songs.resolve("Canto.cho")));
        assertEquals("reference", Files.readString(songs.resolve("Otro.pdf")));
    }

    @Test
    void invalidReviewReportPreventsAnyPartialRename() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.writeString(songs.resolve("Canto.cho"), "original");
        Files.writeString(songs.resolve("revision-cantos.json"), "broken json");
        assertThrows(SongLibraryException.class,
                () -> library.renameSong(new RenameSongRequest("Canto.cho", "Nuevo")));
        assertTrue(Files.exists(songs.resolve("Canto.cho")));
        assertFalse(Files.exists(songs.resolve("Nuevo.cho")));
    }

    @Test
    void correctsFilesWithRevisionCheckAndBacksUpOnlyModifiedContent() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.createDirectories(songs);
        String original = "{title: Mi canto}\n[Do]Mi alma [Re/Fa#]canta";
        Files.writeString(songs.resolve("Canto.cho"), original);
        ChordProFile current = library.getChordProFile("Canto.cho");
        assertThrows(SongLibraryException.class, () -> library.correctSong(
                new CorrectSongRequest("american-chords", null, "Canto.cho", "stale")));
        ChordProFile saved = library.correctSong(new CorrectSongRequest(
                "american-chords", null, "Canto.cho", current.revision()));
        assertEquals("{title: Mi canto}\n[C]Mi alma [D/F#]canta", saved.content());
        Path backups = temporaryDirectory.resolve("correction-backups");
        try (var files = Files.walk(backups)) {
            List<Path> copies = files.filter(Files::isRegularFile).toList();
            assertEquals(1, copies.size());
            assertEquals(original, Files.readString(copies.getFirst()));
        }
        library.correctSong(new CorrectSongRequest("american-chords", null, "Canto.cho", saved.revision()));
        try (var files = Files.walk(backups)) {
            assertEquals(1, files.filter(Files::isRegularFile).count());
        }
        assertEquals("[C]Texto sin guardar", library.correctSong(
                new CorrectSongRequest("american-chords", "[Do]Texto sin guardar", null, null)).content());
        assertThrows(SongLibraryException.class, () -> library.correctSong(
                new CorrectSongRequest("unknown", "[Do]Texto", null, null)));
    }

    @Test
    void showsAttentionNotesForRelatedFilesUntilManuallyReviewed() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.createDirectories(songs.resolve("Entrada"));
        Files.writeString(songs.resolve("Entrada/Canto.cho"), "{title: Canto}\n[C]Letra");
        Files.writeString(songs.resolve("Entrada/Canto.png"), "reference");
        Files.writeString(songs.resolve("revision-cantos.json"), """
                {"songs":[{"needsAttention":true,"notes":"Revisar manuscrito <original>",
                  "files":["Entrada/Canto.png"]}]}
                """);
        LibrarySong pending = library.listLibrary().getFirst();
        assertTrue(pending.needsAttention());
        assertEquals("Revisar manuscrito <original>", pending.reviewNotes());
        assertEquals(1, library.listLibrary().size());
        library.setSongReviewed(new ReviewSongRequest("Entrada/Canto.cho", true));
        assertFalse(library.listLibrary().getFirst().needsAttention());
        library.setSongReviewed(new ReviewSongRequest("Entrada/Canto.cho", false));
        assertTrue(library.listLibrary().getFirst().needsAttention());
    }

    @Test
    void savesChordProWithMetadataAndKeepsNestedFoldersInExport() throws Exception {
        SongDocument saved = library.saveSong(new SaveSongRequest(null, "Luz del Camino",
                "Ana Pérez", "Álbum parroquial", "Litúrgico/Adviento",
                "[G]Brilla tu luz [D]en mí"));

        assertEquals("Litúrgico/Adviento/Luz-del-Camino.pro", saved.id());
        assertTrue(saved.chordPro().startsWith("{title: Luz del Camino}\n"
                + "{artist: Ana Pérez}\n"
                + "{copyright: Álbum parroquial}\n\n"));
        assertEquals(1, library.listSongs().size());

        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(library.exportZip()), StandardCharsets.UTF_8)) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                entries.add(entry.getName());
                entry = zip.getNextEntry();
            }
        }

        assertTrue(entries.contains("Songs/"));
        assertTrue(entries.contains("Songs/Litúrgico/Adviento/Luz-del-Camino.pro"));
        assertTrue(entries.contains("IMPORTAR.txt"));
    }

    @Test
    void updatesSongAndMovesItsFileWhenFolderChanges() throws Exception {
        SongDocument original = library.saveSong(new SaveSongRequest(null, "Canto Nuevo",
                "Compositor", "", "Entrada", "[C]Una canción"));

        SongDocument updated = library.saveSong(new SaveSongRequest(original.id(), "Canto Nuevo",
                "Compositor", "", "Salida", "[F]Otra letra"));

        assertEquals("Salida/Canto-Nuevo.pro", updated.id());
        assertFalse(Files.exists(temporaryDirectory.resolve("Songs/Entrada/Canto-Nuevo.pro")));
        assertEquals(1, library.listSongs().size());
    }

    @Test
    void rejectsUnsafePathsAndDirectiveInjection() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(SongLibraryException.class,
                () -> library.saveSong(new SaveSongRequest(null, "Canto", "", "", "../outside", "[C]Letra")))
                .getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(SongLibraryException.class,
                () -> library.saveSong(new SaveSongRequest(null, "Canto", "", "", "CON", "[C]Letra")))
                .getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(SongLibraryException.class,
                () -> library.getSong("../outside.pro")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(SongLibraryException.class,
                () -> library.saveSong(new SaveSongRequest(null, "Canto\n{artist: atacante}",
                        "", "", "", "[C]Letra"))).getStatus());
    }

    @Test
    void groupsRelatedFilesWithoutLosingUnmatchedReferences() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Files.writeString(songs.resolve("Gema.cho"), "[Em]Voy caminando");
        Files.write(songs.resolve("gema.PDF"), new byte[]{1, 2, 3});
        Files.write(songs.resolve("GÉMA.jpg"), new byte[]{4, 5, 6});
        Files.write(songs.resolve("Solo imagen.png"), new byte[]{7, 8, 9});

        List<LibrarySong> librarySongs = library.listLibrary();

        assertEquals(2, librarySongs.size());
        LibrarySong gema = librarySongs.stream()
                        .filter(song -> song.title().equals("Gema"))
                        .findFirst().orElseThrow();
        assertEquals(3, gema.files().size());
        assertTrue(gema.files().stream().anyMatch(file -> file.kind().equals("chordpro")));
        assertTrue(gema.files().stream().anyMatch(file -> file.kind().equals("pdf")));
        assertTrue(gema.files().stream().anyMatch(file -> file.kind().equals("image")));
        assertEquals("Solo imagen", librarySongs.stream()
                        .filter(song -> song.title().equals("Solo imagen"))
                        .findFirst().orElseThrow().title());
    }

    @Test
    void savesChordProOnlyWhenItHasNotChangedExternally() throws Exception {
        Path songPath = temporaryDirectory.resolve("Songs/Ensayo.cho");
        Files.writeString(songPath, "[C]Versión original");
        ChordProFile opened = library.getChordProFile("Ensayo.cho");

        ChordProFile saved = library.saveChordProFile(
                        new SaveChordProRequest(opened.path(), "[G]Versión editada", opened.revision()));

        assertEquals("[G]Versión editada", Files.readString(songPath));
        assertEquals(saved.revision(), library.getChordProFile("Ensayo.cho").revision());
        Files.writeString(songPath, "[D]Cambio externo");
        assertEquals(HttpStatus.CONFLICT, assertThrows(SongLibraryException.class,
                        () -> library.saveChordProFile(new SaveChordProRequest(opened.path(),
                                "[A]No sobrescribir", opened.revision()))).getStatus());
        assertEquals("[D]Cambio externo", Files.readString(songPath));
    }

    @Test
    void createsChordProFileAndRejectsPathsOutsideLibrary() {
        ChordProFile created = library.saveChordProFile(
                        new SaveChordProRequest("Nueva.cho", "[Am]Canto", null));

        assertEquals("[Am]Canto", library.getChordProFile(created.path()).content());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(SongLibraryException.class,
                        () -> library.getChordProFile("../fuera.cho")).getStatus());
    }

    @Test
    void deletesOnlyTheSelectedRelatedFileAndRejectsUnsupportedTypes() throws Exception {
        Path songs = temporaryDirectory.resolve("Songs");
        Path chordPro = songs.resolve("Grupo/Canto.cho");
        Path pdf = songs.resolve("Grupo/Canto.pdf");
        Files.createDirectories(chordPro.getParent());
        Files.writeString(chordPro, "[C]Letra");
        Files.write(pdf, new byte[]{1, 2, 3});

        library.deleteLibraryFile("Grupo/Canto.pdf");

        assertTrue(Files.exists(chordPro));
        assertFalse(Files.exists(pdf));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(SongLibraryException.class,
                () -> library.deleteLibraryFile("../fuera.pdf")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(SongLibraryException.class,
                () -> library.deleteLibraryFile("Grupo/Canto.txt")).getStatus());
    }

    @Test
    void persistsReviewStateForPdfOnlySongsOutsideTheSongsFolder() throws Exception {
        Path songsFolder = temporaryDirectory.resolve("DefaultSongs");
        Path pdfFolder = songsFolder.resolve("María");
        Files.createDirectories(pdfFolder);
        Files.write(pdfFolder.resolve("Canto de prueba.pdf"), new byte[]{1, 2, 3});
        SongLibraryService directLibrary = new SongLibraryService(
                temporaryDirectory.toString(), songsFolder.toString());

        directLibrary.setSongReviewed(new ReviewSongRequest("María/Canto de prueba.pdf", true));

        LibrarySong reviewed = directLibrary.listLibrary().getFirst();
        assertTrue(reviewed.reviewed());
        assertTrue(Files.exists(pdfFolder.resolve("Canto de prueba.pdf")));
        assertFalse(Files.exists(songsFolder.resolve("review-status.txt")));
        assertTrue(Files.exists(temporaryDirectory.resolve("review-status.txt")));

        SongLibraryService restartedLibrary = new SongLibraryService(
                temporaryDirectory.toString(), songsFolder.toString());
        assertTrue(restartedLibrary.listLibrary().getFirst().reviewed());

        restartedLibrary.setSongReviewed(new ReviewSongRequest("María/Canto de prueba.pdf", false));
        assertFalse(restartedLibrary.listLibrary().getFirst().reviewed());
    }

    @Test
    void supportsUsingAnExistingSongsFolderDirectly() throws Exception {
        Path directFolder = temporaryDirectory.resolve("DefaultSongs");
        Files.createDirectories(directFolder);
        Files.writeString(directFolder.resolve("Canto.cho"), "[C]Letra");
        SongLibraryService directLibrary = new SongLibraryService(
                        temporaryDirectory.toString(), directFolder.toString());

        assertEquals(1, directLibrary.listLibrary().size());
        assertEquals("[C]Letra", directLibrary.getChordProFile("Canto.cho").content());
        assertFalse(Files.exists(directFolder.resolve("Songs")));
    }
}
