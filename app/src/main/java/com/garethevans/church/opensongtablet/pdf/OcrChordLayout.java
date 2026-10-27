package com.garethevans.church.opensongtablet.pdf;

import com.garethevans.church.opensongtablet.songprocessing.ChordNotationCorrector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Native counterpart of OCR_PROJECT's geometric chord/lyric association. */
public final class OcrChordLayout {
    public static final class Token {
        public final String text;
        public final float left, top, right, bottom;
        public Token(String text, float left, float top, float right, float bottom) {
            this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        float cx() { return (left + right) / 2; }
        float cy() { return (top + bottom) / 2; }
        float height() { return Math.max(1, bottom - top); }
    }
    private OcrChordLayout() { }

    public static String toChordPro(List<Token> tokens, float width) {
        List<Token> clean = new ArrayList<>();
        for (Token token : tokens) if (token != null && !token.text.trim().isEmpty()) clean.add(token);
        // Only split on an actual, persistent gutter; never guess from a single lyric line.
        if (clean.size() >= 24 && width > 0) {
            for (float fraction : new float[]{.5f, .48f, .52f, .45f, .55f}) {
                float split = width * fraction, margin = width * .015f;
                List<Token> left = new ArrayList<>(), right = new ArrayList<>(); boolean crossing = false;
                for (Token token : clean) {
                    if (token.left < split + margin && token.right > split - margin) { crossing = true; break; }
                    (token.cx() < split ? left : right).add(token);
                }
                if (!crossing && left.size() >= 8 && right.size() >= 8) {
                    return column(left) + "\n\n" + column(right);
                }
            }
        }
        return column(clean);
    }

    private static String column(List<Token> tokens) {
        tokens.sort(Comparator.comparingDouble(Token::cy).thenComparingDouble(t -> t.left));
        List<List<Token>> rows = new ArrayList<>();
        for (Token token : tokens) {
            List<Token> best = null;
            for (int i = Math.max(0, rows.size() - 4); i < rows.size(); i++) {
                List<Token> row = rows.get(i); Token first = row.get(0);
                if (Math.abs(first.cy() - token.cy()) < Math.min(first.height(), token.height()) * .5f) best = row;
            }
            if (best == null) { best = new ArrayList<>(); rows.add(best); }
            best.add(token);
        }
        rows.sort(Comparator.comparingDouble(r -> r.get(0).cy()));
        for (List<Token> row : rows) row.sort(Comparator.comparingDouble(t -> t.left));
        StringBuilder out = new StringBuilder(); List<Token> pending = new ArrayList<>(); float lastY = -1;
        for (List<Token> sourceRow : rows) {
            List<Token> row = mergeSequences(sourceRow);
            boolean chords = true;
            for (Token t : row) if (!ChordNotationCorrector.isChord(t.text)) { chords = false; break; }
            if (chords) {
                if (!pending.isEmpty()) appendIsolated(out, pending);
                pending = row; continue;
            }
            if (lastY >= 0 && row.get(0).cy() - lastY > row.get(0).height() * 3) out.append('\n');
            if (!pending.isEmpty() && row.get(0).cy() - pending.get(0).cy() > pending.get(0).height() * 4.5f) {
                appendIsolated(out, pending); pending = new ArrayList<>();
            }
            out.append(associate(row, pending)).append('\n'); pending = new ArrayList<>(); lastY = row.get(0).cy();
        }
        if (!pending.isEmpty()) appendIsolated(out, pending);
        return out.toString().trim();
    }

    private static List<Token> mergeSequences(List<Token> row) {
        List<Token> out = new ArrayList<>();
        for (int i = 0; i < row.size();) {
            Token first = row.get(i);
            int best = i;
            String text = first.text, bestText = text;
            for (int j = i + 1; j < Math.min(row.size(), i + 9); j++) {
                text += row.get(j).text;
                if (ChordNotationCorrector.sequence(text).length > 1) { best = j; bestText = text; }
            }
            out.add(best == i ? first : new Token(bestText, first.left, first.top,
                    row.get(best).right, first.bottom));
            i = best + 1;
        }
        return out;
    }

    private static void appendIsolated(StringBuilder out, List<Token> tokens) {
        for (Token token : tokens) out.append('[').append(token.text).append(']');
        out.append('\n');
    }

    private static String associate(List<Token> words, List<Token> chords) {
        StringBuilder lyric = new StringBuilder(); List<Float> xs = new ArrayList<>();
        for (Token word : words) {
            if (lyric.length() > 0) { lyric.append(' '); xs.add(word.left); }
            for (int i = 0; i < word.text.length(); i++) {
                lyric.append(word.text.charAt(i)); xs.add(word.left + (i + .5f) / word.text.length() * (word.right - word.left));
            }
        }
        List<int[]> positions = new ArrayList<>();
        for (int c = 0; c < chords.size(); c++) {
            int nearest = 0;
            for (int i = 1; i < xs.size(); i++) if (Math.abs(xs.get(i) - chords.get(c).cx()) < Math.abs(xs.get(nearest) - chords.get(c).cx())) nearest = i;
            positions.add(new int[]{nearest, c});
        }
        positions.sort((a, b) -> a[0] == b[0] ? Integer.compare(b[1], a[1]) : Integer.compare(b[0], a[0]));
        for (int[] p : positions) lyric.insert(p[0], "[" + chords.get(p[1]).text + "]");
        return lyric.toString();
    }
}
