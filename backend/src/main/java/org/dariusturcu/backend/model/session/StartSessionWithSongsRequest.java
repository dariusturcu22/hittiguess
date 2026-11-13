package org.dariusturcu.backend.model.session;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// Confirms a reviewed song set into a session start: the exact song ids the admin
// reviewed, validated again on the way in.
public record StartSessionWithSongsRequest(
        @NotEmpty(message = "Song IDs cannot be empty")
        @Size(max = 200, message = "At most 200 songs can be included in a session")
        List<Long> songIds) {
}
