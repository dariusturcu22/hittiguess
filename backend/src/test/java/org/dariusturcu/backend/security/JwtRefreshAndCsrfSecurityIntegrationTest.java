package org.dariusturcu.backend.security;

import org.dariusturcu.backend.controller.AuthController;
import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.AuthService;
import org.dariusturcu.backend.util.CookieUtil;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain (JWT authentication, CSRF, and the /auth/**
 * exemption) end to end, rather than mocking any of it out. Uses a minimal web security
 * context in place of the whole BackendApplication, for the same sandbox JDK reason
 * SongSearchIntegrationTest does: the real app's OAuth2 client beans can't build here.
 */
@Testcontainers
@SpringBootTest(classes = JwtRefreshAndCsrfSecurityIntegrationTest.WebTestConfig.class)
@AutoConfigureMockMvc
@Transactional
class JwtRefreshAndCsrfSecurityIntegrationTest {

    private static final String JWT_TEST_SECRET = "test-secret-key-at-least-32-bytes-long-for-hs256";
    private static final long ACCESS_TOKEN_EXPIRATION_MILLIS = 900_000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MILLIS = 604_800_000L;

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @EnableWebSecurity
    static class WebTestConfig {

        @Bean
        JwtUtil jwtUtil() {
            return new JwtUtil();
        }

        @Bean
        CookieUtil cookieUtil() {
            return new CookieUtil();
        }

        @Bean
        UserDetailsService userDetailsService(UserRepository userRepository) {
            return new CustomUserDetailsService(userRepository);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtUtil jwtUtil, CookieUtil cookieUtil, UserDetailsService userDetailsService) {
            return new JwtAuthenticationFilter(jwtUtil, cookieUtil, userDetailsService);
        }

        @Bean
        AuthService authService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                                 PasswordEncoder passwordEncoder, JwtUtil jwtUtil, AuthenticationManager authenticationManager) {
            return new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtUtil, authenticationManager);
        }

        @Bean
        AuthController authController(AuthService authService, CookieUtil cookieUtil, JwtUtil jwtUtil) {
            return new AuthController(authService, cookieUtil, jwtUtil);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @RestController
        static class ProtectedTestController {
            @GetMapping("/api/test/protected")
            public String read() {
                return "ok";
            }

            @PostMapping("/api/test/protected")
            public String write() {
                return "ok";
            }
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(12);
        }

        @Bean
        AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
            provider.setPasswordEncoder(passwordEncoder);
            return provider;
        }

        @Bean
        AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
            return configuration.getAuthenticationManager();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                                  AuthenticationProvider authenticationProvider) throws Exception {
            CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
            csrfHandler.setCsrfRequestAttributeName(null);

            return http
                    .csrf(csrf -> csrf
                            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                            .csrfTokenRequestHandler(csrfHandler)
                            .ignoringRequestMatchers("/auth/**"))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/auth/**").permitAll()
                            .anyRequest().authenticated())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .authenticationProvider(authenticationProvider)
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("jwt.secret", () -> JWT_TEST_SECRET);
        registry.add("jwt.expiration", () -> ACCESS_TOKEN_EXPIRATION_MILLIS);
        registry.add("jwt.refresh-expiration", () -> REFRESH_TOKEN_EXPIRATION_MILLIS);
        registry.add("app.env", () -> "test");
    }

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ACCOUNT_EMAIL = "security-test@example.com";
    private static final String ACCOUNT_PASSWORD = "password123";
    private static final Pattern SET_COOKIE_VALUE_PATTERN = Pattern.compile("^([^;]+)");

    @BeforeEach
    void createAccount() {
        User user = new User();
        user.setUsername("security-test-user");
        user.setEmail(ACCOUNT_EMAIL);
        user.setPassword(passwordEncoder.encode(ACCOUNT_PASSWORD));
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER);
        userRepository.save(user);
    }

    private String cookieValue(String setCookieHeader) {
        Matcher matcher = SET_COOKIE_VALUE_PATTERN.matcher(setCookieHeader);
        matcher.find();
        return matcher.group(1).split("=", 2)[1];
    }

    @Test
    void protectedEndpointRejectsARequestWithNoJwt() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsAMalformedJwt() throws Exception {
        mockMvc.perform(get("/api/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIssuesAJwtThatTheProtectedEndpointAccepts() throws Exception {
        var loginResult = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + ACCOUNT_EMAIL + "\",\"password\":\"" + ACCOUNT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String accessTokenCookie = loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith("access_token="))
                .findFirst()
                .orElseThrow();
        String accessToken = cookieValue(accessTokenCookie);

        mockMvc.perform(get("/api/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTheRefreshTokenSoTheOldOneCanOnlyBeUsedOnce() throws Exception {
        var loginResult = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + ACCOUNT_EMAIL + "\",\"password\":\"" + ACCOUNT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String refreshTokenCookieHeader = loginResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(header -> header.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow();
        String originalRefreshToken = cookieValue(refreshTokenCookieHeader);

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", originalRefreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", originalRefreshToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authEndpointsAreExemptFromCsrfEvenWithoutAToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + ACCOUNT_EMAIL + "\",\"password\":\"" + ACCOUNT_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointRejectsAStateChangingRequestWithNoCsrfToken() throws Exception {
        mockMvc.perform(post("/api/test/protected")
                        .with(SecurityMockMvcRequestPostProcessors.user("anyone")))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpointAcceptsAStateChangingRequestWithAMatchingCsrfToken() throws Exception {
        mockMvc.perform(post("/api/test/protected")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(SecurityMockMvcRequestPostProcessors.user("anyone")))
                .andExpect(status().isOk());
    }
}
