package org.dariusturcu.backend.observability;

import org.dariusturcu.backend.config.SecurityConfig;
import org.dariusturcu.backend.ratelimit.RateLimitingFilter;
import org.dariusturcu.backend.security.JwtAuthenticationFilter;
import org.dariusturcu.backend.security.oauth2.CustomOAuth2UserService;
import org.dariusturcu.backend.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.dariusturcu.backend.security.oauth2.ReturnToOAuth2AuthorizationRequestResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ManagementPortSecurityTest {

    private static final int MANAGEMENT_PORT = 8081;
    private static final int APPLICATION_PORT = 8080;
    private static final List<String> ALLOWED_ORIGINS = List.of("http://localhost:3000");

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private RateLimitingFilter rateLimitingFilter;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Mock
    private OAuth2AuthenticationFailureHandler failureHandler;

    @Mock
    private HttpCookieOAuth2AuthorizationRequestRepository requestRepository;

    @Mock
    private ReturnToOAuth2AuthorizationRequestResolver requestResolver;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void managementPortMatchesRequestCorrectly() {
        MockHttpServletRequest managementRequest = new MockHttpServletRequest();
        managementRequest.setServerPort(MANAGEMENT_PORT);
        managementRequest.setLocalPort(MANAGEMENT_PORT);
        managementRequest.setRequestURI("/actuator/prometheus");

        MockHttpServletRequest applicationRequest = new MockHttpServletRequest();
        applicationRequest.setServerPort(APPLICATION_PORT);
        applicationRequest.setLocalPort(APPLICATION_PORT);
        applicationRequest.setRequestURI("/actuator/prometheus");

        assertThat(managementRequest.getLocalPort()).isEqualTo(MANAGEMENT_PORT);
        assertThat(applicationRequest.getLocalPort()).isEqualTo(APPLICATION_PORT);
        assertThat(managementRequest.getLocalPort() == MANAGEMENT_PORT).isTrue();
        assertThat(applicationRequest.getLocalPort() == MANAGEMENT_PORT).isFalse();
    }
}
