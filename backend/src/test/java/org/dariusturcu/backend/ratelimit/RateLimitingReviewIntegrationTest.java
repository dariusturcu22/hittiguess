package org.dariusturcu.backend.ratelimit;

import org.dariusturcu.backend.config.SecurityConfig;
import org.dariusturcu.backend.security.JwtAuthenticationFilter;
import org.dariusturcu.backend.security.oauth2.CustomOAuth2UserService;
import org.dariusturcu.backend.security.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.dariusturcu.backend.security.oauth2.OAuth2AuthenticationSuccessHandler;
import org.dariusturcu.backend.security.oauth2.ReturnToOAuth2AuthorizationRequestResolver;
import org.dariusturcu.backend.util.CookieUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RateLimitingReviewIntegrationTest.ProbeController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@Import({RateLimitingReviewIntegrationTest.SecurityTestConfig.class, SecurityConfig.class})
class RateLimitingReviewIntegrationTest {

    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final int LOGIN_LIMIT_THRESHOLD = 10;
    private static final int REGISTER_LIMIT_THRESHOLD = 5;
    private static final int EMAIL_SENDING_LIMIT_THRESHOLD = 3;
    private static final int TOKEN_SUBMISSION_LIMIT_THRESHOLD = 10;
    private static final int SONG_AND_IMPORT_LIMIT_THRESHOLD = 10;
    private static final int REPORT_LIMIT_THRESHOLD = 5;
    private static final int AVATAR_LIMIT_THRESHOLD = 5;
    private static final int MANAGEMENT_PORT = 8081;

    @RestController
    static class ProbeController {
        @PostMapping({
                "/auth/login",
                "/auth/register",
                "/auth/resend-verification",
                "/auth/password-reset/request",
                "/auth/password-reset/confirm",
                "/auth/verify-email",
                "/auth/2fa/verify"
        })
        String authEndpoints() {
            return "ok";
        }

        @PostMapping({
                "/api/playlists/{playlistId}/songs",
                "/api/playlists/{playlistId}/imports",
                "/api/playlists/{playlistId}/import-jobs",
                "/api/bulk-import",
                "/api/bulk-import/expand"
        })
        String songAndImportEndpoints(@PathVariable(required = false) Long playlistId) {
            return "ok";
        }

        @GetMapping("/api/metadata/song")
        String metadataLookupEndpoint() {
            return "ok";
        }

        @PostMapping({
                "/api/songs/{songId}/reports",
                "/api/songs/{songId}/confirmations"
        })
        String reportEndpoints(@PathVariable Long songId) {
            return "ok";
        }

        @PutMapping("/api/users/me/avatar")
        String avatarUploadEndpoint() {
            return "ok";
        }

        @GetMapping("/api/general/probe")
        String generalEndpoint() {
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
        RateLimitingFilter rateLimitingFilter(ObjectMapper objectMapper) {
            return new RateLimitingFilter(objectMapper);
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
                ReturnToOAuth2AuthorizationRequestResolver returnToAuthorizationRequestResolver,
                ObjectMapper objectMapper) {
            return new SecurityConfig(
                    jwtAuthenticationFilter,
                    rateLimitingFilter,
                    userDetailsService,
                    customOAuth2UserService,
                    successHandler,
                    failureHandler,
                    authorizationRequestRepository,
                    returnToAuthorizationRequestResolver,
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

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        RateLimitTestSupport.reset(rateLimitingFilter);
    }

    private static String freshCsrfToken() {
        CsrfToken token = CookieCsrfTokenRepository.withHttpOnlyFalse()
                .generateToken(new MockHttpServletRequest());
        return token.getToken();
    }

    @ParameterizedTest
    @CsvSource({
            "/auth/login," + LOGIN_LIMIT_THRESHOLD,
            "/auth/register," + REGISTER_LIMIT_THRESHOLD,
            "/auth/resend-verification," + EMAIL_SENDING_LIMIT_THRESHOLD,
            "/auth/password-reset/request," + EMAIL_SENDING_LIMIT_THRESHOLD,
            "/auth/password-reset/confirm," + TOKEN_SUBMISSION_LIMIT_THRESHOLD,
            "/auth/verify-email," + TOKEN_SUBMISSION_LIMIT_THRESHOLD,
            "/auth/2fa/verify," + TOKEN_SUBMISSION_LIMIT_THRESHOLD
    })
    void authenticationEndpointsReturn429AfterTheirOwnThresholdAndRecover(String endpointPath, int threshold) throws Exception {
        String clientAddress = "198.51.100.10";

        for (int requestIndex = 0; requestIndex < threshold; requestIndex++) {
            postFromAddress(endpointPath, clientAddress).andExpect(status().isOk());
        }

        postFromAddress(endpointPath, clientAddress)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(HttpStatus.TOO_MANY_REQUESTS.value()))
                .andExpect(jsonPath("$.message").value("Too many requests, try again later"))
                .andExpect(jsonPath("$.timestamp").exists());

        RateLimitTestSupport.reset(rateLimitingFilter);

        postFromAddress(endpointPath, clientAddress).andExpect(status().isOk());
    }

    @Test
    void exhaustingOneAuthenticationEndpointDoesNotBlockTheOthersForTheSameAddress() throws Exception {
        String sharedNetworkAddress = "198.51.100.11";

        for (int requestIndex = 0; requestIndex < EMAIL_SENDING_LIMIT_THRESHOLD; requestIndex++) {
            postFromAddress("/auth/password-reset/request", sharedNetworkAddress).andExpect(status().isOk());
        }
        postFromAddress("/auth/password-reset/request", sharedNetworkAddress)
                .andExpect(status().isTooManyRequests());

        postFromAddress("/auth/login", sharedNetworkAddress).andExpect(status().isOk());
        postFromAddress("/auth/register", sharedNetworkAddress).andExpect(status().isOk());
        postFromAddress("/auth/resend-verification", sharedNetworkAddress).andExpect(status().isOk());
    }

    @Test
    void percentEncodedAuthenticationPathIsLimitedLikeThePlainPath() throws Exception {
        String clientAddress = "198.51.100.12";

        for (int requestIndex = 0; requestIndex < LOGIN_LIMIT_THRESHOLD; requestIndex++) {
            postFromAddress("/auth/%6Cogin", clientAddress).andExpect(status().isOk());
        }

        postFromAddress("/auth/login", clientAddress).andExpect(status().isTooManyRequests());
        postFromAddress("/auth/%6Cogin", clientAddress).andExpect(status().isTooManyRequests());
    }

    @Test
    void percentEncodedReportPathIsLimitedLikeThePlainPath() throws Exception {
        String clientAddress = "198.51.100.13";
        String csrfToken = freshCsrfToken();

        for (int requestIndex = 0; requestIndex < REPORT_LIMIT_THRESHOLD; requestIndex++) {
            mockMvc.perform(post(java.net.URI.create("/api/songs/42/%72eports"))
                            .with(fromAddress(clientAddress))
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/songs/42/reports")
                        .with(fromAddress(clientAddress))
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void ipv6AddressesInsideOneSlash64ShareABucket() throws Exception {
        for (int requestIndex = 0; requestIndex < LOGIN_LIMIT_THRESHOLD; requestIndex++) {
            postFromAddress("/auth/login", "2001:db8:1:2::" + Integer.toHexString(requestIndex + 1))
                    .andExpect(status().isOk());
        }

        postFromAddress("/auth/login", "2001:db8:1:2:ffff:ffff:ffff:ffff")
                .andExpect(status().isTooManyRequests());
        postFromAddress("/auth/login", "2001:db8:1:3::1").andExpect(status().isOk());
    }

    private ResultActions postFromAddress(String path, String clientAddress) throws Exception {
        return mockMvc.perform(post(java.net.URI.create(path))
                .with(fromAddress(clientAddress))
                .contentType(MediaType.APPLICATION_JSON));
    }

    private static RequestPostProcessor fromAddress(String clientAddress) {
        return request -> {
            request.setRemoteAddr(clientAddress);
            return request;
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/playlists/42/songs",
            "/api/playlists/42/imports",
            "/api/playlists/42/import-jobs",
            "/api/bulk-import",
            "/api/bulk-import/expand"
    })
    void songAndImportEndpointsReturn429AfterThresholdAndRecover(String endpointPath) throws Exception {
        String clientAddress = "198.51.100.20";
        String csrfToken = freshCsrfToken();

        for (int requestIndex = 0; requestIndex < SONG_AND_IMPORT_LIMIT_THRESHOLD; requestIndex++) {
            mockMvc.perform(post(endpointPath)
                            .with(request -> {
                                request.setRemoteAddr(clientAddress);
                                return request;
                            })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(endpointPath)
                        .with(request -> {
                            request.setRemoteAddr(clientAddress);
                            return request;
                        })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(HttpStatus.TOO_MANY_REQUESTS.value()));

        RateLimitTestSupport.reset(rateLimitingFilter);

        mockMvc.perform(post(endpointPath)
                        .with(request -> {
                            request.setRemoteAddr(clientAddress);
                            return request;
                        })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/songs/42/reports",
            "/api/songs/42/confirmations"
    })
    void reportEndpointsReturn429AfterThresholdAndRecover(String endpointPath) throws Exception {
        String clientAddress = "198.51.100.30";
        String csrfToken = freshCsrfToken();

        for (int requestIndex = 0; requestIndex < REPORT_LIMIT_THRESHOLD; requestIndex++) {
            mockMvc.perform(post(endpointPath)
                            .with(request -> {
                                request.setRemoteAddr(clientAddress);
                                return request;
                            })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(endpointPath)
                        .with(request -> {
                            request.setRemoteAddr(clientAddress);
                            return request;
                        })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());

        RateLimitTestSupport.reset(rateLimitingFilter);

        mockMvc.perform(post(endpointPath)
                        .with(request -> {
                            request.setRemoteAddr(clientAddress);
                            return request;
                        })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metadataLookupGetReturns429AfterThresholdAndRecover() throws Exception {
        String clientAddress = "198.51.100.25";

        for (int requestIndex = 0; requestIndex < SONG_AND_IMPORT_LIMIT_THRESHOLD; requestIndex++) {
            mockMvc.perform(get("/api/metadata/song").with(fromAddress(clientAddress)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/metadata/song").with(fromAddress(clientAddress)))
                .andExpect(status().isTooManyRequests());

        RateLimitTestSupport.reset(rateLimitingFilter);

        mockMvc.perform(get("/api/metadata/song").with(fromAddress(clientAddress)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avatarUploadEndpointReturns429AfterThresholdAndRecover() throws Exception {
        String clientAddress = "198.51.100.40";
        String csrfToken = freshCsrfToken();

        for (int requestIndex = 0; requestIndex < AVATAR_LIMIT_THRESHOLD; requestIndex++) {
            mockMvc.perform(put("/api/users/me/avatar")
                            .with(request -> {
                                request.setRemoteAddr(clientAddress);
                                return request;
                            })
                            .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                            .header(CSRF_HEADER_NAME, csrfToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(put("/api/users/me/avatar")
                        .with(request -> {
                            request.setRemoteAddr(clientAddress);
                            return request;
                        })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());

        RateLimitTestSupport.reset(rateLimitingFilter);

        mockMvc.perform(put("/api/users/me/avatar")
                        .with(request -> {
                            request.setRemoteAddr(clientAddress);
                            return request;
                        })
                        .cookie(new Cookie(CSRF_COOKIE_NAME, csrfToken))
                        .header(CSRF_HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void attackerSpoofingForwardedHeaderCannotBypassRateLimitOnSameSocket() throws Exception {
        String directSocketAddress = "203.0.113.50";

        for (int requestIndex = 0; requestIndex < LOGIN_LIMIT_THRESHOLD; requestIndex++) {
            mockMvc.perform(post("/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(directSocketAddress);
                                return request;
                            })
                            .header("X-Forwarded-For", "spoofed-ip-" + requestIndex)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(directSocketAddress);
                            return request;
                        })
                        .header("X-Forwarded-For", "spoofed-ip-overflow")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void distinctClientAddressesHaveIndependentRateLimiterBuckets() throws Exception {
        String clientAddressOne = "203.0.113.61";
        String clientAddressTwo = "203.0.113.62";

        for (int requestIndex = 0; requestIndex < LOGIN_LIMIT_THRESHOLD; requestIndex++) {
            mockMvc.perform(post("/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(clientAddressOne);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(clientAddressOne);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(clientAddressTwo);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
