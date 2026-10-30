package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.group.AudioCheckStatus;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.session.GameSession;
import org.dariusturcu.backend.model.session.Player;
import org.dariusturcu.backend.model.session.PlayerStatus;

import java.util.Comparator;
import java.util.List;

// Whose audio share a running game depends on. A rotating game needs every remaining
// player who can share, since each of them DJs in turn; a fixed game needs only its DJ.
// A player who cannot share is never required: they only ever DJ when nobody else can.
public final class SessionAudioRules {

    private SessionAudioRules() {
    }

    public static List<Player> requiredSharers(GameSession session) {
        List<Player> remainingSharers = session.getPlayers().stream()
                .filter(player -> player.getStatus() == PlayerStatus.ACTIVE)
                .filter(Player::isCanDj)
                .sorted(Comparator.comparingInt(Player::getTurnOrder))
                .toList();
        if (session.getDjMode() == DjMode.FIXED) {
            return remainingSharers.stream()
                    .filter(player -> player.getId().equals(session.getFixedDjPlayerId()))
                    .toList();
        }
        return remainingSharers;
    }

    // Required players whose share is known to be missing or untested. A null status means
    // the player never shared in this session, which only happens for a player who was not
    // required when the game started, so it does not hold the game up.
    public static List<Player> blockingSharers(GameSession session) {
        return requiredSharers(session).stream()
                .filter(player -> player.getAudioStatus() != null && player.getAudioStatus() != AudioCheckStatus.OK)
                .toList();
    }
}
