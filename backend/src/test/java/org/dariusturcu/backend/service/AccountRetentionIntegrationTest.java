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
import org.dariusturcu.backend.scheduling.AccountRetentionSweeper;
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
@SpringBootTest(classes = AccountRetentionIntegrationTest.JpaTestConfig.class)
class AccountRetentionIntegrationTest {

    private static final int RETENTION_DAYS = 7;
    private static final int STALE_AGE_DAYS = 10;
    private static final int RECENT_AGE_DAYS = 2;
    private static final int TOKEN_EXPIRY_DAYS = 1;

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
        AccountRetentionService accountRetentionService(
                UserRepository userRepository,
                EmailVerificationTokenRepository emailVerificationTokenRepository,
                PasswordResetTokenRepository passwordResetTokenRepository,
                RefreshTokenRepository refreshTokenRepository
        ) {
            return new AccountRetentionService(
                    userRepository,
                    emailVerificationTokenRepository,
                    passwordResetTokenRepository,
                    refreshTokenRepository,
                    RETENTION_DAYS
            );
        }

        @Bean
        AccountRetentionSweeper accountRetentionSweeper(AccountRetentionService accountRetentionService) {
            return new AccountRetentionSweeper(accountRetentionService);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AccountRetentionService accountRetentionService;

    @Autowired
    private AccountRetentionSweeper accountRetentionSweeper;

    @BeforeEach
    void clearDatabase() {
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void purgesOnlyUnverifiedAccountsPastTheRetentionCutoff() {
        User staleUnverifiedUser = persistUser("stale-unverified", false, STALE_AGE_DAYS);
        attachVerificationToken(staleUnverifiedUser);
        attachPasswordResetToken(staleUnverifiedUser);
        attachRefreshToken(staleUnverifiedUser);

        User recentUnverifiedUser = persistUser("recent-unverified", false, RECENT_AGE_DAYS);
        attachVerificationToken(recentUnverifiedUser);

        User staleVerifiedUser = persistUser("stale-verified", true, STALE_AGE_DAYS);

        int purgedCount = accountRetentionService.purgeUnverifiedAccounts();

        assertThat(purgedCount).isEqualTo(1);
        assertThat(userRepository.findById(staleUnverifiedUser.getId())).isEmpty();
        assertThat(emailVerificationTokenRepository.findAll()).hasSize(1);
        assertThat(emailVerificationTokenRepository.findAll().getFirst().getUser().getId())
                .isEqualTo(recentUnverifiedUser.getId());
        assertThat(userRepository.findById(recentUnverifiedUser.getId())).isPresent();
        assertThat(userRepository.findById(staleVerifiedUser.getId())).isPresent();
    }

    @Test
    void sweeperExecutesSuccessfullyWithoutError() {
        persistUser("stale-unverified-sweeper", false, STALE_AGE_DAYS);

        accountRetentionSweeper.sweep();

        assertThat(userRepository.findAll()).isEmpty();
    }

    private User persistUser(String username, boolean emailVerified, int ageInDays) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("encoded-password");
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setEmailVerified(emailVerified);
        user.setCreatedAt(Instant.now().minus(ageInDays, ChronoUnit.DAYS));
        return userRepository.save(user);
    }

    private void attachVerificationToken(User user) {
        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUser(user);
        verificationToken.setToken(UUID.randomUUID().toString());
        verificationToken.setExpiresAt(Instant.now().plus(TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS));
        emailVerificationTokenRepository.save(verificationToken);
    }

    private void attachPasswordResetToken(User user) {
        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setUser(user);
        passwordResetToken.setToken(UUID.randomUUID().toString());
        passwordResetToken.setExpiresAt(Instant.now().plus(TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS));
        passwordResetToken.setUsed(false);
        passwordResetTokenRepository.save(passwordResetToken);
    }

    private void attachRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(Instant.now().plus(TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS));
        refreshToken.setRememberMe(false);
        refreshTokenRepository.save(refreshToken);
    }
}
