package org.dariusturcu.backend.analytics;

import org.dariusturcu.backend.history.AccountHistoryDeleted;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AnalyticsEventErasureIntegrationTest {

    private static final String ANALYTICS_MIGRATION_LOCATION = "classpath:db/analytics-migration";
    private static final long DELETED_USER_ID = 41L;
    private static final long OTHER_USER_ID = 42L;
    private static final long ANY_ID = 7L;
    private static final int ANY_COUNT = 3;
    private static final int EVENTS_NAMING_DELETED_USER = 7;

    @Container
    static final PostgreSQLContainer<?> analyticsDatabase = new PostgreSQLContainer<>("postgres:18");

    private static JdbcTemplate analyticsJdbcTemplate;
    private static AnalyticsEventRecorder recorder;
    private static AnalyticsEventErasureService erasureService;

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
        recorder = new AnalyticsEventRecorder(analyticsJdbcTemplate, new ObjectMapper());
        erasureService = new AnalyticsEventErasureService(analyticsJdbcTemplate);
    }

    @BeforeEach
    void clearEvents() {
        analyticsJdbcTemplate.update("DELETE FROM analytics_events");
    }

    @Test
    void erasesEveryEventShapeThatNamesTheDeletedUserAndKeepsOtherUsersEvents() {
        recorder.recordEvent(AnalyticsEventType.LOGIN, new LoginEventPayload(DELETED_USER_ID));
        recorder.recordEvent(AnalyticsEventType.PLAYLIST_CREATED, new PlaylistCreatedEventPayload(DELETED_USER_ID, ANY_ID));
        recorder.recordEvent(AnalyticsEventType.SONG_SUBMITTED, new SongSubmittedEventPayload(DELETED_USER_ID, ANY_ID));
        recorder.recordEvent(AnalyticsEventType.RATE_LIMIT_EXCEEDED, new RateLimitExceededEventPayload(DELETED_USER_ID, "/login"));
        recorder.recordEvent(AnalyticsEventType.REPORT_SUBMITTED, new ReportSubmittedEventPayload(DELETED_USER_ID, ANY_ID));
        recorder.recordEvent(AnalyticsEventType.GAME_SESSION_STARTED,
                new GameSessionStartedPayload(ANY_ID, List.of(OTHER_USER_ID, DELETED_USER_ID), Instant.now()));
        recorder.recordEvent(AnalyticsEventType.GAME_SESSION_ENDED, new GameSessionEndedPayload(
                ANY_ID, List.of(OTHER_USER_ID), OTHER_USER_ID,
                Map.of(OTHER_USER_ID, ANY_COUNT, DELETED_USER_ID, ANY_COUNT),
                Map.of(OTHER_USER_ID, ANY_COUNT), Instant.now()));
        recorder.recordEvent(AnalyticsEventType.LOGIN, new LoginEventPayload(OTHER_USER_ID));
        recorder.recordEvent(AnalyticsEventType.FAILED_LOGIN_ATTEMPT, new FailedLoginAttemptEventPayload());

        int erasedEvents = erasureService.deleted(new AccountHistoryDeleted(DELETED_USER_ID));

        assertThat(erasedEvents).isEqualTo(EVENTS_NAMING_DELETED_USER);
        assertThat(analyticsJdbcTemplate.queryForList("SELECT event_type FROM analytics_events ORDER BY id", String.class))
                .containsExactly(AnalyticsEventType.LOGIN.name(), AnalyticsEventType.FAILED_LOGIN_ATTEMPT.name());
    }

    @Test
    void failedLoginAttemptPayloadCarriesNoAttemptedUsername() {
        recorder.recordEvent(AnalyticsEventType.FAILED_LOGIN_ATTEMPT, new FailedLoginAttemptEventPayload());

        String storedPayload = analyticsJdbcTemplate.queryForObject(
                "SELECT payload::text FROM analytics_events", String.class);
        assertThat(storedPayload).doesNotContain("username");
    }
}
