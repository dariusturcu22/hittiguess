package org.dariusturcu.backend.difficulty;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.history.GameHistoryService;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PreparedDifficultyService {
    private static final String CALCULATION_VERSION = "global-v1";
    private static final String REQUESTED_TIER_PREDICATE = "difficulty.tier = ?";
    private static final String OTHER_TIER_PREDICATE = "difficulty.tier <> ?";
    private static final String AFTER_PIVOT_COMPARISON = ">=";
    private static final String BEFORE_PIVOT_COMPARISON = "<";
    private final JdbcTemplate core;
    private final JdbcTemplate analytics;
    private final SongRepository songs;
    private final SongDifficultyScorer scorer;
    private final DifficultyBand bands;

    public PreparedDifficultyService(@Qualifier("coreJdbcTemplate") JdbcTemplate core,
            @Qualifier("analyticsJdbcTemplate") JdbcTemplate analytics, SongRepository songs,
            SongDifficultyScorer scorer, DifficultyBand bands) {
        this.core = core;
        this.analytics = analytics;
        this.songs = songs;
        this.scorer = scorer;
        this.bands = bands;
    }

    @Transactional(readOnly = true)
    public List<Song> select(DifficultyTier tier, int targetCount) {
        double samplingPivot = ThreadLocalRandom.current().nextDouble();
        List<Long> selected = new ArrayList<>();
        appendPreparedBand(selected, REQUESTED_TIER_PREDICATE, tier, samplingPivot, targetCount);
        appendPreparedBand(selected, OTHER_TIER_PREDICATE, tier, samplingPivot, targetCount);
        appendUnpreparedInternationalSongs(selected, targetCount);
        if (selected.size() < targetCount) {
            throw new ConflictException("Not enough internationally known verified songs: " + selected.size()
                    + " available, " + targetCount + " needed for a " + targetCount + "-card " + tier + " set");
        }
        Map<Long, Song> byId = songs.findAllById(selected).stream().collect(Collectors.toMap(Song::getId, Function.identity()));
        return selected.stream().map(byId::get).collect(Collectors.toCollection(ArrayList::new));
    }

    // Fills the target in widening order: the requested tier first so it dominates when its band
    // is large enough, then the other prepared tiers, then verified songs in international scope
    // that have no prepared row. The steps draw from disjoint predicates, so no song is selected
    // twice, and each step keeps the pivot's two halves so a short catalog is spread across it.
    private void appendPreparedBand(List<Long> selected, String tierPredicate, DifficultyTier tier, double pivot,
            int targetCount) {
        int remaining = targetCount - selected.size();
        if (remaining <= 0) {
            return;
        }
        selected.addAll(selectPreparedBand(tierPredicate, AFTER_PIVOT_COMPARISON, tier, pivot, remaining));
        remaining = targetCount - selected.size();
        if (remaining <= 0) {
            return;
        }
        selected.addAll(selectPreparedBand(tierPredicate, BEFORE_PIVOT_COMPARISON, tier, pivot, remaining));
    }

    private void appendUnpreparedInternationalSongs(List<Long> selected, int targetCount) {
        int remaining = targetCount - selected.size();
        if (remaining <= 0) {
            return;
        }
        selected.addAll(core.query("""
                SELECT song.id FROM songs song
                WHERE song.verification_status = 'VERIFIED' AND song.wikidata_sitelinks_count >= ?
                  AND NOT EXISTS (SELECT 1 FROM song_difficulty difficulty WHERE difficulty.song_id = song.id)
                ORDER BY song.id LIMIT ?
                """, (result, rowNumber) -> result.getLong("id"),
                DifficultyTunedSongSelector.INTERNATIONAL_SCOPE_MINIMUM_SITELINKS, remaining));
    }

    private List<Long> selectPreparedBand(String tierPredicate, String pivotComparison, DifficultyTier tier, double pivot,
            int count) {
        return core.query("""
                SELECT difficulty.song_id FROM song_difficulty difficulty JOIN songs song ON song.id = difficulty.song_id
                WHERE %s AND difficulty.sampling_key %s ? AND song.verification_status = 'VERIFIED'
                  AND song.wikidata_sitelinks_count >= ?
                ORDER BY difficulty.sampling_key, difficulty.song_id LIMIT ?
                """.formatted(tierPredicate, pivotComparison), (result, rowNumber) -> result.getLong("song_id"),
                tier.name(), pivot, DifficultyTunedSongSelector.INTERNATIONAL_SCOPE_MINIMUM_SITELINKS, count);
    }

    @Transactional
    public int refresh() {
        Map<Long, SongPlacementStats> statistics = analytics.query("""
                SELECT song_id, sum(placement_attempts) AS attempts, sum(correct_placements) AS correct
                FROM song_difficulty_aggregates WHERE rules_version = ? GROUP BY song_id
                """, (result, rowNumber) -> new SongPlacementStats(result.getLong("song_id"),
                result.getLong("attempts"), result.getLong("correct")), GameHistoryService.RULES_VERSION).stream()
                .collect(Collectors.toMap(SongPlacementStats::songId, Function.identity()));
        List<Song> catalog = songs.findByVerificationStatus(org.dariusturcu.backend.model.song.VerificationStatus.VERIFIED);
        for (Song song : catalog) {
            SongPlacementStats stats = statistics.get(song.getId());
            double score = scorer.score(new SongDifficultySignals(song.getId(), stats, Optional.ofNullable(song.getWikidataSitelinksCount())));
            core.update("""
                    INSERT INTO song_difficulty(song_id, score, tier, placement_sample_count, calculation_version, calculated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) ON CONFLICT(song_id) DO UPDATE SET
                    score = EXCLUDED.score, tier = EXCLUDED.tier, placement_sample_count = EXCLUDED.placement_sample_count,
                    calculation_version = EXCLUDED.calculation_version, calculated_at = EXCLUDED.calculated_at
                    """, song.getId(), score, bands.tierForScore(score).name(), stats == null ? 0 : stats.scoredRoundCount(), CALCULATION_VERSION);
        }
        return catalog.size();
    }
}
