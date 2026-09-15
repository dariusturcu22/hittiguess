package org.dariusturcu.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the whole BackendApplication context against a real transactional database and a
 * real analytics database, the one test that exercises the entire bean graph rather than a
 * scoped slice. The transactional store uses pgvector (the embeddings column depends on it);
 * the analytics store is a plain Postgres migrated by its own independent Flyway. External
 * integrations that only fail on use, not construction (the AI-service RestClient, the OAuth2
 * client) are wired with placeholder configuration so the context builds without reaching them.
 */
@Testcontainers
@SpringBootTest
class BackendApplicationTests {

    private static final String TRANSACTIONAL_DATABASE_IMAGE = "pgvector/pgvector:pg18";
    private static final String ANALYTICS_DATABASE_IMAGE = "postgres:18";
    private static final String PLACEHOLDER_JWT_SECRET = "context-load-test-signing-secret-of-sufficient-length";
    private static final String PLACEHOLDER_INTERNAL_API_KEY = "context-load-test-internal-api-key";
    private static final String PLACEHOLDER_OAUTH_CLIENT_ID = "context-load-test-oauth-client-id";
    private static final String PLACEHOLDER_OAUTH_CLIENT_SECRET = "context-load-test-oauth-client-secret";
    private static final String PLACEHOLDER_FRONTEND_URL = "http://localhost:3000";

    @Container
    static final PostgreSQLContainer<?> transactionalDatabase =
            new PostgreSQLContainer<>(TRANSACTIONAL_DATABASE_IMAGE);

    @Container
    static final PostgreSQLContainer<?> analyticsDatabase =
            new PostgreSQLContainer<>(ANALYTICS_DATABASE_IMAGE);

    @DynamicPropertySource
    static void registerContextProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", transactionalDatabase::getJdbcUrl);
        registry.add("spring.datasource.username", transactionalDatabase::getUsername);
        registry.add("spring.datasource.password", transactionalDatabase::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("analytics.datasource.url", analyticsDatabase::getJdbcUrl);
        registry.add("analytics.datasource.username", analyticsDatabase::getUsername);
        registry.add("analytics.datasource.password", analyticsDatabase::getPassword);
        registry.add("jwt.secret", () -> PLACEHOLDER_JWT_SECRET);
        registry.add("ai.service.internal-api-key", () -> PLACEHOLDER_INTERNAL_API_KEY);
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> PLACEHOLDER_OAUTH_CLIENT_ID);
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> PLACEHOLDER_OAUTH_CLIENT_SECRET);
        registry.add("app.oauth2.redirect-uri", () -> PLACEHOLDER_FRONTEND_URL + "/oauth2/redirect");
    }

    // Spring's own Flyway is disabled here because baseline-on-migrate with baseline-version 1
    // skips V1 against a fresh database, leaving tables Hibernate then fails to validate. A clean
    // migrate with no baseline runs every migration from V1, matching how the JPA integration
    // tests prepare their schema. The analytics store keeps its own in-context Flyway bean.
    @BeforeAll
    static void migrateTransactionalSchema() {
        Flyway.configure()
                .dataSource(
                        transactionalDatabase.getJdbcUrl(),
                        transactionalDatabase.getUsername(),
                        transactionalDatabase.getPassword())
                .load()
                .migrate();
    }

    @Test
    void contextLoads() {
    }

}
