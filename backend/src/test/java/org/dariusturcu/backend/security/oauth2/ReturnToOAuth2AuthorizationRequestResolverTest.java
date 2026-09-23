package org.dariusturcu.backend.security.oauth2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnToOAuth2AuthorizationRequestResolverTest {

    private static final String GOOGLE_REGISTRATION_ID = "google";

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    private ReturnToOAuth2AuthorizationRequestResolver resolver() {
        ClientRegistration registration = ClientRegistration.withRegistrationId(GOOGLE_REGISTRATION_ID)
                .clientId("google-client-id")
                .clientSecret("google-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        when(clientRegistrationRepository.findByRegistrationId(GOOGLE_REGISTRATION_ID)).thenReturn(registration);
        return new ReturnToOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
    }

    private MockHttpServletRequest authorizationEndpointRequest(String returnTo) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/oauth2/authorization/" + GOOGLE_REGISTRATION_ID);
        if (returnTo != null) {
            request.setParameter("returnTo", returnTo);
        }
        return request;
    }

    @Test
    void inviteReturnToSurvivesIntoTheSavedAuthorizationRequest() {
        OAuth2AuthorizationRequest resolved =
                resolver().resolve(authorizationEndpointRequest("/groups/join/abc123"), GOOGLE_REGISTRATION_ID);

        assertThat(resolved).isNotNull();
        assertThat(resolved.getAdditionalParameters()).containsEntry("returnTo", "/groups/join/abc123");
    }

    @Test
    void offSiteReturnToIsDroppedFromTheSavedAuthorizationRequest() {
        OAuth2AuthorizationRequest resolved = resolver().resolve(
                authorizationEndpointRequest("https://evil.example.com"), GOOGLE_REGISTRATION_ID);

        assertThat(resolved).isNotNull();
        assertThat(resolved.getAdditionalParameters()).doesNotContainKey("returnTo");
    }

    @Test
    void missingReturnToLeavesTheSavedAuthorizationRequestUntouched() {
        OAuth2AuthorizationRequest resolved =
                resolver().resolve(authorizationEndpointRequest(null), GOOGLE_REGISTRATION_ID);

        assertThat(resolved).isNotNull();
        assertThat(resolved.getAdditionalParameters()).doesNotContainKey("returnTo");
    }
}
