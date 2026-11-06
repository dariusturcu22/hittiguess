package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.EmailVerificationToken;
import org.dariusturcu.backend.model.PasswordResetToken;
import org.dariusturcu.backend.model.RefreshToken;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.EmailVerificationTokenRepository;
import org.dariusturcu.backend.repository.PasswordResetTokenRepository;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.scheduling.TokenRetentionSweeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = TokenRetentionIntegrationTest.JpaTestConfig.class)
class TokenRetentionIntegrationTest {

    private static final int EXPIRED_HOURS = 2;
    private static final int ACTIVE_HOURS = 48;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, RefreshTokenRepository.class})
    static class JpaTestConfig {

        @Bean
        TokenRetentionService tokenRetentionService(
                RefreshTokenRepository refreshTokenRepository,
                PasswordResetTokenRepository passwordResetTokenRepository,
                EmailVerificationTokenRepository emailVerificationTokenRepository
        ) {
            return new TokenRetentionService(
                    refreshTokenRepository,
                    passwordResetTokenRepository,
                    emailVerificationTokenRepository
            );
        }

        @Bean
        TokenRetentionSweeper tokenRetentionSweeper(TokenRetentionService tokenRetentionService) {
            return new TokenRetentionSweeper(tokenRetentionService);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private TokenRetentionService tokenRetentionService;

    @Autowired
    private TokenRetentionSweeper tokenRetentionSweeper;

    private User testUser;
    private User anotherUser;

    @BeforeEach
    void setupUsers() {
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = persistUser("user-one");
        anotherUser = persistUser("user-two");
    }

    @Test
    void purgesExpiredAndUsedTokensWhileRetainingActiveTokens() {
        RefreshToken expiredRefreshToken = persistRefreshToken(testUser, Instant.now().minus(EXPIRED_HOURS, ChronoUnit.HOURS));
        RefreshToken activeRefreshToken = persistRefreshToken(anotherUser, Instant.now().plus(ACTIVE_HOURS, ChronoUnit.HOURS));

        PasswordResetToken expiredResetToken = persistPasswordResetToken(testUser, Instant.now().minus(EXPIRED_HOURS, ChronoUnit.HOURS), false);
        PasswordResetToken usedResetToken = persistPasswordResetToken(anotherUser, Instant.now().plus(ACTIVE_HOURS, ChronoUnit.HOURS), true);

        User thirdUser = persistUser("user-three");
        PasswordResetToken activeResetToken = persistPasswordResetToken(thirdUser, Instant.now().plus(ACTIVE_HOURS, ChronoUnit.HOURS), false);

        EmailVerificationToken expiredVerificationToken = persistEmailToken(testUser, Instant.now().minus(EXPIRED_HOURS, ChronoUnit.HOURS));
        EmailVerificationToken activeVerificationToken = persistEmailToken(anotherUser, Instant.now().plus(ACTIVE_HOURS, ChronoUnit.HOURS));

        int purgedTokensCount = tokenRetentionService.purgeExpiredTokens();

        assertThat(purgedTokensCount).isEqualTo(4);

        assertThat(refreshTokenRepository.findById(expiredRefreshToken.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(activeRefreshToken.getId())).isPresent();

        assertThat(passwordResetTokenRepository.findById(expiredResetToken.getId())).isEmpty();
        assertThat(passwordResetTokenRepository.findById(usedResetToken.getId())).isEmpty();
        assertThat(passwordResetTokenRepository.findById(activeResetToken.getId())).isPresent();

        assertThat(emailVerificationTokenRepository.findById(expiredVerificationToken.getId())).isEmpty();
        assertThat(emailVerificationTokenRepository.findById(activeVerificationToken.getId())).isPresent();
    }

    @Test
    void sweeperExecutesSuccessfully() {
        persistRefreshToken(testUser, Instant.now().minus(EXPIRED_HOURS, ChronoUnit.HOURS));

        tokenRetentionSweeper.sweep();

        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("password");
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private RefreshToken persistRefreshToken(User user, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(expiresAt);
        refreshToken.setRememberMe(false);
        return refreshTokenRepository.save(refreshToken);
    }

    private PasswordResetToken persistPasswordResetToken(User user, Instant expiresAt, boolean used) {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setExpiresAt(expiresAt);
        resetToken.setUsed(used);
        return passwordResetTokenRepository.save(resetToken);
    }

    private EmailVerificationToken persistEmailToken(User user, Instant expiresAt) {
        EmailVerificationToken emailToken = new EmailVerificationToken();
        emailToken.setUser(user);
        emailToken.setToken(UUID.randomUUID().toString());
        emailToken.setExpiresAt(expiresAt);
        return emailVerificationTokenRepository.save(emailToken);
    }
}
