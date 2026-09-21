package org.dariusturcu.backend.model.session;

// Starts a Custom-mode session from exactly one source: an accessible playlist by id
// (owned, member of, or public), or a YouTube playlist link or id pasted directly.
public record StartCustomSessionRequest(Long playlistId, String playlistLink) {
}
