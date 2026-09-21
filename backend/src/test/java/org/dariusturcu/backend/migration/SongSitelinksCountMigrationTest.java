package org.dariusturcu.backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies V21 adds a nullable wikidata_sitelinks_count column to songs, with no
 * default: unknown stays null rather than passing as zero (obscure).
 */
@Testcontainers
class SongSitelinksCountMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static Connection connection;

    @BeforeAll
    static void migrateAndConnect() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void addsANullableSitelinksCountColumnWithNoDefault() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT is_nullable, column_default, data_type FROM information_schema.columns "
                             + "WHERE table_name = 'songs' AND column_name = 'wikidata_sitelinks_count'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("is_nullable")).isEqualTo("YES");
            assertThat(resultSet.getString("column_default")).isNull();
            assertThat(resultSet.getString("data_type")).isEqualTo("integer");
        }
    }
}
