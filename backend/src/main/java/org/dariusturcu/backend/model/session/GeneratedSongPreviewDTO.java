package org.dariusturcu.backend.model.session;

import java.util.List;

// Preview of one generated song for the admin's review step: identity and release facts
// only, never the youtubeId the round flow withholds until the reveal.
public record GeneratedSongPreviewDTO(Long id, String title, List<String> artists, int releaseYear) {
}
