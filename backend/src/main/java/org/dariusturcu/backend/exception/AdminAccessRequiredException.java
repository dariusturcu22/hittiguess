package org.dariusturcu.backend.exception;

public class AdminAccessRequiredException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Admin access is required for this operation";

    public AdminAccessRequiredException() {
        super(DEFAULT_MESSAGE);
    }
}
