package org.dariusturcu.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final String EXPLAINED_REFUSAL_MESSAGE = "The request conflicts with existing data";
    private static final String INTERNAL_DETAIL = "database_constraint_name";
    private static final String GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void accessDeniedKeepsItsForbiddenStatus() {
        var response = exceptionHandler.handleAccessDenied(new AccessDeniedException(EXPLAINED_REFUSAL_MESSAGE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo(EXPLAINED_REFUSAL_MESSAGE);
    }

    @Test
    void anInvalidRequestKeepsItsBadRequestStatusAndMessage() {
        var response = exceptionHandler.handleIllegalArgument(new IllegalArgumentException(EXPLAINED_REFUSAL_MESSAGE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo(EXPLAINED_REFUSAL_MESSAGE);
    }

    @Test
    void responseStatusExceptionKeepsItsStatusAndMessage() {
        var response = exceptionHandler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.CONFLICT, EXPLAINED_REFUSAL_MESSAGE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo(EXPLAINED_REFUSAL_MESSAGE);
    }

    @Test
    void unexpectedRuntimeExceptionHidesItsInternalDetail() {
        var response = exceptionHandler.handleRuntimeException(new IllegalStateException(INTERNAL_DETAIL));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo(GENERIC_ERROR_MESSAGE);
    }
}
