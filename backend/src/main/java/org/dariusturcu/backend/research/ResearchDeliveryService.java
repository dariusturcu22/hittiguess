package org.dariusturcu.backend.research;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ResearchDeliveryService {
    private static final int DELIVERY_BATCH_SIZE = 50;
    private static final int TIMELINE_SIZE_BAND_WIDTH = 5;
    private final JdbcTemplate core;
    private final JdbcTemplate analytics;
    private final ObjectMapper mapper;
    private final TransactionTemplate analyticsTransaction;
    private static final int DEFAULT_RETENTION_DAYS = 365;
    @Value("${analytics.retention.days:365}")
    private int retentionDays = DEFAULT_RETENTION_DAYS;

    public ResearchDeliveryService(@Qualifier("coreJdbcTemplate") JdbcTemplate core,
            @Qualifier("analyticsJdbcTemplate") JdbcTemplate analytics, ObjectMapper mapper) {
        this.core = core;
        this.analytics = analytics;
        this.mapper = mapper;
        this.analyticsTransaction = new TransactionTemplate(new JdbcTransactionManager(analytics.getDataSource()));
    }

    @Transactional
    public int purgeExpiredQueuedObservations() {
        return core.update("""
                DELETE FROM research_delivery_queue WHERE event_type = ?
                AND (payload->>'occurredAt')::timestamptz < ?
                """, SongResearchService.OBSERVATION,
                Timestamp.from(Instant.now().minus(retentionDays, ChronoUnit.DAYS)));
    }

    @Transactional
    public int deliverBatch() {
        List<ResearchQueueEntry> entries = core.query("""
                SELECT event_id, event_type, payload::text FROM research_delivery_queue
                ORDER BY queued_at, event_id LIMIT ? FOR UPDATE SKIP LOCKED
                """, (result, rowNumber) -> new ResearchQueueEntry(result.getObject("event_id", UUID.class),
                result.getString("event_type"), result.getString("payload")), DELIVERY_BATCH_SIZE);
        for (ResearchQueueEntry entry : entries) {
            analyticsTransaction.executeWithoutResult(status -> deliver(entry));
            core.update("DELETE FROM research_delivery_queue WHERE event_id = ?", entry.eventId());
        }
        return entries.size();
    }

    private void deliver(ResearchQueueEntry entry) {
        int inserted = analytics.update("INSERT INTO research_processed_events(event_id) VALUES (?) ON CONFLICT DO NOTHING", entry.eventId());
        if (inserted == 0) {
            return;
        }
        if (SongResearchService.DELETE_PLAYER.equals(entry.eventType())) {
            UUID identity = mapper.readValue(entry.payload(), UUID.class);
            lockIdentity(identity);
            analytics.update("INSERT INTO research_deleted_players(research_player_id) VALUES (?) ON CONFLICT DO NOTHING", identity);
            analytics.update("UPDATE song_play_observations SET research_player_id = NULL WHERE research_player_id = ?", identity);
            return;
        }
        SongPlayObservation observation = mapper.readValue(entry.payload(), SongPlayObservation.class);
        lockIdentity(observation.researchPlayerId());
        UUID identity = analytics.queryForObject("SELECT EXISTS(SELECT 1 FROM research_deleted_players WHERE research_player_id = ?)",
                Boolean.class, observation.researchPlayerId()) ? null : observation.researchPlayerId();
        analytics.update("""
                INSERT INTO song_play_observations(event_id, occurred_at, song_id, research_player_id, game_correlation_id,
                  placement_outcome, timeline_card_count, valid_insertion_slot_count, title_attempted, title_correct,
                  artist_attempts, correct_artists, requested_difficulty_tier, rules_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, observation.eventId(), Timestamp.from(observation.occurredAt()), observation.songId(), identity,
                observation.gameCorrelationId(), observation.placementOutcome(), observation.timelineCardCount(),
                observation.validInsertionSlotCount(), observation.titleAttempted(), observation.titleCorrect(),
                observation.artistAttempts(), observation.correctArtists(), observation.requestedDifficultyTier(), observation.rulesVersion());
        boolean attempted = !SongResearchService.NO_PLACEMENT.equals(observation.placementOutcome());
        analytics.update("""
                INSERT INTO song_difficulty_aggregates(song_id, rules_version, timeline_size_band, placement_attempts,
                  correct_placements, title_attempts, correct_titles, artist_attempts, correct_artists, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(song_id, rules_version, timeline_size_band) DO UPDATE SET
                  placement_attempts = song_difficulty_aggregates.placement_attempts + EXCLUDED.placement_attempts,
                  correct_placements = song_difficulty_aggregates.correct_placements + EXCLUDED.correct_placements,
                  title_attempts = song_difficulty_aggregates.title_attempts + EXCLUDED.title_attempts,
                  correct_titles = song_difficulty_aggregates.correct_titles + EXCLUDED.correct_titles,
                  artist_attempts = song_difficulty_aggregates.artist_attempts + EXCLUDED.artist_attempts,
                  correct_artists = song_difficulty_aggregates.correct_artists + EXCLUDED.correct_artists,
                  updated_at = CURRENT_TIMESTAMP
                """, observation.songId(), observation.rulesVersion(), observation.timelineCardCount() / TIMELINE_SIZE_BAND_WIDTH,
                attempted ? 1 : 0, SongResearchService.CORRECT.equals(observation.placementOutcome()) ? 1 : 0,
                observation.titleAttempted() ? 1 : 0, observation.titleCorrect() ? 1 : 0,
                observation.artistAttempts(), observation.correctArtists());
    }

    private void lockIdentity(UUID identity) {
        analytics.query("SELECT pg_advisory_xact_lock(hashtext(?))",
                (org.springframework.jdbc.core.RowCallbackHandler) result -> {}, identity.toString());
    }
}
