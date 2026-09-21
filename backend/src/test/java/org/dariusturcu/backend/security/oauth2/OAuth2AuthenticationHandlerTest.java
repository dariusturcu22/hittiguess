package org.dariusturcu.backend.security.oauth2;

import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.AuthService;
import org.dariusturcu.backend.util.CookieUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationHandlerTest {

    private static final String REDIRECT_URI = "http://localhost:3000/oauth2/redirect";

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthService authService;

    private User googleUser() {
        User user = new User();
        user.setId(7L);
        user.setUsername("Google Player");
        user.setEmail("google.player@example.com");
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        return user;
    }

    private OAuth2AuthenticationSuccessHandler successHandler() {
        CookieUtil cookieUtil = new CookieUtil();
        ReflectionTestUtils.setField(cookieUtil, "appEnv", "dev");
        OAuth2AuthenticationSuccessHandler handler =
                new OAuth2AuthenticationSuccessHandler(jwtUtil, cookieUtil, authService);
        ReflectionTestUtils.setField(handler, "redirectUri", REDIRECT_URI);
        return handler;
    }

    @Test
    void successSetsTokenCookiesAndRedirectsToTheFrontend() throws Exception {
        User user = googleUser();
        when(jwtUtil.generateToken(user)).thenReturn("access-token");
        when(authService.createAndSaveRefreshToken(user, false)).thenReturn("refresh-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(900L);
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(604800L);
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "google-subject-1"));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler().onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_URI);
        Collection<String> setCookieHeaders = response.getHeaders("Set-Cookie");
        assertThat(setCookieHeaders).hasSize(3);
        assertThat(String.join(";", setCookieHeaders)).contains("refresh_token=refresh-token");
    }

    @Test
    void failureRedirectsWithAnErrorFlag() throws Exception {
        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler();
        ReflectionTestUtils.setField(handler, "redirectUri", REDIRECT_URI);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                new MockHttpServletRequest(), response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_URI + "?error=oauth2_failed");
    }
}
