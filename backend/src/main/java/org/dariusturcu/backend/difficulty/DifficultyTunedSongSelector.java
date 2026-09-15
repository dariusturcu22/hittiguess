package org.dariusturcu.backend.difficulty;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.RoundRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// Selects songs for a Difficulty-Based session: given the group's players, a target tier,
// and how many cards the session needs, it scores the verified catalog for those players,
// keeps the songs whose group-level score falls in the target tier's band, and returns them.
// Selection reuses the existing Song and Round repositories rather than a difficulty-specific
// store, since the play-derived signal is computed on the fly from scored rounds and no
// difficulty column exists on Song.
//
// The Wikidata sitelinks popularity signal is left empty on every song here: the column that
// would carry it does not exist yet, and capturing it through the metadata pipeline is
// deferred to story 23. Once it lands, the international-scope filter and the sitelinks
// weighting attach at this seam without changing the selection flow. See docs/DECISIONS.md.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DifficultyTunedSongSelector {

    private final SongRepository songRepository;
    private final RoundRepository roundRepository;
    private final SongDifficultyScorer songDifficultyScorer;
    private final DifficultyBand difficultyBand;
    private final GroupDifficultyStrategy groupDifficultyStrategy;
    private final PersonalizedDifficultyPredictor personalizedDifficultyPredictor;

    private static final VerificationStatus SELECTABLE_STATUS = VerificationStatus.VERIFIED;
    private static final RoundStatus SCORED_STATUS = RoundStatus.SCORED;
    private static final Optional<Integer> SITELINKS_NOT_YET_CAPTURED = Optional.empty();
    private static final int MINIMUM_TARGET_CARD_COUNT = 1;

    public List<ScoredSong> selectForGroup(
            List<Long> playerIds,
            DifficultyTier tier,
            int targetCardCount) {
        if (playerIds == null || playerIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Difficulty selection needs at least one player to score against");
        }
        if (targetCardCount < MINIMUM_TARGET_CARD_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Target card count must be at least " + MINIMUM_TARGET_CARD_COUNT);
        }

        List<Song> verifiedCatalog = songRepository.findByVerificationStatus(SELECTABLE_STATUS);
        if (verifiedCatalog.isEmpty()) {
            return List.of();
        }

        Map<Long, SongPlacementStats> placementStatsBySong = loadPlacementStats(verifiedCatalog);

        return verifiedCatalog.stream()
                .map(song -> scoreSongForGroup(song, playerIds, tier, placementStatsBySong))
                .filter(scoredSong -> scoredSong.tier() == tier)
                .sorted(orderWithinTier(tier))
                .limit(targetCardCount)
                .toList();
    }

    private Map<Long, SongPlacementStats> loadPlacementStats(List<Song> verifiedCatalog) {
        List<Long> songIds = verifiedCatalog.stream().map(Song::getId).toList();
        return roundRepository.aggregatePlacementStatsBySong(SCORED_STATUS, songIds).stream()
                .collect(Collectors.toMap(SongPlacementStats::songId, Function.identity()));
    }

    // The group reduction strategy is chosen by the requested tier (easy protects the weakest
    // player, hard averages, medium takes the median). The tier a song ends up filed under is
    // the band its reduced group score falls in, which is what selection filters on.
    private ScoredSong scoreSongForGroup(
            Song song,
            List<Long> playerIds,
            DifficultyTier requestedTier,
            Map<Long, SongPlacementStats> placementStatsBySong) {
        SongPlacementStats placementStats = placementStatsBySong.get(song.getId());
        SongDifficultySignals signals = new SongDifficultySignals(
                song.getId(), placementStats, SITELINKS_NOT_YET_CAPTURED);
        double baselineDifficultyScore = songDifficultyScorer.score(signals);

        List<Double> perPlayerScores = playerIds.stream()
                .map(playerId -> personalizedDifficultyPredictor.predictForPlayer(
                        playerId, song.getId(), baselineDifficultyScore))
                .toList();

        double groupScore = groupDifficultyStrategy.groupScore(requestedTier, perPlayerScores);
        return new ScoredSong(song, groupScore, difficultyBand.tierForScore(groupScore));
    }

    // Easy leads with the lowest-difficulty songs first, hard leads with the highest, medium
    // orders from the low end of its band upward.
    private Comparator<ScoredSong> orderWithinTier(DifficultyTier tier) {
        Comparator<ScoredSong> byAscendingDifficulty = Comparator.comparingDouble(ScoredSong::groupDifficultyScore);
        if (tier == DifficultyTier.HARD) {
            return byAscendingDifficulty.reversed();
        }
        return byAscendingDifficulty;
    }
}
