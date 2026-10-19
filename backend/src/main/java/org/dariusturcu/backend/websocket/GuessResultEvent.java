package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.session.GuessResultDTO;

// Published by GameSessionService for each guess, delivered only to the guesser's own
// user queue by SessionBroadcastListener. username is the guesser's STOMP principal name.
public record GuessResultEvent(String username, Long sessionId, GuessResultDTO result) {
}
