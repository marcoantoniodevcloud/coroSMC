package com.garethevans.church.opensongtablet;

import com.garethevans.church.opensongtablet.songprocessing.ChordNotationCorrector;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChordNotationCorrectorTest {
    @Test public void convertsModifiersAndBassButNotLyricsOrMetadata() {
        String before = "{title: Mi canto [Do]}\n{comment: [Re]}\nMi alma [Do] [Sol#m7] [Re/Fa#] [Sibmaj7] [Lam(add9)]";
        String after = "{title: Mi canto [Do]}\n{comment: [Re]}\nMi alma [C] [G#m7] [D/F#] [Bbmaj7] [Am(add9)]";
        assertEquals(after, ChordNotationCorrector.convert(before));
        assertEquals(after, ChordNotationCorrector.convert(after));
    }
    @Test public void nativeChordRowsKeepTheirHorizontalPositions() {
        String source = ".Do     Rem/Sol# Si7\r\n Mi alma canta\r\n";
        String converted = ChordNotationCorrector.convert(source);
        assertEquals(source.indexOf("Rem"), converted.indexOf("Dm"));
        assertEquals(source.indexOf("Si7"), converted.indexOf("B7"));
        assertTrue(converted.endsWith("\r\n Mi alma canta\r\n"));
    }
    @Test public void xmlPreservesMetadataAndChangesOnlyLyricsAndKey() {
        String before = "<song><title>Mi canción [Do]</title><key>Rem</key><lyrics>.Do   Sol\n Mi alma</lyrics><author>La cantante</author></song>";
        String expected = "<song><title>Mi canción [Do]</title><key>Dm</key><lyrics>.C    G  \n Mi alma</lyrics><author>La cantante</author></song>";
        assertEquals(expected, ChordNotationCorrector.correctFile("test.xml", before));
    }
    @Test public void cdataAndCommentsArePreserved() {
        assertEquals("<song><lyrics><![CDATA[[C]Mi alma]]></lyrics></song>",
                ChordNotationCorrector.correctFile("test", "<song><lyrics><![CDATA[[Do]Mi alma]]></lyrics></song>"));
        assertEquals("# [Do]\n[Intro] [texto] [C/G]", ChordNotationCorrector.convert("# [Do]\n[Intro] [texto] [Do/Sol]"));
        assertEquals("[Bb] [Eb/Gb]",ChordNotationCorrector.convert("[SIB] [MIB/SOLB]"));
        assertEquals("\uFEFF<song><key>Dm</key></song>",ChordNotationCorrector.correctFile("song.xml","\uFEFF<song><key>Rem</key></song>"));
    }
    @Test public void preservesAndConvertsCompleteChordSequences() {
        assertEquals("[Dm-Am] [A#-G#-G] [C/G–Dm/F] [C-7] [G7(b9)] [C-texto]",
                ChordNotationCorrector.convert("[Rem-Lam] [La#-Sol#-Sol] [Do/Sol–Rem/Fa] [C-7] [G7(b9)] [C-texto]"));
        assertEquals("[Dm — Am]", ChordNotationCorrector.convert("[Rem — Lam]"));
    }
}
