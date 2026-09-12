package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.session.PlaceCardRequest;
import org.dariusturcu.backend.model.session.TitleArtistGuessRequest;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

// The client-to-server action channel for a game session, the STOMP counterpart to
// GroupController's REST actions. Mirrors SessionDestinations' /app/sessions/{sessionId}/
// destinations exactly: place, guess, bet, skip-betting.
@Controller
@RequiredArgsConstructor
public class GameActionController {

    private final GameSessionService gameSessionService;

    @MessageMapping("/sessions/{sessionId}/place")
    public void placeCard(@DestinationVariable Long sessionId, @Payload PlaceCardRequest request, Principal principal) {
        gameSessionService.lockInPlacement(sessionId, resolveUserId(principal), request);
    }

    @MessageMapping("/sessions/{sessionId}/guess")
    public void submitGuess(@DestinationVariable Long sessionId, @Payload TitleArtistGuessRequest request, Principal principal) {
        gameSessionService.submitTitleArtistGuess(sessionId, resolveUserId(principal), request);
    }

    @MessageMapping("/sessions/{sessionId}/bet")
    public void placeBet(@DestinationVariable Long sessionId, Principal principal) {
        gameSessionService.placeBet(sessionId, resolveUserId(principal));
    }

    @MessageMapping("/sessions/{sessionId}/skip-betting")
    public void skipBetting(@DestinationVariable Long sessionId, Principal principal) {
        gameSessionService.skipBetting(sessionId, resolveUserId(principal));
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUser().getId();
        }
        throw new AccessDeniedException("No authenticated user on this STOMP session");
    }
}
