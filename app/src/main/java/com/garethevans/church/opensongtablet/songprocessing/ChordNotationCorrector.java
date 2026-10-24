package com.garethevans.church.opensongtablet.songprocessing;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit chord conversion for ChordPro and native OpenSong chord rows. */
public final class ChordNotationCorrector {
    private static final Pattern CHORD = Pattern.compile("(?i)^(Do|Re|Mi|Fa|Sol|La|Si|[A-G])([#b♯♭]?)((?:(?:maj|min|dim|aug|sus|add|omit|no|m)|[0-9#b♯♭+°ø(),-])*)(?:/(Do|Re|Mi|Fa|Sol|La|Si|[A-G])([#b♯♭]?))?$");
    private static final Pattern LABEL = Pattern.compile("\\[([^\\[\\]\\r\\n]+)]");
    private static final Pattern DIRECTIVE = Pattern.compile("(?i)(\\{(?:key|define|chord):[ \\t]*)([^}\\s]+)");
    private ChordNotationCorrector() { }

    public static boolean isChord(String text) { return CHORD.matcher(text.trim()).matches(); }

    public static String chord(String text) {
        Matcher m = CHORD.matcher(text.trim());
        if (!m.matches()) return text;
        return root(m.group(1)) + accidental(m.group(2)) + m.group(3)
                + (m.group(4) == null ? "" : "/" + root(m.group(4)) + accidental(m.group(5)));
    }

    private static String root(String text) {
        switch (text.toLowerCase(Locale.ROOT)) {
            case "do": return "C";
            case "re": return "D";
            case "mi": return "E";
            case "fa": return "F";
            case "sol": return "G";
            case "la": return "A";
            case "si": return "B";
            default: return text.toUpperCase(Locale.ROOT);
        }
    }

    private static String accidental(String s) { return s.replace('♯', '#').replace('♭', 'b').replace('B', 'b'); }

    public static String convert(String content) {
        if (content == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : content.split("(?<=\\n)", -1)) {
            if (line.trim().startsWith("#")) { out.append(line); continue; }
            if (line.startsWith(".")) {
                Matcher tokens = Pattern.compile("\\S+").matcher(line.substring(1));
                StringBuffer row = new StringBuffer();
                while (tokens.find()) {
                    String converted = chord(tokens.group());
                    StringBuilder padded = new StringBuilder(converted);
                    while (padded.length() < tokens.group().length()) padded.append(' ');
                    tokens.appendReplacement(row, Matcher.quoteReplacement(padded.toString()));
                }
                tokens.appendTail(row); out.append('.').append(row); continue;
            }
            Matcher directives = Pattern.compile("\\{[^}]*}").matcher(line);
            int end = 0;
            while (directives.find()) {
                out.append(labels(line.substring(end, directives.start())));
                Matcher key = DIRECTIVE.matcher(directives.group());
                StringBuffer value = new StringBuffer();
                while (key.find()) key.appendReplacement(value, Matcher.quoteReplacement(key.group(1) + chord(key.group(2))));
                key.appendTail(value); out.append(value); end = directives.end();
            }
            out.append(labels(line.substring(end)));
        }
        return out.toString();
    }

    private static String labels(String value) {
        Matcher m = LABEL.matcher(value); StringBuffer out = new StringBuffer();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement("[" + chord(m.group(1)) + "]"));
        m.appendTail(out); return out.toString();
    }

    public static String correctFile(String filename, String content) {
        if (!content.replaceFirst("^\\uFEFF", "").trim().startsWith("<")) return convert(content);
        Matcher fields = Pattern.compile("(?is)(<(lyrics|key)(?:\\s[^>]*)?>)(.*?)(</\\2>)").matcher(content);
        StringBuffer out = new StringBuffer();
        while (fields.find()) {
            String value = fields.group(3);
            boolean cdata = value.startsWith("<![CDATA[") && value.endsWith("]]>" );
            if (cdata) value = value.substring(9, value.length()-3);
            value = fields.group(2).equalsIgnoreCase("key") ? chord(value) : convert(value);
            if (cdata) value = "<![CDATA[" + value + "]]>";
            fields.appendReplacement(out, Matcher.quoteReplacement(fields.group(1) + value + fields.group(4)));
        }
        fields.appendTail(out); return out.toString();
    }
}
