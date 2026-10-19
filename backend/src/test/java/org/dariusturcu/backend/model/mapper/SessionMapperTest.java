package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerCard;
import org.dariusturcu.backend.model.session.PlayerCardDTO;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundDTO;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.session.RoundTiming;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.repository.BetRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionMapperTest {

    private static final Instant LOCKED_IN_AT = Instant.parse("2026-09-23T20:00:00Z");
    private static final Instant BETTING_WINDOW_ENDS_AT = Instant.parse("2026-09-23T20:00:19Z");
    private static final Instant SCORED_AT = Instant.parse("2026-09-23T20:00:20Z");

    private final BetRepository betRepository = mock(BetRepository.class);
    private final SessionMapper sessionMapper = new SessionMapper(betRepository);

    @Test
    void timelineCardCarriesTheArtistCreditAndTheSongColor() {
        Song song = song("Levitating", 2020, "74c7ec", "Dua Lipa", "DaBaby");
        PlayerCard card = PlayerCard.of(song);

        PlayerCardDTO cardDTO = sessionMapper.toCardDTO(card);

        assertThat(cardDTO.artist()).isEqualTo("Dua Lipa, DaBaby");
        assertThat(cardDTO.title()).isEqualTo("Levitating");
        assertThat(cardDTO.releaseYear()).isEqualTo(2020);
        assertThat(cardDTO.color()).isEqualTo("74c7ec");
    }

    @Test
    void scoredRoundPublishesEveryDeadlineAndTheRevealedColor() {
        Round round = round(song("Flowers", 2023, "f38ba8", "Miley Cyrus"));
        round.setStatus(RoundStatus.SCORED);
        round.setLockedInAt(LOCKED_IN_AT);
        round.setBettingWindowEndsAt(BETTING_WINDOW_ENDS_AT);
        round.setScoredAt(SCORED_AT);

        RoundDTO roundDTO = sessionMapper.toRoundDTO(round);

        assertThat(roundDTO.countdownEndsAt()).isEqualTo(LOCKED_IN_AT.plus(RoundTiming.LOCK_IN_COUNTDOWN));
        assertThat(roundDTO.bettingWindowEndsAt()).isEqualTo(BETTING_WINDOW_ENDS_AT);
        assertThat(roundDTO.nextRoundStartsAt()).isEqualTo(SCORED_AT.plus(RoundTiming.REVEAL_HOLD));
        assertThat(roundDTO.revealedColor()).isEqualTo("f38ba8");
    }

    @Test
    void unrevealedRoundHidesTheSongAndHasNoNextRoundDeadline() {
        Round round = round(song("Flowers", 2023, "f38ba8", "Miley Cyrus"));
        round.setStatus(RoundStatus.AWAITING_PLACEMENT);

        RoundDTO roundDTO = sessionMapper.toRoundDTO(round);

        assertThat(roundDTO.revealedTitle()).isNull();
        assertThat(roundDTO.revealedColor()).isNull();
        assertThat(roundDTO.countdownEndsAt()).isNull();
        assertThat(roundDTO.nextRoundStartsAt()).isNull();
    }

    private Round round(Song song) {
        when(betRepository.findByRoundId(anyLong())).thenReturn(List.of());
        GameSession session = new GameSession();
        Player activePlayer = new Player();
        activePlayer.setId(1L);
        Player djPlayer = new Player();
        djPlayer.setId(2L);
        Round round = new Round();
        round.setId(10L);
        round.setSession(session);
        round.setRoundNumber(1);
        round.setActivePlayer(activePlayer);
        round.setDjPlayer(djPlayer);
        round.setSong(song);
        return round;
    }

    private Song song(String title, int releaseYear, String color, String... artistNames) {
        Song song = new Song();
        song.setId(100L);
        song.setTitle(title);
        song.setReleaseYear(releaseYear);
        song.setColor(color);
        for (int displayOrder = 0; displayOrder < artistNames.length; displayOrder++) {
            SongArtist songArtist = new SongArtist();
            songArtist.setName(artistNames[displayOrder]);
            songArtist.setRole(displayOrder == 0 ? ArtistRole.MAIN : ArtistRole.FEATURED);
            songArtist.setDisplayOrder(displayOrder);
            songArtist.setSong(song);
            song.getArtists().add(songArtist);
        }
        return song;
    }
}
