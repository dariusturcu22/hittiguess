package org.dariusturcu.backend.analytics;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies AnalyticsRetentionService.purgeExpiredEvents against a real analytics database:
 * events older than the retention window are deleted, events inside it are left alone.
 */
@Testcontainers
class AnalyticsRetentionCleanupIntegrationTest {

    private static final String ANALYTICS_MIGRATION_LOCATION = "classpath:db/analytics-migration";
    private static final int RETENTION_DAYS = 30;
    private static final String INSERT_EVENT_AT_AGE_SQL =
            "INSERT INTO analytics_events (event_type, occurred_at, payload) VALUES (?, ?, CAST(? AS jsonb))";

    @Container
    static final PostgreSQLContainer<?> analyticsDatabase = new PostgreSQLContainer<>("postgres:18");

    private static JdbcTemplate analyticsJdbcTemplate;

    @BeforeAll
    static void migrateAnalyticsStore() {
        Flyway.configure()
                .dataSource(analyticsDatabase.getJdbcUrl(), analyticsDatabase.getUsername(), analyticsDatabase.getPassword())
                .locations(ANALYTICS_MIGRATION_LOCATION)
                .load()
                .migrate();

        DriverManagerDataSource analyticsDataSource = new DriverManagerDataSource(
                analyticsDatabase.getJdbcUrl(), analyticsDatabase.getUsername(), analyticsDatabase.getPassword());
        analyticsJdbcTemplate = new JdbcTemplate(analyticsDataSource);
    }

    @BeforeEach
    void clearEvents() {
        analyticsJdbcTemplate.update("DELETE FROM analytics_events");
    }

    @Test
    void purgeRemovesOnlyEventsOlderThanTheRetentionWindow() {
        int daysPastRetention = 10;
        int daysStillWithinRetention = 10;
        insertEventAgedInDays(RETENTION_DAYS + daysPastRetention);
        insertEventAgedInDays(RETENTION_DAYS - daysStillWithinRetention);

        AnalyticsRetentionService retentionService = new AnalyticsRetentionService(analyticsJdbcTemplate, RETENTION_DAYS);
        int purgedEventCount = retentionService.purgeExpiredEvents();

        assertThat(purgedEventCount).isEqualTo(1);
        Integer remainingEventCount = analyticsJdbcTemplate.queryForObject(
                "SELECT count(*) FROM analytics_events", Integer.class);
        assertThat(remainingEventCount).isEqualTo(1);
    }

    @Test
    void purgeLeavesEverythingWhenNoEventIsOlderThanTheRetentionWindow() {
        insertEventAgedInDays(RETENTION_DAYS - 1);

        AnalyticsRetentionService retentionService = new AnalyticsRetentionService(analyticsJdbcTemplate, RETENTION_DAYS);
        int purgedEventCount = retentionService.purgeExpiredEvents();

        assertThat(purgedEventCount).isEqualTo(0);
        Integer remainingEventCount = analyticsJdbcTemplate.queryForObject(
                "SELECT count(*) FROM analytics_events", Integer.class);
        assertThat(remainingEventCount).isEqualTo(1);
    }

    private void insertEventAgedInDays(int ageInDays) {
        Instant occurredAt = Instant.now().minus(ageInDays, ChronoUnit.DAYS);
        analyticsJdbcTemplate.update(
                INSERT_EVENT_AT_AGE_SQL, AnalyticsEventType.LOGIN.name(), Timestamp.from(occurredAt), "{}");
    }
}
