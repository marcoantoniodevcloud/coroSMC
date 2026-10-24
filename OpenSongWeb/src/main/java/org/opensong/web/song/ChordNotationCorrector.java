package org.opensong.web.song;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Converts explicit ChordPro chord labels, never ordinary lyric words. */
public final class ChordNotationCorrector {
    private static final Map<String, String> ROOTS = Map.of(
            "do", "C", "re", "D", "mi", "E", "fa", "F", "sol", "G", "la", "A", "si", "B");
    private static final Pattern ROOT = Pattern.compile("(?i)^(Do|Re|Mi|Fa|Sol|La|Si|[A-G])([#b♯♭]?)(.*)$");
    private static final Pattern SUFFIX = Pattern.compile(
            "(?i)(?:(?:maj|min|dim|aug|sus|add|omit|no|m)|[0-9#b♯♭+°ø(),-])*");
    private static final Pattern LABEL = Pattern.compile("\\[([^\\[\\]\\r\\n]+)]");
    private static final Pattern DIRECTIVE = Pattern.compile(
            "(?im)(\\{(?:key|define|chord):[ \\t]*)([^}\\s]+)");

    private ChordNotationCorrector() { }

    public static String convert(String content) {
        var comments = Pattern.compile("(?m)^[ \\t]*#[^\\r\\n]*").matcher(content);
        StringBuilder output = new StringBuilder();
        int end = 0;
        while (comments.find()) {
            output.append(convertContent(content.substring(end, comments.start())));
            output.append(comments.group());
            end = comments.end();
        }
        return output.append(convertContent(content.substring(end))).toString();
    }

    private static String convertContent(String content) {
        // Comments and all other directive values are prose, even when they contain brackets.
        StringBuilder output = new StringBuilder();
        var directives = Pattern.compile("\\{[^}]*}").matcher(content);
        int end = 0;
        while (directives.find()) {
            output.append(convertLabels(content.substring(end, directives.start())));
            output.append(DIRECTIVE.matcher(directives.group()).replaceAll(m ->
                    java.util.regex.Matcher.quoteReplacement(m.group(1) + chord(m.group(2)))));
            end = directives.end();
        }
        return output.append(convertLabels(content.substring(end))).toString();
    }

    private static String convertLabels(String text) {
        return LABEL.matcher(text).replaceAll(m ->
                java.util.regex.Matcher.quoteReplacement("[" + chord(m.group(1)) + "]"));
    }

    private static String chord(String text) {
        String trimmed = text.strip();
        String[] parts = trimmed.split("/", -1);
        if (parts.length > 2) return text;
        var root = ROOT.matcher(parts[0]);
        if (!root.matches() || !SUFFIX.matcher(root.group(3)).matches()) return text;
        String result = rootName(root.group(1)) + accidental(root.group(2)) + root.group(3);
        if (parts.length == 2) {
            var bass = ROOT.matcher(parts[1]);
            if (!bass.matches() || !bass.group(3).isEmpty()) return text;
            result += "/" + rootName(bass.group(1)) + accidental(bass.group(2));
        }
        return result;
    }

    private static String rootName(String root) {
        return ROOTS.getOrDefault(root.toLowerCase(Locale.ROOT), root.toUpperCase(Locale.ROOT));
    }

    private static String accidental(String text) {
        return text.replace('♯', '#').replace('♭', 'b');
    }
}
