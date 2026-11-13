package org.dariusturcu.backend.model.admin;

import jakarta.validation.constraints.Min;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.validation.NotFutureYear;

public record UpdateCatalogSongRequest(
        @Min(value = 1000, message = "Release year must be 1000 or later")
        @NotFutureYear
        Integer releaseYear,
        VerificationStatus verificationStatus) {
}
