package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.ratelimit.RateLimitingFilter;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.CustomUserDetailsService;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.AuthService;
import org.dariusturcu.backend.util.CookieUtil;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves /auth/register and /auth/login are rate-limited by IP independently of
 * whether each request succeeds, and that the limit holds under real concurrent
 * traffic rather than only sequential single-threaded calls. Uses a minimal
 * security context rather than the whole BackendApplication, for the same reason
 * SongSearchIntegrationTest does: this sandbox's JDK can't construct the
 * java.net.http.HttpClient the real app's OAuth2 client and AI-service beans build.
 */
@Testcontainers
@SpringBootTest(classes = AuthRateLimitIntegrationTest.WebTestConfig.class)
@AutoConfigureMockMvc
class AuthRateLimitIntegrationTest {

    private static final int AUTH_MAX_REQUESTS_PER_WINDOW = 5;
    private static final int CONCURRENT_LOGIN_ATTEMPTS = 20;
    private static final int CONCURRENCY_TEST_TIMEOUT_SECONDS = 10;

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @EnableWebSecurity
    @Import({JwtUtil.class, CookieUtil.class, CustomUserDetailsService.class, RateLimitingFilter.class})
    static class WebTestConfig {
        @Bean
        AuthService authService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                                 PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                                 AuthenticationManager authenticationManager) {
            return new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtUtil, authenticationManager);
        }

        @Bean
        AuthController authController(AuthService authService, CookieUtil cookieUtil, JwtUtil jwtUtil) {
            return new AuthController(authService, cookieUtil, jwtUtil);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                           PasswordEncoder passwordEncoder) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
            provider.setPasswordEncoder(passwordEncoder);
            return provider;
        }

        @Bean
        AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
            return configuration.getAuthenticationManager();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http, RateLimitingFilter rateLimitingFilter) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.requestMatchers("/auth/**").permitAll().anyRequest().authenticated())
                    .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
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
        registry.add("jwt.secret", () -> "integration-test-jwt-secret-integration-test-jwt-secret");
        registry.add("jwt.expiration", () -> "900000");
        registry.add("jwt.refresh-expiration", () -> "604800000");
        registry.add("app.env", () -> "test");
    }

    @BeforeAll
    static void migrate() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    private static final String RATE_LIMIT_KEY_HEADER = "X-Forwarded-For";

    @Autowired
    private MockMvc mockMvc;

    // The filter under test keys its bucket by client address; MockMvc requests all carry
    // the same loopback address by default, and the filter's buckets live for the whole
    // Spring context, cached across every test method in this class. Each test claims its
    // own fake address through X-Forwarded-For so its requests get an independent bucket,
    // rather than bleeding rate-limit state into whichever test happens to run next.
    private String uniqueRateLimitTestAddress() {
        return "test-client-" + System.nanoTime();
    }

    private String registerRequestBody(String username) {
        return """
                {"username":"%s","email":"%s@example.com","password":"password123"}
                """.formatted(username, username);
    }

    private String loginRequestBody(String email) {
        return """
                {"email":"%s","password":"wrong-password"}
                """.formatted(email);
    }

    // RegisterRequest.username caps at 20 characters, so test usernames use the low-order
    // digits of nanoTime rather than the full value.
    private String shortUniqueUsername(String label) {
        return label + (System.nanoTime() % 1_000_000L);
    }

    @Test
    void registrationRequestsUnderTheLimitAreNotRateLimited() throws Exception {
        String usernamePrefix = shortUniqueUsername("reg");
        String testAddress = uniqueRateLimitTestAddress();

        for (int requestNumber = 0; requestNumber < AUTH_MAX_REQUESTS_PER_WINDOW; requestNumber++) {
            mockMvc.perform(post("/auth/register")
                            .header(RATE_LIMIT_KEY_HEADER, testAddress)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerRequestBody(usernamePrefix + requestNumber)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void theRegistrationRequestOverTheLimitIsRejectedWithTheAppsStandardErrorShape() throws Exception {
        String usernamePrefix = shortUniqueUsername("regover");
        String testAddress = uniqueRateLimitTestAddress();

        for (int requestNumber = 0; requestNumber < AUTH_MAX_REQUESTS_PER_WINDOW; requestNumber++) {
            mockMvc.perform(post("/auth/register")
                    .header(RATE_LIMIT_KEY_HEADER, testAddress)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerRequestBody(usernamePrefix + "-" + requestNumber)));
        }

        mockMvc.perform(post("/auth/register")
                        .header(RATE_LIMIT_KEY_HEADER, testAddress)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestBody(usernamePrefix + "-overflow")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(HttpStatus.TOO_MANY_REQUESTS.value()))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void loginRequestsOverTheLimitAreRejectedRegardlessOfCredentialCorrectness() throws Exception {
        String email = "rate-limit-login-" + System.nanoTime() + "@example.com";
        String testAddress = uniqueRateLimitTestAddress();

        for (int requestNumber = 0; requestNumber < AUTH_MAX_REQUESTS_PER_WINDOW; requestNumber++) {
            mockMvc.perform(post("/auth/login")
                    .header(RATE_LIMIT_KEY_HEADER, testAddress)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginRequestBody(email)));
        }

        mockMvc.perform(post("/auth/login")
                        .header(RATE_LIMIT_KEY_HEADER, testAddress)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody(email)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void theLoginRateLimitHoldsUnderRealConcurrentTraffic() throws Exception {
        String email = "rate-limit-login-concurrent-" + System.nanoTime() + "@example.com";
        String testAddress = uniqueRateLimitTestAddress();
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_LOGIN_ATTEMPTS);

        List<Callable<Integer>> loginAttempts = IntStream.range(0, CONCURRENT_LOGIN_ATTEMPTS)
                .<Callable<Integer>>mapToObj(attemptNumber -> () -> mockMvc.perform(post("/auth/login")
                                .header(RATE_LIMIT_KEY_HEADER, testAddress)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequestBody(email)))
                        .andReturn().getResponse().getStatus())
                .collect(Collectors.toList());

        List<Future<Integer>> futures = executorService.invokeAll(loginAttempts, CONCURRENCY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executorService.shutdownNow();

        long rateLimitedCount = futures.stream()
                .map(this::resultOrRateLimited)
                .filter(status -> status == HttpStatus.TOO_MANY_REQUESTS.value())
                .count();
        long notRateLimitedCount = futures.size() - rateLimitedCount;

        assertThat(notRateLimitedCount).isEqualTo(AUTH_MAX_REQUESTS_PER_WINDOW);
        assertThat(rateLimitedCount).isEqualTo(CONCURRENT_LOGIN_ATTEMPTS - AUTH_MAX_REQUESTS_PER_WINDOW);
    }

    private int resultOrRateLimited(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
