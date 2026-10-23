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
