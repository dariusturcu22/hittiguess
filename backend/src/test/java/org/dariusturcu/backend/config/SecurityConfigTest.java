package org.dariusturcu.backend.config;

import org.dariusturcu.backend.ratelimit.RateLimitingFilter;
import org.dariusturcu.backend.security.JwtAuthenticationFilter;
import org.dariusturcu.backend.security.oauth2.CustomOAuth2UserService;
import org.dariusturcu.backend.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    private static final String LOCAL_FRONTEND_ORIGIN = "http://localhost:3000";
    private static final String SECOND_FRONTEND_ORIGIN = "http://localhost:3001";

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private RateLimitingFilter rateLimitingFilter;

    @Mock
    private org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    @Mock
    private CustomOAuth2UserService customOAuth2UserService;

    @Mock
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Mock
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Mock
    private HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void corsConfigurationUsesConfiguredFrontendOrigins() {
        SecurityConfig securityConfig = securityConfigWithOrigins(List.of(SECOND_FRONTEND_ORIGIN));

        CorsConfiguration configuration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/groups"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(SECOND_FRONTEND_ORIGIN);
    }

    @Test
    void multipleFrontendOriginsRemainAllowed() {
        SecurityConfig securityConfig = securityConfigWithOrigins(
                List.of(LOCAL_FRONTEND_ORIGIN, SECOND_FRONTEND_ORIGIN));

        CorsConfiguration configuration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/groups"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly(LOCAL_FRONTEND_ORIGIN, SECOND_FRONTEND_ORIGIN);
    }

    private SecurityConfig securityConfigWithOrigins(List<String> allowedOrigins) {
        return new SecurityConfig(
                jwtAuthenticationFilter,
                rateLimitingFilter,
                userDetailsService,
                customOAuth2UserService,
                oAuth2AuthenticationSuccessHandler,
                oAuth2AuthenticationFailureHandler,
                authorizationRequestRepository,
                objectMapper,
                allowedOrigins);
    }
}
