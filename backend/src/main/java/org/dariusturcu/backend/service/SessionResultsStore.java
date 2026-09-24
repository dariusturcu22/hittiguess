package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.session.SessionResultsDTO;
import org.dariusturcu.backend.model.session.StoredSessionResults;
import org.dariusturcu.backend.repository.StoredSessionResultsRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Holds the most recent completed session's results per group, keyed by groupId, so the
// results screen and export stay available after GameSessionService purges the session
// rows themselves. Written through to the session_results table so a restart doesn't
// lose them, with the in-memory map as a cache in front of it. A later completed session
// for the same group replaces the entry.
@Component
@RequiredArgsConstructor
public class SessionResultsStore {

    private final StoredSessionResultsRepository storedSessionResultsRepository;
    private final ObjectMapper objectMapper;
    private final Map<Long, SessionResultsDTO> resultsByGroupId = new ConcurrentHashMap<>();

    public void store(Long groupId, SessionResultsDTO results) {
        StoredSessionResults storedResults = new StoredSessionResults();
        storedResults.setGroupId(groupId);
        storedResults.setResults(objectMapper.writeValueAsString(results));
        storedResults.setStoredAt(Instant.now());
        storedSessionResultsRepository.save(storedResults);
        resultsByGroupId.put(groupId, results);
    }

    public Optional<SessionResultsDTO> get(Long groupId) {
        SessionResultsDTO cachedResults = resultsByGroupId.get(groupId);
        if (cachedResults != null) {
            return Optional.of(cachedResults);
        }
        return storedSessionResultsRepository.findById(groupId)
                .map(storedResults -> objectMapper.readValue(storedResults.getResults(), SessionResultsDTO.class))
                .map(results -> {
                    resultsByGroupId.put(groupId, results);
                    return results;
                });
    }
}
