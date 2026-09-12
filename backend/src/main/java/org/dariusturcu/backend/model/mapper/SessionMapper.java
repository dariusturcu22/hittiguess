package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.GameSessionDTO;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerCard;
import org.dariusturcu.backend.model.session.PlayerCardDTO;
import org.dariusturcu.backend.model.session.PlayerDTO;
import org.dariusturcu.backend.model.session.Round;
import org.dariusturcu.backend.model.session.RoundDTO;
import org.dariusturcu.backend.model.session.RoundStatus;

import org.springframework.stereotype.Component;

@Component
public class SessionMapper {

    public PlayerCardDTO toCardDTO(PlayerCard card) {
        return new PlayerCardDTO(card.getSong().getId(), card.getSong().getTitle(), card.getReleaseYear(), card.getPosition());
    }

    public PlayerDTO toPlayerDTO(Player player) {
        return new PlayerDTO(
                player.getId(),
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
                round.getBettorPlayer() != null ? round.getBettorPlayer().getId() : null,
                isRevealedOrLater ? artistNames(round) : null,
                isRevealedOrLater ? round.getSong().getTitle() : null,
                isRevealedOrLater ? round.getSong().getReleaseYear() : null);
    }

    private String artistNames(Round round) {
        return round.getSong().getArtists().stream()
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
