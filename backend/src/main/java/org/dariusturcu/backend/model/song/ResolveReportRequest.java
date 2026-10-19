package org.dariusturcu.backend.model.song;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.dariusturcu.backend.validation.NotFutureYear;

/**
 * An admin's resolution of a song's open reports. The corrected year is optional, an admin can
 * resolve a report by status alone. The verification status is required and always moves the
 * song toward more trust, so `UNVERIFIED` is not a valid outcome here.
 */
public record ResolveReportRequest(
        @Min(value = 1000, message = "Corrected year must be 1000 or later")
        @NotFutureYear
        Integer correctedYear,

        @NotNull(message = "A verification status is required")
        VerificationStatus verificationStatus) {
}
