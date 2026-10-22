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
}
