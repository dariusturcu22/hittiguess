package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.auth.AuthResult;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.AuthService;
import org.dariusturcu.backend.util.CookieUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private CookieUtil cookieUtil;
    @Mock
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, cookieUtil, jwtUtil))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        ResponseCookie placeholderCookie = ResponseCookie.from("placeholder", "value").build();
        org.mockito.Mockito.lenient().when(cookieUtil.createAccessTokenCookie(any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(placeholderCookie);
        org.mockito.Mockito.lenient().when(cookieUtil.createRefreshTokenCookie(any(), org.mockito.ArgumentMatchers.anyLong())).thenReturn(placeholderCookie);
    }

    private AuthResult sampleAuthResult() {
        return new AuthResult("access-token", "refresh-token", 1L, "someuser", "someone@example.com");
    }

    @Test
    void registerReturnsTheNewAccountAndSetsAuthCookies() throws Exception {
        when(authService.register(any())).thenReturn(sampleAuthResult());

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"username":"someuser","email":"someone@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("someuser"))
                .andExpect(jsonPath("$.email").value("someone@example.com"));
    }

    @Test
    void registerRejectsAnInvalidPayloadBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"username":"a","email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerReturnsConflictWhenTheUsernameOrEmailIsAlreadyTaken() throws Exception {
        when(authService.register(any())).thenThrow(new ConflictException("Username or email already in use"));

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"username":"someuser","email":"someone@example.com","password":"password123"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void loginReturnsTheSameGenericFailureForAnUnknownEmailAsForAWrongPassword() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"unknown@example.com","password":"whatever"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginReturnsTheAuthenticatedAccountOnSuccess() throws Exception {
        when(authService.login(any())).thenReturn(sampleAuthResult());

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"someone@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("someuser"));
    }

    @Test
    void refreshRejectsARequestWithNoRefreshTokenCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshRotatesTheTokenPairWhenAValidRefreshTokenCookieIsPresent() throws Exception {
        when(cookieUtil.extractFromCookies(any(), org.mockito.ArgumentMatchers.eq("refresh_token")))
                .thenReturn(java.util.Optional.of("old-refresh-token"));
        when(authService.refreshTokens("old-refresh-token")).thenReturn(sampleAuthResult());

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("someuser"));
    }

    @Test
    void logoutSucceedsEvenWhenNoRefreshTokenCookieIsPresent() throws Exception {
        when(cookieUtil.deleteCookie(any(), any())).thenReturn(ResponseCookie.from("placeholder", "").build());

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk());
    }
}
