package com.garethevans.church.opensongtablet;

import com.garethevans.church.opensongtablet.pdf.OcrChordLayout;
import com.garethevans.church.opensongtablet.pdf.OcrChordLayout.Token;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OcrChordLayoutTest {
    private Token token(String text, float x, float y) { return new Token(text, x, y, x+25, y+12); }
    @Test public void preservesShortLyricWithMiAndAssociatesChords() {
        String text = OcrChordLayout.toChordPro(Arrays.asList(token("Sol",0,0),token("Re",80,0),
                token("Mi",0,25),token("amor",30,25),token("vive",70,25)), 200);
        assertEquals("Mi amor vive", text.replaceAll("\\[[^]]*]", ""));
        assertTrue(text.contains("[Sol]")); assertTrue(text.contains("[Re]"));
    }
    @Test public void columnsReadLeftThenRightWithoutMixingVerses() {
        List<Token> tokens = new ArrayList<>();
        for (int row=0; row<4; row++) for (int col=0;col<2;col++) {
            int x=col*400;
            tokens.add(token(col==0?"izquierda":"derecha",x,row*30));
            tokens.add(token("palabra",x+80,row*30)); tokens.add(token("final",x+140,row*30));
        }
        String text=OcrChordLayout.toChordPro(tokens,800);
        assertTrue(text.lastIndexOf("izquierda") < text.indexOf("derecha"));
    }
    @Test public void isolatedChordsAndEmptyRecognitionAreNotLost() {
        assertEquals("[E7]\n[Am]", OcrChordLayout.toChordPro(Arrays.asList(token("E7",0,0), token("Am",0,30)),200));
        assertEquals("",OcrChordLayout.toChordPro(Collections.emptyList(),200));
    }
    @Test public void recognizesProgressionsEvenWhenDashHasItsOwnBox() {
        String result = OcrChordLayout.toChordPro(Arrays.asList(
                token("Rem",0,0),token("-",30,0),token("Lam",60,0),
                token("Vino",0,25),token("y",40,25),token("pan",70,25)),200);
        assertTrue(result, result.contains("[Rem-Lam]"));
        assertEquals("Vino y pan",result.replaceAll("\\[[^]]*]", ""));
        assertTrue(com.garethevans.church.opensongtablet.songprocessing.ChordNotationCorrector.isChord("La#-Sol#-Sol"));
        assertFalse(com.garethevans.church.opensongtablet.songprocessing.ChordNotationCorrector.isChord("La#-texto"));
    }
}
