package com.garethevans.church.opensongtablet;
import com.garethevans.church.opensongtablet.songprocessing.Song;
import org.junit.Test;
import static org.junit.Assert.*;
public class TextSongDisplayTest {
    @Test public void textSourcesRenderWithoutPretendingToBeXmlFiles() {
        Song song = new Song();
        for (String type : new String[]{"XML","CHO","TXT","iOS"}) {
            song.setFiletype(type); assertTrue(type,song.shouldShowLyrics());
            assertFalse(song.shouldShowMediaReference());
        }
        song.setFiletype("IMG");song.setLyrics("");assertFalse(song.shouldShowLyrics());
        assertTrue(song.shouldShowMediaReference());
    }
}
