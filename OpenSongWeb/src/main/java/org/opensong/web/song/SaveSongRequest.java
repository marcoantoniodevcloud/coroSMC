package org.opensong.web.song;

public record SaveSongRequest(String id, String title, String author, String copyright,
                              String folder, String chordPro) {
}
