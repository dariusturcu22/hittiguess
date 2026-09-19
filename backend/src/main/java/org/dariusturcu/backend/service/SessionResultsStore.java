package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.session.SessionResultsDTO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Holds the most recent completed session's results per group, keyed by groupId, so the
// export stays fetchable for a short while after GameSessionService purges the session
// rows themselves. Deliberately in-memory rather than a persisted table: a downloadable
// export is explicitly the one thing story 10 keeps past the purge, but nothing in the
// story calls for keeping it forever or across a restart, and a new session starting
// (and later completing) for the same group naturally overwrites the old entry.
@Component
public class SessionResultsStore {

    private final Map<Long, SessionResultsDTO> resultsByGroupId = new ConcurrentHashMap<>();

    public void store(Long groupId, SessionResultsDTO results) {
        resultsByGroupId.put(groupId, results);
    }

    public Optional<SessionResultsDTO> get(Long groupId) {
        return Optional.ofNullable(resultsByGroupId.get(groupId));
    }
}
