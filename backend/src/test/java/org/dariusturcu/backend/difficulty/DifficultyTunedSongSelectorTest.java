package org.dariusturcu.backend.difficulty;

import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.RoundRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DifficultyTunedSongSelectorTest {

    @Mock
    private SongRepository songRepository;
    @Mock
    private RoundRepository roundRepository;

    private final SongDifficultyScorer songDifficultyScorer = new SongDifficultyScorer();
    private final DifficultyBand difficultyBand = new DifficultyBand();
    private final GroupDifficultyStrategy groupDifficultyStrategy = new GroupDifficultyStrategy();
    private final PersonalizedDifficultyPredictor personalizedDifficultyPredictor = new AggregateBaselinePredictor();

    private DifficultyTunedSongSelector difficultyTunedSongSelector;

    private static final Long EASY_SONG_ID = 10L;
    private static final Long HARD_SONG_ID = 20L;
    private static final Long SOLO_PLAYER_ID = 1L;
    private static final List<Long> ONE_PLAYER = List.of(SOLO_PLAYER_ID);
    private static final int GENEROUS_CARD_COUNT = 50;
    private static final long FULLY_TRUSTED_ROUNDS = SongDifficultyScorer.PLAY_HISTORY_FULL_TRUST_ROUND_COUNT;

    @BeforeEach
    void setUp() {
        difficultyTunedSongSelector = new DifficultyTunedSongSelector(
                songRepository,
                roundRepository,
                songDifficultyScorer,
                difficultyBand,
                groupDifficultyStrategy,
                personalizedDifficultyPredictor);
    }

    private Song songWithId(Long id) {
        Song song = new Song();
        song.setId(id);
        song.setVerificationStatus(VerificationStatus.VERIFIED);
        return song;
    }

    @Test
    void anEmptyCatalogReturnsNoSongsRatherThanFailing() {
        when(songRepository.findByVerificationStatus(VerificationStatus.VERIFIED)).thenReturn(List.of());

        List<ScoredSong> selected =
                difficultyTunedSongSelector.selectForGroup(ONE_PLAYER, DifficultyTier.EASY, GENEROUS_CARD_COUNT);

        assertThat(selected).isEmpty();
    }

    @Test
    void selectionReturnsOnlySongsInTheTargetTiersBand() {
        Song easySong = songWithId(EASY_SONG_ID);
        Song hardSong = songWithId(HARD_SONG_ID);
        when(songRepository.findByVerificationStatus(VerificationStatus.VERIFIED))
                .thenReturn(List.of(easySong, hardSong));

        SongPlacementStats alwaysCorrect =
                new SongPlacementStats(EASY_SONG_ID, FULLY_TRUSTED_ROUNDS, FULLY_TRUSTED_ROUNDS);
        long neverCorrect = 0L;
        SongPlacementStats alwaysWrong =
                new SongPlacementStats(HARD_SONG_ID, FULLY_TRUSTED_ROUNDS, neverCorrect);
        when(roundRepository.aggregatePlacementStatsBySong(any(RoundStatus.class), any()))
                .thenReturn(List.of(alwaysCorrect, alwaysWrong));

        List<ScoredSong> easyPick =
                difficultyTunedSongSelector.selectForGroup(ONE_PLAYER, DifficultyTier.EASY, GENEROUS_CARD_COUNT);
        assertThat(easyPick).extracting(scored -> scored.song().getId()).containsExactly(EASY_SONG_ID);

        List<ScoredSong> hardPick =
                difficultyTunedSongSelector.selectForGroup(ONE_PLAYER, DifficultyTier.HARD, GENEROUS_CARD_COUNT);
        assertThat(hardPick).extracting(scored -> scored.song().getId()).containsExactly(HARD_SONG_ID);
    }

    @Test
    void aTargetTierWithNoMatchingSongsReturnsEmpty() {
        Song hardSong = songWithId(HARD_SONG_ID);
        when(songRepository.findByVerificationStatus(VerificationStatus.VERIFIED)).thenReturn(List.of(hardSong));

        long neverCorrect = 0L;
        SongPlacementStats alwaysWrong =
                new SongPlacementStats(HARD_SONG_ID, FULLY_TRUSTED_ROUNDS, neverCorrect);
        when(roundRepository.aggregatePlacementStatsBySong(any(RoundStatus.class), any()))
                .thenReturn(List.of(alwaysWrong));

        List<ScoredSong> easyPick =
                difficultyTunedSongSelector.selectForGroup(ONE_PLAYER, DifficultyTier.EASY, GENEROUS_CARD_COUNT);

        assertThat(easyPick).isEmpty();
    }

    @Test
    void aSongWithNoRecordedGuessesStillGetsANeutralPlacementFromNoSignal() {
        Song neverPlayed = songWithId(EASY_SONG_ID);
        when(songRepository.findByVerificationStatus(VerificationStatus.VERIFIED)).thenReturn(List.of(neverPlayed));
        when(roundRepository.aggregatePlacementStatsBySong(any(RoundStatus.class), any()))
                .thenReturn(List.of());

        List<ScoredSong> mediumPick =
                difficultyTunedSongSelector.selectForGroup(ONE_PLAYER, DifficultyTier.MEDIUM, GENEROUS_CARD_COUNT);

        assertThat(mediumPick).extracting(scored -> scored.song().getId()).containsExactly(EASY_SONG_ID);
    }

    @Test
    void theResultIsCappedAtTheTargetCardCount() {
        Song firstEasySong = songWithId(EASY_SONG_ID);
        Song secondEasySong = songWithId(EASY_SONG_ID + 1);
        when(songRepository.findByVerificationStatus(VerificationStatus.VERIFIED))
                .thenReturn(List.of(firstEasySong, secondEasySong));

        SongPlacementStats firstAlwaysCorrect =
                new SongPlacementStats(EASY_SONG_ID, FULLY_TRUSTED_ROUNDS, FULLY_TRUSTED_ROUNDS);
        SongPlacementStats secondAlwaysCorrect =
                new SongPlacementStats(EASY_SONG_ID + 1, FULLY_TRUSTED_ROUNDS, FULLY_TRUSTED_ROUNDS);
        when(roundRepository.aggregatePlacementStatsBySong(any(RoundStatus.class), any()))
                .thenReturn(List.of(firstAlwaysCorrect, secondAlwaysCorrect));

        int singleCard = 1;
        List<ScoredSong> capped =
                difficultyTunedSongSelector.selectForGroup(ONE_PLAYER, DifficultyTier.EASY, singleCard);

        assertThat(capped).hasSize(singleCard);
    }
}
