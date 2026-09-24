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
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
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
                new OAuth2AuthenticationSuccessHandler(
                        jwtUtil, cookieUtil, new HttpCookieOAuth2AuthorizationRequestRepository(), authService);
        ReflectionTestUtils.setField(handler, "redirectUri", REDIRECT_URI);
        return handler;
    }

    private MockHttpServletRequest requestWithSavedAuthorizationRequest(
            String returnTo, MockHttpServletResponse saveResponse) {
        OAuth2AuthorizationRequest.Builder requestBuilder = OAuth2AuthorizationRequest.authorizationCode()
                .clientId("google-client-id")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .redirectUri("http://localhost:8080/login/oauth2/code/google");
        if (returnTo != null) {
            requestBuilder.additionalParameters(Map.of("returnTo", returnTo));
        }
        new HttpCookieOAuth2AuthorizationRequestRepository().saveAuthorizationRequest(
                requestBuilder.build(), new MockHttpServletRequest(), saveResponse);
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setCookies(saveResponse.getCookies());
        return callbackRequest;
    }

    private UsernamePasswordAuthenticationToken googleAuthentication() {
        CustomOAuth2User principal = new CustomOAuth2User(googleUser(), Map.of("sub", "google-subject-1"));
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    private void stubTokenCookies() {
        when(jwtUtil.generateToken(org.mockito.ArgumentMatchers.any(User.class))).thenReturn("access-token");
        when(authService.createAndSaveRefreshToken(
                org.mockito.ArgumentMatchers.any(User.class), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn("refresh-token");
        when(jwtUtil.getExpirationSeconds()).thenReturn(900L);
        when(jwtUtil.getRefreshExpirationSeconds()).thenReturn(604800L);
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
    void successCarriesAReturnToDestinationThroughToTheFrontend() throws Exception {
        stubTokenCookies();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        MockHttpServletRequest callbackRequest =
                requestWithSavedAuthorizationRequest("/groups/join/abc123", saveResponse);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler().onAuthenticationSuccess(callbackRequest, response, googleAuthentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_URI + "?returnTo=/groups/join/abc123");
    }

    @Test
    void successDropsAnOffSiteReturnToDestination() throws Exception {
        stubTokenCookies();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        MockHttpServletRequest callbackRequest =
                requestWithSavedAuthorizationRequest("https://evil.example.com", saveResponse);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler().onAuthenticationSuccess(callbackRequest, response, googleAuthentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_URI);
    }

    @Test
    void successDropsBackslashAndProtocolRelativeReturnToDestinations() throws Exception {
        for (String unsafeReturnTo : List.of("/\\evil.example.com", "//evil.example.com", "/\t/evil.example.com")) {
            stubTokenCookies();
            MockHttpServletResponse saveResponse = new MockHttpServletResponse();
            MockHttpServletRequest callbackRequest = requestWithSavedAuthorizationRequest(unsafeReturnTo, saveResponse);
            MockHttpServletResponse response = new MockHttpServletResponse();

            successHandler().onAuthenticationSuccess(callbackRequest, response, googleAuthentication());

            assertThat(response.getRedirectedUrl()).isEqualTo(REDIRECT_URI);
        }
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
