package com.garethevans.church.opensongtablet;

import com.garethevans.church.opensongtablet.songmenu.SongReviewKey;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SongReviewKeyTest {

    @Test
    public void relatedFilesShareKeyAcrossExtensionsAndUnicodeNormalization() {
        String textKey = SongReviewKey.create("Música/Entrada", "Jesús de Nazaret.cho", "");
        String imageKey = SongReviewKey.create("musica\\entrada", "Jesus de Nazaret.PNG", "A different title");

        assertEquals(textKey, imageKey);
    }

    @Test
    public void differentFoldersRemainSeparate() {
        assertNotEquals(
                SongReviewKey.create("Entrada", "Aleluya.cho", ""),
                SongReviewKey.create("Pascua", "Aleluya.pdf", "")
        );
    }

    @Test
    public void titleIsUsedWhenFilenameIsMissing() {
        assertEquals(
                SongReviewKey.create("Maria", "", "Dolorosa"),
                SongReviewKey.create("maria", null, "DOLOROSA")
        );
    }
}
