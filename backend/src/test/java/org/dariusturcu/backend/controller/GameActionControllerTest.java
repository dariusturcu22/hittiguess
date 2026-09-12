package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.session.PlaceCardRequest;
import org.dariusturcu.backend.model.session.TitleArtistGuessRequest;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GameSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameActionControllerTest {

    @Mock
    private GameSessionService gameSessionService;

    private static final Long SESSION_ID = 1L;
    private static final Long USER_ID = 10L;

    private GameActionController controller() {
        return new GameActionController(gameSessionService);
    }

    private Principal authenticatedPrincipal() {
        User user = new User();
        user.setId(USER_ID);
        user.setRole(Role.USER);
        return new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null);
    }

    @Test
    void placeCardResolvesTheUserIdFromThePrincipalAndDelegatesToTheService() {
        PlaceCardRequest request = new PlaceCardRequest(0);

        controller().placeCard(SESSION_ID, request, authenticatedPrincipal());

        verify(gameSessionService).lockInPlacement(SESSION_ID, USER_ID, request);
    }

    @Test
    void submitGuessResolvesTheUserIdFromThePrincipalAndDelegatesToTheService() {
        TitleArtistGuessRequest request = new TitleArtistGuessRequest("Title", "Artist");

        controller().submitGuess(SESSION_ID, request, authenticatedPrincipal());

        verify(gameSessionService).submitTitleArtistGuess(SESSION_ID, USER_ID, request);
    }

    @Test
    void placeBetDelegatesToTheServiceWithTheResolvedUserId() {
        controller().placeBet(SESSION_ID, authenticatedPrincipal());

        verify(gameSessionService).placeBet(SESSION_ID, USER_ID);
    }

    @Test
    void skipBettingDelegatesToTheServiceWithTheResolvedUserId() {
        controller().skipBetting(SESSION_ID, authenticatedPrincipal());

        verify(gameSessionService).skipBetting(SESSION_ID, USER_ID);
    }

    @Test
    void placeCardRejectsAStompSessionWithNoAuthenticatedPrincipal() {
        Principal unauthenticatedPrincipal = () -> "raw-session-id";

        assertThatThrownBy(() -> controller().placeCard(SESSION_ID, new PlaceCardRequest(0), unauthenticatedPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }
}
