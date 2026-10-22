package org.opensong.web.song;

import org.springframework.http.HttpStatus;

public class SongLibraryException extends RuntimeException {

    private final HttpStatus status;

    public SongLibraryException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public SongLibraryException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
