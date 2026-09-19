package org.dariusturcu.backend.analytics;

import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots AnalyticsDataSourceConfig the same way Spring Boot's real autoconfiguration
 * process does, proving the analytics datasource, its Flyway migration, and its
 * JdbcTemplate wire up correctly alongside the auto-configured primary datasource,
 * rather than only through the hand-rolled JDBC setup the other analytics tests use.
 * Also scans the core JPA entities and repositories, the same way the real
 * application context does, so that the primary datasource's own Flyway migration
 * and Hibernate schema validation actually run here too, rather than only against
 * a context that never asks the core schema to prove itself.
 */
@Testcontainers
@SpringBootTest(classes = AnalyticsDataSourceConfigStartupTest.AnalyticsTestConfig.class)
class AnalyticsDataSourceConfigStartupTest {

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @ComponentScan(basePackageClasses = AnalyticsDataSourceConfig.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class AnalyticsTestConfig {
    }

    @Container
    static final PostgreSQLContainer<?> transactionalDatabase = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @Container
    static final PostgreSQLContainer<?> analyticsDatabase = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void configureDataSources(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", transactionalDatabase::getJdbcUrl);
        registry.add("spring.datasource.username", transactionalDatabase::getUsername);
        registry.add("spring.datasource.password", transactionalDatabase::getPassword);
        registry.add("analytics.datasource.url", analyticsDatabase::getJdbcUrl);
        registry.add("analytics.datasource.username", analyticsDatabase::getUsername);
        registry.add("analytics.datasource.password", analyticsDatabase::getPassword);
    }

    @Autowired
    @Qualifier("analyticsJdbcTemplate")
    private JdbcTemplate analyticsJdbcTemplate;

    @Autowired
    private AnalyticsEventRecorder analyticsEventRecorder;

    @Autowired
    private SongRepository songRepository;

    @Test
    void springWiresTheAnalyticsStoreAndRunsItsOwnFlywayMigration() {
        analyticsEventRecorder.recordEvent(AnalyticsEventType.LOGIN, new LoginEventPayload(1L));

        Integer analyticsEventCount = analyticsJdbcTemplate.queryForObject(
                "SELECT count(*) FROM analytics_events", Integer.class);
        assertThat(analyticsEventCount).isEqualTo(1);
    }

    @Test
    void thePrimaryDatasourceStillRunsItsOwnFlywayMigrationAlongsideTheAnalyticsOne() {
        // Reaching this point at all means Spring Boot's own auto-configured Flyway bean
        // migrated the primary datasource and Hibernate's schema validation passed against
        // the result, proving the analytics module's own Flyway bean doesn't satisfy Spring
        // Boot's @ConditionalOnMissingBean(Flyway.class) guard and silently skip it.
        assertThat(songRepository.findAll()).isEmpty();
    }
}
