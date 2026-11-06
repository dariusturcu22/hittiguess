package org.dariusturcu.backend.observability;

import org.dariusturcu.backend.config.SecurityConfig;
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
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ManagementPortSecurityTest.ProbeController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@Import({ManagementPortSecurityTest.SecurityTestConfig.class, SecurityConfig.class})
class ManagementPortSecurityTest {

    private static final int MANAGEMENT_PORT = 8081;
    private static final int APPLICATION_PORT = 8080;
    private static final String METRICS_PATH = "/actuator/prometheus";

    @RestController
    static class ProbeController {
        @GetMapping(METRICS_PATH)
        String metrics() {
            return "metrics";
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
        RateLimitingFilter rateLimitingFilter(ObjectMapper objectMapper) {
            return new RateLimitingFilter(objectMapper);
        }

        @Bean
        SecurityConfig securityConfig(
                JwtAuthenticationFilter jwtAuthenticationFilter,
                RateLimitingFilter rateLimitingFilter,
                ObjectMapper objectMapper) {
            return new SecurityConfig(
                    jwtAuthenticationFilter,
                    rateLimitingFilter,
                    mock(UserDetailsService.class),
                    mock(CustomOAuth2UserService.class),
                    mock(OAuth2AuthenticationSuccessHandler.class),
                    mock(OAuth2AuthenticationFailureHandler.class),
                    mock(HttpCookieOAuth2AuthorizationRequestRepository.class),
                    mock(ReturnToOAuth2AuthorizationRequestResolver.class),
                    objectMapper,
                    List.of("http://localhost:3000"),
                    MANAGEMENT_PORT);
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

    private static RequestPostProcessor arrivingOnPort(int localPort) {
        return request -> {
            request.setLocalPort(localPort);
            return request;
        };
    }

    @Test
    void metricsRequestOnTheApplicationPortIsUnauthorized() throws Exception {
        mockMvc.perform(get(METRICS_PATH).with(arrivingOnPort(APPLICATION_PORT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metricsRequestOnTheManagementPortIsAllowed() throws Exception {
        mockMvc.perform(get(METRICS_PATH).with(arrivingOnPort(MANAGEMENT_PORT)))
                .andExpect(status().isOk());
    }
}
