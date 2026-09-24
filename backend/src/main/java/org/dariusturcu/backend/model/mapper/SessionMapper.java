package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.session.BetDTO;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.GameSessionDTO;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerCard;
import org.dariusturcu.backend.model.session.PlayerCardDTO;
import org.dariusturcu.backend.model.session.PlayerDTO;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundDTO;
import org.dariusturcu.backend.model.session.RoundStatus;
import org.dariusturcu.backend.model.session.RoundTiming;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.repository.BetRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SessionMapper {

    private final BetRepository betRepository;

    public PlayerCardDTO toCardDTO(PlayerCard card) {
        Song song = card.getSong();
        return new PlayerCardDTO(song.getId(), artistNames(song), song.getTitle(), card.getReleaseYear(), song.getColor(), card.getPosition());
    }

    public PlayerDTO toPlayerDTO(Player player) {
        return new PlayerDTO(
                player.getId(),
                player.getUser().getId(),
                player.getDisplayName(),
                player.getAvatarUrl(),
                player.getTurnOrder(),
                player.getTokenCount(),
                player.getStatus(),
                player.isConnected(),
                player.getTotalArtistsGuessed(),
                player.getTotalTitlesGuessed(),
                player.getTimeline().stream().map(this::toCardDTO).toList());
    }

    public RoundDTO toRoundDTO(Round round) {
        boolean isRevealedOrLater = round.getStatus() == RoundStatus.REVEALED || round.getStatus() == RoundStatus.SCORED;
        return new RoundDTO(
                round.getId(),
                round.getRoundNumber(),
                round.getActivePlayer().getId(),
                round.getDjPlayer().getId(),
                round.getStatus(),
                round.getPlacedPosition(),
                round.getPlacementCorrect(),
                // Queried directly rather than through round.getBets(): that association is
                // lazy, and a round handed to this mapper isn't always still attached to an
                // open persistence context by the time this runs.
                betRepository.findByRoundId(round.getId()).stream()
                        .map(bet -> new BetDTO(bet.getPlayer().getId(), bet.getPosition()))
                        .toList(),
                List.copyOf(round.getBettingSkippedPlayerIds()),
                isRevealedOrLater ? artistNames(round.getSong()) : null,
                isRevealedOrLater ? round.getSong().getTitle() : null,
                isRevealedOrLater ? round.getSong().getReleaseYear() : null,
                isRevealedOrLater ? round.getSong().getColor() : null,
                round.getStatus() == RoundStatus.AWAITING_PLACEMENT ? round.getPlacementEndsAt() : null,
                plusOrNull(round.getLockedInAt(), RoundTiming.LOCK_IN_COUNTDOWN),
                round.getBettingWindowEndsAt(),
                round.getStatus() == RoundStatus.SCORED ? plusOrNull(round.getScoredAt(), RoundTiming.REVEAL_HOLD) : null);
    }

    private static Instant plusOrNull(Instant start, Duration length) {
        return start != null ? start.plus(length) : null;
    }

    public String artistNames(Song song) {
        return song.getArtists().stream()
                .map(songArtist -> songArtist.getName())
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
    }

    public GameSessionDTO toSessionDTO(GameSession session, Round currentRound) {
        return new GameSessionDTO(
                session.getId(),
                session.getGroupId(),
                session.getStatus(),
                session.getDjMode(),
                session.getWinConditionCardCount(),
                session.getCurrentRoundNumber(),
                session.getPlayers().stream().map(this::toPlayerDTO).toList(),
                currentRound != null ? toRoundDTO(currentRound) : null);
    }
}
