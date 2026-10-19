package org.dariusturcu.backend.analytics;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the transactional database and the analytics store as two independent Postgres
 * containers, each migrated through its own Flyway history, to prove an analytics event
 * write never reaches the transactional side and never blocks it.
 */
@Testcontainers
class AnalyticsStoreIsolationIntegrationTest {

    private static final String ANALYTICS_MIGRATION_LOCATION = "classpath:db/analytics-migration";

    @Container
    static final PostgreSQLContainer<?> transactionalDatabase = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @Container
    static final PostgreSQLContainer<?> analyticsDatabase = new PostgreSQLContainer<>("postgres:18");

    private static Connection transactionalConnection;
    private static AnalyticsEventRecorder analyticsEventRecorder;
    private static JdbcTemplate analyticsJdbcTemplate;

    @BeforeAll
    static void migrateBothStoresIndependently() throws SQLException {
        Flyway.configure()
                .dataSource(transactionalDatabase.getJdbcUrl(), transactionalDatabase.getUsername(), transactionalDatabase.getPassword())
                .load()
                .migrate();

        Flyway.configure()
                .dataSource(analyticsDatabase.getJdbcUrl(), analyticsDatabase.getUsername(), analyticsDatabase.getPassword())
                .locations(ANALYTICS_MIGRATION_LOCATION)
                .load()
                .migrate();

        transactionalConnection = DriverManager.getConnection(
                transactionalDatabase.getJdbcUrl(), transactionalDatabase.getUsername(), transactionalDatabase.getPassword());

        DriverManagerDataSource analyticsDataSource = new DriverManagerDataSource(
                analyticsDatabase.getJdbcUrl(), analyticsDatabase.getUsername(), analyticsDatabase.getPassword());
        analyticsJdbcTemplate = new JdbcTemplate(analyticsDataSource);
        analyticsEventRecorder = new AnalyticsEventRecorder(analyticsJdbcTemplate, new ObjectMapper());
    }

    @Test
    void anEventWriteNeverReachesTheTransactionalDatabase() throws SQLException {
        try (Statement statement = transactionalConnection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM analytics_events"))
                    .isInstanceOf(SQLException.class);
        }

        long transactionalUserCountBeforeWrite = countTransactionalUsers();

        analyticsEventRecorder.recordEvent(AnalyticsEventType.LOGIN, new LoginEventPayload(1L));

        Integer analyticsEventCount = analyticsJdbcTemplate.queryForObject(
                "SELECT count(*) FROM analytics_events", Integer.class);
        assertThat(analyticsEventCount).isEqualTo(1);

        try (Statement statement = transactionalConnection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM analytics_events"))
                    .isInstanceOf(SQLException.class);
        }
        assertThat(countTransactionalUsers()).isEqualTo(transactionalUserCountBeforeWrite);
    }

    @Test
    void theTransactionalDatabaseStaysWritableImmediatelyAfterAnEventWrite() throws SQLException {
        analyticsEventRecorder.recordEvent(AnalyticsEventType.LOGIN, new LoginEventPayload(2L));

        try (Statement statement = transactionalConnection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('isolation-check-user')");
        }

        assertThat(countTransactionalUsers()).isGreaterThan(0);
    }

    private long countTransactionalUsers() throws SQLException {
        try (Statement statement = transactionalConnection.createStatement();
             var resultSet = statement.executeQuery("SELECT count(*) AS user_count FROM users")) {
            resultSet.next();
            return resultSet.getLong("user_count");
        }
    }
}
