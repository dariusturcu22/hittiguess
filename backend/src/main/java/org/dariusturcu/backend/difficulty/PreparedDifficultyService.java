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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PreparedDifficultyService {
    private static final String CALCULATION_VERSION = "global-v1";
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
        List<Long> selected = new ArrayList<>(selectIds(tier, samplingPivot, true, targetCount));
        if (selected.size() < targetCount) {
            selected.addAll(selectIds(tier, samplingPivot, false, targetCount - selected.size()));
        }
        if (selected.size() < targetCount) {
            throw new ConflictException("Not enough internationally known verified songs for a " + targetCount + "-card " + tier + " set");
        }
        Map<Long, Song> byId = songs.findAllById(selected).stream().collect(Collectors.toMap(Song::getId, Function.identity()));
        List<Song> pool = selected.stream().map(byId::get).collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(pool);
        return pool;
    }

    private List<Long> selectIds(DifficultyTier tier, double pivot, boolean afterPivot, int count) {
        String comparison = afterPivot ? ">=" : "<";
        return core.query("""
                SELECT difficulty.song_id FROM song_difficulty difficulty JOIN songs song ON song.id = difficulty.song_id
                WHERE difficulty.tier = ? AND difficulty.sampling_key %s ? AND song.verification_status = 'VERIFIED'
                  AND song.wikidata_sitelinks_count >= ?
                ORDER BY difficulty.sampling_key, difficulty.song_id LIMIT ?
                """.formatted(comparison), (result, rowNumber) -> result.getLong("song_id"), tier.name(), pivot,
                DifficultyTunedSongSelector.INTERNATIONAL_SCOPE_MINIMUM_SITELINKS, count);
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
