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
 * Verifies V23 adds a nullable fixed_dj_member_id column to groups, with no
 * default: null keeps the historical earliest-joined-member behavior.
 */
@Testcontainers
class GroupFixedDjMigrationTest {

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
    void addsANullableFixedDjMemberIdColumnWithNoDefault() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT is_nullable, column_default, data_type FROM information_schema.columns "
                             + "WHERE table_name = 'groups' AND column_name = 'fixed_dj_member_id'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("is_nullable")).isEqualTo("YES");
            assertThat(resultSet.getString("column_default")).isNull();
            assertThat(resultSet.getString("data_type")).isEqualTo("bigint");
        }
    }
}
