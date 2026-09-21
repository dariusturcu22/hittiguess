package org.dariusturcu.backend.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Holds a reviewed song pool for a group's next session start, keyed by groupId, so a
// Difficulty-Based review confirmation or a Custom-mode start can hand its chosen songs
// to the same GAME_SESSION_STARTED flow the legacy start uses. GameSessionStartListener
// takes the entry when the broadcast fires, so a staged pool is consumed exactly once;
// a start that fails before the broadcast discards it, never leaking into a later start.
// Deliberately in-memory like SessionResultsStore: a staged pool is short-lived by
// nature, confirmed or abandoned within the same admin flow, with nothing worth
// persisting if the start never happens.
@Component
public class PendingSessionSongPool {

    private final Map<Long, List<Long>> songPoolByGroupId = new ConcurrentHashMap<>();

    public void stage(Long groupId, List<Long> songIds) {
        songPoolByGroupId.put(groupId, new ArrayList<>(songIds));
    }

    public Optional<List<Long>> take(Long groupId) {
        return Optional.ofNullable(songPoolByGroupId.remove(groupId));
    }

    public void discard(Long groupId) {
        songPoolByGroupId.remove(groupId);
    }
}
