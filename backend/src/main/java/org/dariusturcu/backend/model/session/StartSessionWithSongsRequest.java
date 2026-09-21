package org.dariusturcu.backend.model.session;

import java.util.List;

// Confirms a reviewed song set into a session start: the exact song ids the admin
// reviewed, validated again on the way in.
public record StartSessionWithSongsRequest(List<Long> songIds) {
}
