package org.dariusturcu.backend.config;

import jakarta.servlet.http.Cookie;
import org.dariusturcu.backend.ratelimit.RateLimitingFilter;
import org.dariusturcu.backend.security.JwtAuthenticationFilter;
import org.dariusturcu.backend.security.oauth2.CustomOAuth2UserService;
import org.dariusturcu.backend.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.dariusturcu.backend.util.CookieUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the CSRF posture against the real SecurityConfig: state-changing API
 * calls need the cookie/header token pair, public reads need nothing, and the
 * /auth/** paths that must work pre-login skip CSRF entirely.
 */
@WebMvcTest(
        controllers = CsrfSecurityTest.ProbeController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@Import({CsrfSecurityTest.SecurityTestConfig.class, SecurityConfig.class})
class CsrfSecurityTest {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @RestController
    static class ProbeController {
        @GetMapping("/api/enums/probe")
        String publicGet() {
            return "ok";
        }

        @PostMapping("/api/probe")
        String apiPost() {
            return "ok";
        }

        @PostMapping("/auth/probe")
        String authPost() {
            return "ok";
        }
    }

    @Configuration
    @EnableWebSecurity
    static class SecurityTestConfig {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(
                    mock(org.dariusturcu.backend.security.util.JwtUtil.class),
                    new CookieUtil(),
                    mock(UserDetailsService.class));
        }

        @Bean
        RateLimitingFilter rateLimitingFilter() {
            return new RateLimitingFilter(new ObjectMapper());
        }

        @Bean
        SecurityConfig securityConfig(
                JwtAuthenticationFilter jwtAuthenticationFilter,
                RateLimitingFilter rateLimitingFilter,
                UserDetailsService userDetailsService,
                CustomOAuth2UserService customOAuth2UserService,
                OAuth2AuthenticationSuccessHandler successHandler,
                OAuth2AuthenticationFailureHandler failureHandler,
                HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
                ObjectMapper objectMapper) {
            return new SecurityConfig(
                    jwtAuthenticationFilter,
                    rateLimitingFilter,
                    userDetailsService,
                    customOAuth2UserService,
                    successHandler,
                    failureHandler,
                    authorizationRequestRepository,
                    objectMapper,
                    List.of("http://localhost:3000"));
        }

        @Bean
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }

        @Bean
        CustomOAuth2UserService customOAuth2UserService() {
            return mock(CustomOAuth2UserService.class);
        }

        @Bean
        OAuth2AuthenticationSuccessHandler successHandler() {
            return mock(OAuth2AuthenticationSuccessHandler.class);
        }

        @Bean
        OAuth2AuthenticationFailureHandler failureHandler() {
            return mock(OAuth2AuthenticationFailureHandler.class);
        }

        @Bean
        HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
            return mock(HttpCookieOAuth2AuthorizationRequestRepository.class);
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return mock(ClientRegistrationRepository.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private static String freshCsrfToken() {
        CsrfToken token = CookieCsrfTokenRepository.withHttpOnlyFalse()
                .generateToken(new MockHttpServletRequest());
        return token.getToken();
    }

    @Test
    void publicGetNeedsNoCsrfToken() throws Exception {
        mockMvc.perform(get("/api/enums/probe"))
                .andExpect(status().isOk());
    }

    @Test
    void apiPostWithoutTokenIsRejectedAsCsrf() throws Exception {
        mockMvc.perform(post("/api/probe"))
                .andExpect(status().isForbidden())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("CSRF"));
    }

    @Test
    void apiPostWithTokenPairPassesCsrfToAuthentication() throws Exception {
        String csrfToken = freshCsrfToken();

        mockMvc.perform(post("/api/probe")
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("CSRF"));
    }

    @Test
    void ignoredAuthPathSkipsCsrfEntirely() throws Exception {
        mockMvc.perform(post("/auth/probe"))
                .andExpect(status().isOk());
    }
}
