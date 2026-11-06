package org.dariusturcu.backend.config;

import org.dariusturcu.backend.ratelimit.RateLimitingFilter;
import org.dariusturcu.backend.security.JwtAuthenticationFilter;
import org.dariusturcu.backend.security.oauth2.CustomOAuth2UserService;
import org.dariusturcu.backend.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.dariusturcu.backend.security.oauth2.ReturnToOAuth2AuthorizationRequestResolver;
import org.dariusturcu.backend.util.CookieUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CorsIntegrationTest.ProbeController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@Import({CorsIntegrationTest.SecurityTestConfig.class, SecurityConfig.class})
@TestPropertySource(properties = {
        "frontend.allowed-origins=http://localhost:3000,https://hittiguess.com"
})
class CorsIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";
    private static final String SECOND_ALLOWED_ORIGIN = "https://hittiguess.com";
    private static final String FOREIGN_ORIGIN = "https://foreign-attacker.com";

    @RestController
    static class ProbeController {
        @GetMapping({"/health", "/api/enums/probe"})
        String publicProbe() {
            return "ok";
        }

        @PostMapping("/api/probe")
        String probePost() {
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
        ReturnToOAuth2AuthorizationRequestResolver returnToAuthorizationRequestResolver() {
            return mock(ReturnToOAuth2AuthorizationRequestResolver.class);
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

    @Test
    void preflightWithAllowedOriginSucceedsAndListsExactAllowedAttributes() throws Exception {
        mockMvc.perform(options("/api/probe")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, X-XSRF-TOKEN, X-Request-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andExpect(result -> {
                    String allowedMethodsHeader = result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
                    assertThat(allowedMethodsHeader).isNotNull();
                    assertThat(allowedMethodsHeader).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

                    String allowedHeadersHeader = result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
                    assertThat(allowedHeadersHeader).isNotNull();
                    assertThat(allowedHeadersHeader).doesNotContain("*");
                });
    }

    @Test
    void preflightWithSecondAllowedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/probe")
                        .header(HttpHeaders.ORIGIN, SECOND_ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, SECOND_ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void preflightWithForeignOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/probe")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void simpleRequestWithAllowedOriginIncludesCorsHeaders() throws Exception {
        mockMvc.perform(get("/health")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void simpleRequestWithForeignOriginIsRejectedWithoutCorsHeaders() throws Exception {
        mockMvc.perform(get("/health")
                        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflightWithDisallowedHeaderIsRejected() throws Exception {
        mockMvc.perform(options("/api/probe")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Unapproved-Header"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
