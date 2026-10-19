package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.session.SessionResultsDTO;
import org.dariusturcu.backend.model.session.StoredSessionResults;
import org.dariusturcu.backend.repository.StoredSessionResultsRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Holds the most recent completed session's results per group, keyed by groupId, so the
// results screen and export stay available after GameSessionService purges the session
// rows themselves. Written through to the session_results table so a restart doesn't
// lose them, with the in-memory map as a cache in front of it. A later completed session
// for the same group replaces the entry.
@Component
@RequiredArgsConstructor
public class SessionResultsStore {

    // A group's results together with the users who played that session, the only ones
    // allowed to read them.
    public record PlayedResults(SessionResultsDTO results, Set<Long> playerUserIds) {
        public boolean wasPlayedBy(Long userId) {
            return playerUserIds.contains(userId);
        }
    }

    private final StoredSessionResultsRepository storedSessionResultsRepository;
    private final ObjectMapper objectMapper;
    private final Map<Long, PlayedResults> resultsByGroupId = new ConcurrentHashMap<>();

    public void store(Long groupId, SessionResultsDTO results, Set<Long> playerUserIds) {
        StoredSessionResults storedResults = new StoredSessionResults();
        storedResults.setGroupId(groupId);
        storedResults.setResults(objectMapper.writeValueAsString(results));
        storedResults.setStoredAt(Instant.now());
        storedResults.setPlayerUserIds(new HashSet<>(playerUserIds));
        storedSessionResultsRepository.save(storedResults);
        resultsByGroupId.put(groupId, new PlayedResults(results, Set.copyOf(playerUserIds)));
    }

    public Optional<PlayedResults> get(Long groupId) {
        PlayedResults cachedResults = resultsByGroupId.get(groupId);
        if (cachedResults != null) {
            return Optional.of(cachedResults);
        }
        return storedSessionResultsRepository.findById(groupId)
                .map(storedResults -> new PlayedResults(
                        objectMapper.readValue(storedResults.getResults(), SessionResultsDTO.class),
                        Set.copyOf(storedResults.getPlayerUserIds())))
                .map(playedResults -> {
                    resultsByGroupId.put(groupId, playedResults);
                    return playedResults;
                });
    }
}
