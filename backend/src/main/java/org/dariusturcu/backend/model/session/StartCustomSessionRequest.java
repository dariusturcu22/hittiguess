package org.dariusturcu.backend.model.session;

import jakarta.validation.constraints.Size;

// Starts a Custom-mode session from exactly one source: an accessible playlist by id
// (owned, member of, or public), or a YouTube playlist link or id pasted directly.
public record StartCustomSessionRequest(
        Long playlistId,
        @Size(max = 2000, message = "Playlist link must be at most 2000 characters")
        String playlistLink
) {
}
