package org.dariusturcu.backend.exception;

// Raised when a submitted playlist link cannot be expanded into video ids, either
// because it does not parse to a valid playlist or because the AI microservice's
// expansion call itself failed. Surfaces as a clear client error instead of the
// import silently proceeding as if the playlist link had been ignored.
public class PlaylistImportException extends RuntimeException {
    public PlaylistImportException(String message) {
        super(message);
    }
}
