package org.dariusturcu.backend.controller;

import com.jayway.jsonpath.JsonPath;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.dariusturcu.backend.exception.GlobalExceptionHandler;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.PasswordResetTokenRepository;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.TwoFactorBackupCodeRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.CustomUserDetailsService;
import org.dariusturcu.backend.security.JwtAuthenticationFilter;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.AuthService;
import org.dariusturcu.backend.service.EmailService;
import org.dariusturcu.backend.service.LoginAttemptService;
import org.dariusturcu.backend.service.TwoFactorService;
import org.dariusturcu.backend.util.CookieUtil;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.servlet.http.Cookie;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// End-to-end coverage over real HTTP for the flows AuthServiceTest and TwoFactorServiceTest
// already prove at the unit level: registration through email verification, the full
// password-reset round trip, and the two-step 2FA login. Uses the same minimal security
// context AuthRateLimitIntegrationTest does, extended with JwtAuthenticationFilter since
// the 2FA setup/confirm endpoints require a real authenticated principal.
@Testcontainers
@SpringBootTest(classes = AuthHardeningFlowIntegrationTest.WebTestConfig.class)
@AutoConfigureMockMvc
class AuthHardeningFlowIntegrationTest {

    private static final Pattern TOKEN_QUERY_PARAM_PATTERN = Pattern.compile("token=([^&\\s\"<]+)");
    private static final long TOTP_PERIOD_SECONDS = 30;
    private static final int FAILED_ATTEMPTS_BEFORE_LOCKOUT = 5;

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @EnableWebSecurity
    @Import({JwtUtil.class, CookieUtil.class, CustomUserDetailsService.class, JwtAuthenticationFilter.class,
            GlobalExceptionHandler.class})
    static class WebTestConfig {
        @Bean
        EmailService emailService() {
            return mock(EmailService.class);
        }

        @Bean
        TwoFactorService twoFactorService(UserRepository userRepository,
                                           TwoFactorBackupCodeRepository backupCodeRepository,
                                           PasswordEncoder passwordEncoder) {
            return new TwoFactorService(userRepository, backupCodeRepository, passwordEncoder);
        }

        @Bean
        LoginAttemptService loginAttemptService(UserRepository userRepository) {
            return new LoginAttemptService(userRepository);
        }

        @Bean
        AuthService authService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                                 EmailVerificationTokenRepository emailVerificationTokenRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository,
                                 PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                                 AuthenticationManager authenticationManager,
                                 EmailService emailService, TwoFactorService twoFactorService,
                                 LoginAttemptService loginAttemptService) {
            return new AuthService(userRepository, refreshTokenRepository, emailVerificationTokenRepository,
                    passwordResetTokenRepository, passwordEncoder, jwtUtil, authenticationManager,
                    emailService, twoFactorService, loginAttemptService);
        }

        @Bean
        AuthController authController(AuthService authService, TwoFactorService twoFactorService,
                                       CookieUtil cookieUtil, JwtUtil jwtUtil) {
            return new AuthController(authService, twoFactorService, cookieUtil, jwtUtil);
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
        SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
                throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/auth/2fa/setup", "/auth/2fa/confirm", "/auth/2fa/disable").authenticated()
                            .requestMatchers("/auth/**").permitAll()
                            .anyRequest().authenticated())
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
        registry.add("jwt.secret", () -> "integration-test-jwt-secret-integration-test-jwt-secret");
        registry.add("jwt.expiration", () -> "900000");
        registry.add("jwt.refresh-expiration", () -> "604800000");
        registry.add("jwt.two-factor-pending-expiration", () -> "300000");
        registry.add("email.verification-token-expiration", () -> "86400000");
        registry.add("email.password-reset-token-expiration", () -> "3600000");
        registry.add("frontend.url", () -> "http://localhost:3000");
        registry.add("app.env", () -> "test");
    }

    @BeforeAll
    static void migrate() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailService emailService;

    private String lastRegisteredEmail;

    // emailService is a singleton mock shared across every test method through the cached
    // Spring context; resetting it between tests keeps one test's sent emails from being
    // counted against another test's never()/verify() assertions.
    @AfterEach
    void resetEmailServiceMock() {
        Mockito.reset(emailService);
    }

    private String uniqueEmail(String label) {
        return label + "-" + System.nanoTime() + "@example.com";
    }

    private String registerRequestBody(String username, String email) {
        return """
                {"username":"%s","email":"%s","password":"password123"}
                """.formatted(username, email);
    }

    private String loginRequestBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String extractToken(String link) {
        Matcher matcher = TOKEN_QUERY_PARAM_PATTERN.matcher(link);
        assertThat(matcher.find()).as("link should contain a token query param: %s", link).isTrue();
        return matcher.group(1);
    }

    @Test
    void loginIsBlockedUntilTheAccountIsVerifiedThenSucceedsAfterVerification() throws Exception {
        String email = uniqueEmail("verify-flow");
        String username = "verifyflow" + (System.nanoTime() % 1_000_000L);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestBody(username, email)))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("access_token"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody(email, "password123")))
                .andExpect(status().isForbidden());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), linkCaptor.capture());
        String rawToken = extractToken(linkCaptor.getValue());

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(rawToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody(email, "password123")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"));
    }

    @Test
    void anExpiredOrUnknownVerificationTokenIsRejected() throws Exception {
        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"not-a-real-token\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void passwordResetRoundTripAllowsLoginWithTheNewPasswordAndRejectsTokenReuse() throws Exception {
        String email = uniqueEmail("reset-flow");
        String username = "resetflow" + (System.nanoTime() % 1_000_000L);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestBody(username, email)));
        ArgumentCaptor<String> verificationLinkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), verificationLinkCaptor.capture());
        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(extractToken(verificationLinkCaptor.getValue()))));

        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        ArgumentCaptor<String> resetLinkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(email), resetLinkCaptor.capture());
        String rawResetToken = extractToken(resetLinkCaptor.getValue());

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"new-password456\"}".formatted(rawResetToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody(email, "new-password456")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"another-password789\"}".formatted(rawResetToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void aPasswordResetRequestForANonexistentEmailStillReturnsSuccess() throws Exception {
        mockMvc.perform(post("/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + System.nanoTime() + "@example.com\"}"))
                .andExpect(status().isOk());

        verify(emailService, org.mockito.Mockito.never()).sendPasswordResetEmail(anyString(), anyString());
    }

    // A code for the step after the current one: still inside the accepted drift window,
    // and later than any step this test already used, so replay protection accepts it.
    private String nextStepCode(String secret) throws Exception {
        return new DefaultCodeGenerator().generate(secret, new SystemTimeProvider().getTime() / TOTP_PERIOD_SECONDS + 1);
    }

    private String currentStepCode(String secret) throws Exception {
        return new DefaultCodeGenerator().generate(secret, new SystemTimeProvider().getTime() / TOTP_PERIOD_SECONDS);
    }

    private Cookie registerVerifyAndLogIn(String label) throws Exception {
        String email = uniqueEmail(label);
        String username = label.replace("-", "") + (System.nanoTime() % 1_000_000L);
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerRequestBody(username, email)));
        ArgumentCaptor<String> verificationLinkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq(email), verificationLinkCaptor.capture());
        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(extractToken(verificationLinkCaptor.getValue()))));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody(email, "password123")))
                .andExpect(status().isOk())
                .andReturn();
        Cookie accessTokenCookie = loginResult.getResponse().getCookie("access_token");
        assertThat(accessTokenCookie).isNotNull();
        lastRegisteredEmail = email;
        return accessTokenCookie;
    }

    // Turns two-factor on for the signed-in account and returns its TOTP secret.
    private String enableTwoFactor(Cookie accessTokenCookie) throws Exception {
        MvcResult setupResult = mockMvc.perform(post("/auth/2fa/setup").cookie(accessTokenCookie))
                .andExpect(status().isOk())
                .andReturn();
        String secret = JsonPath.read(setupResult.getResponse().getContentAsString(), "$.secret");
        mockMvc.perform(post("/auth/2fa/confirm")
                        .cookie(accessTokenCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(currentStepCode(secret))))
                .andExpect(status().isOk());
        return secret;
    }

    private String loginForPendingToken(String email) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody(email, "password123")))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist("access_token"))
                .andReturn();
        return JsonPath.read(loginResult.getResponse().getContentAsString(), "$.pendingToken");
    }

    private String verifyRequestBody(String pendingToken, String code) {
        return "{\"pendingToken\":\"%s\",\"code\":\"%s\"}".formatted(pendingToken, code);
    }

    @Test
    void theTwoStepLoginRequiresACorrectSecondFactorBeforeIssuingRealTokens() throws Exception {
        Cookie accessTokenCookie = registerVerifyAndLogIn("two-factor-flow");
        String secret = enableTwoFactor(accessTokenCookie);
        String pendingToken = loginForPendingToken(lastRegisteredEmail);

        mockMvc.perform(post("/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyRequestBody(pendingToken, "000000")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyRequestBody(pendingToken, nextStepCode(secret))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("access_token"));
    }

    @Test
    void thePendingTokenIsRefusedAsAnAccessToken() throws Exception {
        Cookie accessTokenCookie = registerVerifyAndLogIn("pending-token");
        enableTwoFactor(accessTokenCookie);
        String pendingToken = loginForPendingToken(lastRegisteredEmail);

        mockMvc.perform(post("/auth/2fa/setup").cookie(new Cookie("access_token", pendingToken)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/auth/2fa/setup").header("Authorization", "Bearer " + pendingToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void setupIsRefusedWhileTwoFactorIsOnSoItCannotTurnItOff() throws Exception {
        Cookie accessTokenCookie = registerVerifyAndLogIn("setup-while-on");
        enableTwoFactor(accessTokenCookie);

        mockMvc.perform(post("/auth/2fa/setup").cookie(accessTokenCookie))
                .andExpect(status().isConflict());

        loginForPendingToken(lastRegisteredEmail);
    }

    @Test
    void aTwoFactorCodeCannotBeReplayed() throws Exception {
        Cookie accessTokenCookie = registerVerifyAndLogIn("replay");
        String secret = enableTwoFactor(accessTokenCookie);
        String code = nextStepCode(secret);

        mockMvc.perform(post("/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyRequestBody(loginForPendingToken(lastRegisteredEmail), code)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyRequestBody(loginForPendingToken(lastRegisteredEmail), code)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedWrongPasswordsLockTheAccountEvenForTheRightPassword() throws Exception {
        registerVerifyAndLogIn("lockout");
        String email = lastRegisteredEmail;

        for (int attempt = 0; attempt < FAILED_ATTEMPTS_BEFORE_LOCKOUT; attempt++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequestBody(email, "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody(email, "password123")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void repeatedWrongTwoFactorCodesLockTheAccount() throws Exception {
        Cookie accessTokenCookie = registerVerifyAndLogIn("code-lockout");
        String secret = enableTwoFactor(accessTokenCookie);
        String pendingToken = loginForPendingToken(lastRegisteredEmail);

        for (int attempt = 0; attempt < FAILED_ATTEMPTS_BEFORE_LOCKOUT; attempt++) {
            mockMvc.perform(post("/auth/2fa/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyRequestBody(pendingToken, "000000")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyRequestBody(pendingToken, nextStepCode(secret))))
                .andExpect(status().isTooManyRequests());
    }
}
