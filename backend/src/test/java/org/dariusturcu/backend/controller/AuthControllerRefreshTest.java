package org.dariusturcu.backend.controller;

import jakarta.servlet.http.Cookie;
import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.AuthService;
import org.dariusturcu.backend.service.TwoFactorService;
import org.dariusturcu.backend.util.CookieUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the refresh endpoint through a standalone MockMvc with the real exception handler, so the
 * status codes a visitor without a usable session receives are checked without a database.
 */
class AuthControllerRefreshTest {

    private static final String REFRESH_PATH = "/auth/refresh";
    private static final String UNKNOWN_TOKEN = "unknown-token";
    private static final String INVALID_TOKEN_MESSAGE = "Invalid refresh token";

    private final AuthService authService = mock(AuthService.class);
    private final CookieUtil cookieUtil = mock(CookieUtil.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService, mock(TwoFactorService.class), cookieUtil, mock(JwtUtil.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void aVisitorWithoutARefreshTokenReceivesUnauthorizedInsteadOfAServerError() throws Exception {
        when(cookieUtil.extractFromCookies(any(), eq(AuthController.REFRESH_TOKEN_COOKIE_NAME)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(REFRESH_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(HttpStatus.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.message").value(AuthController.NO_REFRESH_TOKEN_MESSAGE));

        verify(authService, never()).refreshTokens(any());
    }

    @Test
    void anUnknownRefreshTokenStillReceivesUnauthorized() throws Exception {
        when(cookieUtil.extractFromCookies(any(), eq(AuthController.REFRESH_TOKEN_COOKIE_NAME)))
                .thenReturn(Optional.of(UNKNOWN_TOKEN));
        when(authService.refreshTokens(UNKNOWN_TOKEN))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN_MESSAGE));

        mockMvc.perform(post(REFRESH_PATH).cookie(new Cookie(AuthController.REFRESH_TOKEN_COOKIE_NAME, UNKNOWN_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_TOKEN_MESSAGE));
    }
}
