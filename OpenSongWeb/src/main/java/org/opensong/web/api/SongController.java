package org.opensong.web.api;

import java.util.List;

import org.opensong.web.song.ChordProFile;
import org.opensong.web.song.CorrectSongRequest;
import org.opensong.web.song.LibrarySong;
import org.opensong.web.song.OcrSongRequest;
import org.opensong.web.song.ReviewSongRequest;
import org.opensong.web.song.SaveChordProRequest;
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

    @GetMapping("/library")
    public List<LibrarySong> listLibrary() {
        return songLibrary.listLibrary();
    }

    @PostMapping("/library/review")
    public ResponseEntity<Void> setSongReviewed(@RequestBody ReviewSongRequest request) {
        songLibrary.setSongReviewed(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/library/ocr")
    public ChordProFile runOcr(@RequestBody OcrSongRequest request) {
        return songLibrary.runOcr(request);
    }

    @PostMapping("/library/correct")
    public ChordProFile correctSong(@RequestBody CorrectSongRequest request) {
        return songLibrary.correctSong(request);
    }

    @GetMapping("/library/chordpro")
    public ChordProFile getChordPro(@RequestParam String path) {
        return songLibrary.getChordProFile(path);
    }

    @PostMapping("/library/chordpro")
    public ChordProFile saveChordPro(@RequestBody SaveChordProRequest request) {
        return songLibrary.saveChordProFile(request);
    }

    @PostMapping("/library/rename")
    public LibrarySong renameSong(@RequestBody org.opensong.web.song.RenameSongRequest request) {
        return songLibrary.renameSong(request);
    }

    @PostMapping(value = "/library/media", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public org.opensong.web.song.LibraryFile uploadMedia(@RequestParam String path,
            @RequestParam String filename, @RequestParam(required = false) String replace,
            @RequestBody byte[] content) {
        return songLibrary.uploadMedia(path, replace, filename, content);
    }

    @GetMapping("/library/media")
    public ResponseEntity<byte[]> getMedia(@RequestParam String path) {
        String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        MediaType mediaType = switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(songLibrary.getMedia(path));
    }

    @DeleteMapping("/library/file")
    public ResponseEntity<Void> deleteLibraryFile(@RequestParam String path) {
        songLibrary.deleteLibraryFile(path);
        return ResponseEntity.noContent().build();
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
