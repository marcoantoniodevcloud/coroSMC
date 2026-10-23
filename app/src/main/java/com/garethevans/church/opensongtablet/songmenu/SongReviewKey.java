package com.garethevans.church.opensongtablet.songmenu;

import java.text.Normalizer;
import java.util.Locale;

public final class SongReviewKey {
    private static final String[] SONG_EXTENSIONS = {
            ".chordpro", ".chopro", ".jpeg", ".heif", ".heic", ".webp",
            ".xml", ".cho", ".crd", ".pro", ".pdf", ".jpg", ".png",
            ".gif", ".bmp"
    };

    private SongReviewKey() {
    }

    public static String create(String folder, String filename, String title) {
        String songName = normalizeFilename(filename);
        if (songName.isEmpty()) {
            songName = normalize(title);
        }
        return normalizeFolder(folder) + "|" + songName;
    }

    private static String normalizeFolder(String folder) {
        return normalize(folder).replace('\\', '/').replaceAll("^/+|/+$", "");
    }

    private static String normalizeFilename(String filename) {
        return normalize(fileBaseName(filename));
    }

    public static String fileBaseName(String filename) {
        String name = filename == null ? "" : filename.replace('\\', '/');
        int separator = name.lastIndexOf('/');
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        for (String extension : SONG_EXTENSIONS) {
            if (lowerName.endsWith(extension) && name.length() > extension.length()) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }
}
