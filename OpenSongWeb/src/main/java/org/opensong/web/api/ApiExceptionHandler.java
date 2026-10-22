package org.opensong.web.api;

import java.util.Map;

import org.opensong.web.song.SongLibraryException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SongLibraryException.class)
    public ResponseEntity<Map<String, String>> handleLibraryException(SongLibraryException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage()));
    }
}
