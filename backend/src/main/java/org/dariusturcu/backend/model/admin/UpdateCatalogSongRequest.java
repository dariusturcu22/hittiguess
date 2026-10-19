package org.dariusturcu.backend.model.admin;

import org.dariusturcu.backend.model.song.VerificationStatus;

public record UpdateCatalogSongRequest(Integer releaseYear, VerificationStatus verificationStatus) {
}
