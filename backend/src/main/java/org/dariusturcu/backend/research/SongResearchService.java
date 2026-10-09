package org.dariusturcu.backend.research;

import org.dariusturcu.backend.history.AccountHistoryDeleted;
import org.dariusturcu.backend.history.GameHistoryService;
import org.dariusturcu.backend.model.session.Guess;
import org.dariusturcu.backend.model.session.Round;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SongResearchService {
    public static final String OBSERVATION = "OBSERVATION";
    public static final String DELETE_PLAYER = "DELETE_PLAYER";
    public static final String CORRECT = "CORRECT";
    public static final String INCORRECT = "INCORRECT";
    public static final String NO_PLACEMENT = "NO_PLACEMENT";
    private static final String TURN_EVENT_NAMESPACE = "turn";
    private static final String GAME_CORRELATION_NAMESPACE = "game";
    private final JdbcTemplate core;
    private final ObjectMapper mapper;
    private final JdbcTemplate analytics;

    public SongResearchService(@Qualifier("coreJdbcTemplate") JdbcTemplate core,
            @Qualifier("analyticsJdbcTemplate") JdbcTemplate analytics, ObjectMapper mapper) {
        this.core = core;
        this.analytics = analytics;
        this.mapper = mapper;
    }

    @EventListener
    public void scored(ScoredTurn event) {
        Round round = event.round();
        UUID identity = core.queryForObject("""
                INSERT INTO research_player_identities(user_id, research_player_id) VALUES (?, ?)
                ON CONFLICT(user_id) DO UPDATE SET user_id = EXCLUDED.user_id RETURNING research_player_id
                """, UUID.class, round.getActivePlayer().getUser().getId(), UUID.randomUUID());
        List<Guess> guesses = round.getGuesses().stream()
                .filter(guess -> guess.getPlayer().getId().equals(round.getActivePlayer().getId())).toList();
        SongPlayObservation observation = new SongPlayObservation(stableId(TURN_EVENT_NAMESPACE, round.getId()), round.getScoredAt(),
                round.getSong().getId(), identity, stableId(GAME_CORRELATION_NAMESPACE, round.getSession().getId()),
                round.getPlacedPosition() == null ? NO_PLACEMENT : Boolean.TRUE.equals(round.getPlacementCorrect()) ? CORRECT : INCORRECT,
                round.getTimelineCardCount(), round.getValidInsertionSlotCount(),
                guesses.stream().anyMatch(guess -> guess.getGuessedTitle() != null), guesses.stream().anyMatch(Guess::isTitleCorrect),
                (int) guesses.stream().filter(guess -> guess.getGuessedArtist() != null).count(),
                (int) guesses.stream().filter(Guess::isArtistCorrect).count(),
                round.getSession().getDifficultyTier() == null ? null : round.getSession().getDifficultyTier().name(),
                GameHistoryService.RULES_VERSION);
        enqueue(observation.eventId(), OBSERVATION, mapper.writeValueAsString(observation));
    }

    @EventListener
    public void deleted(AccountHistoryDeleted deletion) {
        List<UUID> identities = core.query("SELECT research_player_id FROM research_player_identities WHERE user_id = ? FOR UPDATE",
                (result, rowNumber) -> result.getObject("research_player_id", UUID.class), deletion.userId());
        for (UUID identity : identities) {
            enqueue(UUID.randomUUID(), DELETE_PLAYER, mapper.writeValueAsString(identity));
        }
        core.update("DELETE FROM research_player_identities WHERE user_id = ?", deletion.userId());
    }

    @Transactional(readOnly = true)
    public List<SongPlayObservation> export(long userId) {
        List<UUID> identities = core.query("SELECT research_player_id FROM research_player_identities WHERE user_id = ?",
                (result, rowNumber) -> result.getObject("research_player_id", UUID.class), userId);
        if (identities.isEmpty()) {
            return List.of();
        }
        UUID identity = identities.getFirst();
        java.util.Map<UUID, SongPlayObservation> observations = new java.util.LinkedHashMap<>();
        analytics.query("SELECT row_to_json(observation)::text AS payload FROM song_play_observations observation WHERE research_player_id = ?",
                (org.springframework.jdbc.core.RowCallbackHandler) result -> {
                    var node = mapper.readTree(result.getString("payload"));
                    SongPlayObservation observation = new SongPlayObservation(UUID.fromString(node.get("event_id").asText()),
                            java.time.OffsetDateTime.parse(node.get("occurred_at").asText()).toInstant(), node.get("song_id").asLong(),
                            identity, UUID.fromString(node.get("game_correlation_id").asText()), node.get("placement_outcome").asText(),
                            node.get("timeline_card_count").asInt(), node.get("valid_insertion_slot_count").asInt(),
                            node.get("title_attempted").asBoolean(), node.get("title_correct").asBoolean(),
                            node.get("artist_attempts").asInt(), node.get("correct_artists").asInt(),
                            node.get("requested_difficulty_tier").isNull() ? null : node.get("requested_difficulty_tier").asText(),
                            node.get("rules_version").asText());
                    observations.put(observation.eventId(), observation);
                }, identity);
        core.query("SELECT payload::text FROM research_delivery_queue WHERE event_type = ? AND payload->>'researchPlayerId' = ?",
                (org.springframework.jdbc.core.RowCallbackHandler) result -> {
                    SongPlayObservation observation = mapper.readValue(result.getString("payload"), SongPlayObservation.class);
                    observations.put(observation.eventId(), observation);
                }, OBSERVATION, identity.toString());
        return List.copyOf(observations.values());
    }

    private void enqueue(UUID eventId, String eventType, String payload) {
        core.update("""
                INSERT INTO research_delivery_queue(event_id, event_type, payload) VALUES (?, ?, CAST(? AS jsonb))
                ON CONFLICT(event_id) DO NOTHING
                """, eventId, eventType, payload);
    }

    private UUID stableId(String kind, Long sourceId) {
        return UUID.nameUUIDFromBytes((kind + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
    }
}
