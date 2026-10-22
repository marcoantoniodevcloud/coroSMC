package org.opensong.web.api;

import java.util.List;

import org.opensong.web.song.SaveSongRequest;
import org.opensong.web.song.SongDocument;
import org.opensong.web.song.SongLibraryService;
import org.opensong.web.song.SongSummary;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SongController {

    private final SongLibraryService songLibrary;

    public SongController(SongLibraryService songLibrary) {
        this.songLibrary = songLibrary;
    }

    @GetMapping("/songs")
    public List<SongSummary> listSongs() {
        return songLibrary.listSongs();
    }

    @GetMapping("/song")
    public SongDocument getSong(@RequestParam String path) {
        return songLibrary.getSong(path);
    }

    @PostMapping("/song")
    public SongDocument saveSong(@RequestBody SaveSongRequest request) {
        return songLibrary.saveSong(request);
    }

    @DeleteMapping("/song")
    public ResponseEntity<Void> deleteSong(@RequestParam String path) {
        songLibrary.deleteSong(path);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export.zip")
    public ResponseEntity<byte[]> exportLibrary() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("OpenSongWeb-Songs.zip").build());
        return ResponseEntity.ok().headers(headers).body(songLibrary.exportZip());
    }
}
