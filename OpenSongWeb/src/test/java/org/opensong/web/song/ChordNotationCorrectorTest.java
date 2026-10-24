package org.opensong.web.song;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class ChordNotationCorrectorTest {
    @Test
    void convertsRootsAlterationsAndBassWithoutTouchingLyrics() {
        assertEquals("Mi alma [C]canta [D/F#]a [G#m7]Ti [Bbmaj7] [Bdim] [Am(add9)]",
                ChordNotationCorrector.convert("Mi alma [Do]canta [Re/Fa#]a [Sol#m7]Ti [Sibmaj7] [Sidim] [Lam(add9)]"));
    }

    @Test
    void preservesMetadataCommentsAndUnknownLabelsAndIsIdempotent() {
        String input = "{title: Mi canto [Do]}\n{comment: Re y [Mi]}\n[Intro] La luz [C] [G/B] [Fa#m7b5] [REsus4]";
        String expected = "{title: Mi canto [Do]}\n{comment: Re y [Mi]}\n[Intro] La luz [C] [G/B] [F#m7b5] [Dsus4]";
        assertEquals(expected, ChordNotationCorrector.convert(input));
        assertEquals(expected, ChordNotationCorrector.convert(expected));
    }

    @Test
    void convertsKeysAndDefinitionsAndPreservesLineEndings() {
        assertEquals("{key: Eb}\r\n{define: F#m base-fret 2 frets 0 0 0}\r\n{chord: C/G}",
                ChordNotationCorrector.convert("{key: Mib}\r\n{define: Fa#m base-fret 2 frets 0 0 0}\r\n{chord: Do/Sol}"));
    }

    @Test
    void preservesHashCommentsAndUnknownChordSyntax() {
        String input = "# {key: Do} [Do] comentario\n  # [Re] también\n[Do]Mi [Do/no-es-bajo] [Reina] [texto]";
        assertEquals("# {key: Do} [Do] comentario\n  # [Re] también\n[C]Mi [Do/no-es-bajo] [Reina] [texto]",
                ChordNotationCorrector.convert(input));
    }
}
