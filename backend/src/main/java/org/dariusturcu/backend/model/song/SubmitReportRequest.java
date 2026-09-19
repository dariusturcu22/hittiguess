package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.dariusturcu.backend.validation.NotFutureYear;

/**
 * A community report against a song. The message explaining what is wrong is required; a
 * suggested correct year and supporting sources are optional, since a report can flag a
 * problem without proposing a fix.
 */
public record SubmitReportRequest(
        @NotBlank(message = "A report message is required")
        String message,

        @Min(value = 1000, message = "Suggested year must be 1000 or later")
        @NotFutureYear
        Integer suggestedCorrectYear,

        String sources) {
}
