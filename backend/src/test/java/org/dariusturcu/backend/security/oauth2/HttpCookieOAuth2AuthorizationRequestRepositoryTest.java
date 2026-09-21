package org.dariusturcu.backend.security.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private final HttpCookieOAuth2AuthorizationRequestRepository repository =
            new HttpCookieOAuth2AuthorizationRequestRepository();

    private OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .clientId("google-client-id")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .build();
    }

    @Test
    void savedRequestLoadsBackWithItsFields() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(), new MockHttpServletRequest(), saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(saveResponse.getCookies());

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getAuthorizationUri()).isEqualTo("https://accounts.google.com/o/oauth2/auth");
        assertThat(loaded.getClientId()).isEqualTo("google-client-id");
    }

    @Test
    void removeReturnsTheRequestAndClearsTheCookie() {
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(
                authorizationRequest(), new MockHttpServletRequest(), saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(saveResponse.getCookies());
        MockHttpServletResponse removeResponse = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(loadRequest, removeResponse);

        assertThat(removed).isNotNull();
        assertThat(removeResponse.getCookie("oauth2_auth_request").getMaxAge()).isZero();
    }

    @Test
    void missingCookieLoadsNull() {
        assertThat(repository.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
    }
}
