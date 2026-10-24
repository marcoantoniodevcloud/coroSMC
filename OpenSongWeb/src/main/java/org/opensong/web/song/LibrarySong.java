package org.opensong.web.song;

import java.util.List;

public record LibrarySong(String id, String title, String folder, List<LibraryFile> files, boolean reviewed,
                          boolean needsAttention, String reviewNotes) {
}
