package org.opensong.web.song;

public record SongDocument(String id, String title, String author, String copyright,
                           String folder, String chordPro) {
}
