package com.garethevans.church.opensongtablet;

import com.garethevans.church.opensongtablet.chords.Transpose;
import com.garethevans.church.opensongtablet.songprocessing.Song;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChordSequenceTransposeTest {
    private String transpose(String chords, int format, String direction, int steps) throws Exception {
        Transpose engine = new Transpose(null);
        Object[][] fields = {{"oldChordFormat",format},{"newChordFormat",1},
                {"transposeDirection",direction},{"transposeTimes",steps},{"convertToSharps",true}};
        for (Object[] entry : fields) {
            java.lang.reflect.Field field = Transpose.class.getDeclaredField((String)entry[0]);
            field.setAccessible(true); field.set(engine,entry[1]);
        }
        Song song = new Song(); song.setKey("C"); song.setLyrics("." + chords + "\n Vino y pan");
        String result = engine.transposeString(song);
        assertTrue(result, result.endsWith(" Vino y pan"));
        return result.split("\n")[0].replaceAll("\\s", "");
    }
    @Test public void transposesEveryMemberAndBassAndKeepsSeparators() throws Exception {
        assertEquals(".Em—Bm",transpose("Rem — Lam",1,"+1",2));
        assertEquals(".Em-BmC-A#-AD/A-Em/G",transpose("Rem-Lam La#-Sol#-Sol Do/Sol-Rem/Fa",1,"+1",2));
    }
    @Test public void solfegeGroupsAndDescendingTransposeUseExistingEngine() throws Exception {
        assertEquals(".Dm-AmA#-G#-G",transpose("Rem-Lam La#-Sol#-Sol",4,"+1",0));
        assertEquals(".Cm-GmG#-F#-F",transpose("Dm-Am A#-G#-G",1,"-1",2));
    }
}
