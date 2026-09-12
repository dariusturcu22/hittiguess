package org.dariusturcu.backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies V9's groups/members/group_playlists tables carry the constraints
 * story 39's one-active-group-per-user rule and join-code uniqueness depend on.
 */
@Testcontainers
class GroupSchemaMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static Connection connection;
    private static long firstUserId;
    private static long secondUserId;
    private static long groupId;

    @BeforeAll
    static void migrateAndInsertAGroupWithOneMember() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('first-user')");
            statement.execute("INSERT INTO users (username) VALUES ('second-user')");
            statement.execute(
                    "INSERT INTO groups (invite_code, join_code, status, dj_mode, win_condition_card_count, created_at, expires_at) "
                            + "VALUES ('invite-1', 'ABCD', 'OPEN', 'FIXED', 5, now(), now() + interval '30 minutes')"
            );

            try (var resultSet = statement.executeQuery("SELECT id FROM users WHERE username = 'first-user'")) {
                resultSet.next();
                firstUserId = resultSet.getLong("id");
            }
            try (var resultSet = statement.executeQuery("SELECT id FROM users WHERE username = 'second-user'")) {
                resultSet.next();
                secondUserId = resultSet.getLong("id");
            }
            try (var resultSet = statement.executeQuery("SELECT id FROM groups WHERE invite_code = 'invite-1'")) {
                resultSet.next();
                groupId = resultSet.getLong("id");
            }

            statement.execute(
                    "INSERT INTO members (group_id, user_id, display_name, is_admin, is_connected, is_in_voice, joined_at) "
                            + "VALUES (" + groupId + ", " + firstUserId + ", 'First Member', true, true, false, now())"
            );
        }
    }

    @Test
    void aSecondGroupCannotReuseAnAlreadyTakenJoinCode() {
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "INSERT INTO groups (invite_code, join_code, status, dj_mode, win_condition_card_count, created_at, expires_at) "
                                + "VALUES ('invite-2', 'ABCD', 'OPEN', 'FIXED', 5, now(), now() + interval '30 minutes')"
                );
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    void aUserCannotHoldTwoMembershipRowsAtOnce() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO groups (invite_code, join_code, status, dj_mode, win_condition_card_count, created_at, expires_at) "
                            + "VALUES ('invite-3', 'WXYZ', 'OPEN', 'FIXED', 5, now(), now() + interval '30 minutes')"
            );
            try (var resultSet = statement.executeQuery("SELECT id FROM groups WHERE invite_code = 'invite-3'")) {
                resultSet.next();
                long otherGroupId = resultSet.getLong("id");

                assertThatThrownBy(() -> {
                    try (Statement insertStatement = connection.createStatement()) {
                        insertStatement.execute(
                                "INSERT INTO members (group_id, user_id, display_name, is_admin, is_connected, is_in_voice, joined_at) "
                                        + "VALUES (" + otherGroupId + ", " + firstUserId + ", 'Duplicate Membership', false, true, false, now())"
                        );
                    }
                }).isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void aDifferentUserCanStillJoinTheSameGroup() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO members (group_id, user_id, display_name, is_admin, is_connected, is_in_voice, joined_at) "
                            + "VALUES (" + groupId + ", " + secondUserId + ", 'Second Member', false, true, false, now())"
            );
            try (var resultSet = statement.executeQuery(
                    "SELECT count(*) AS member_count FROM members WHERE group_id = " + groupId)) {
                resultSet.next();
                assertThat(resultSet.getInt("member_count")).isEqualTo(2);
            }
        }
    }
}
