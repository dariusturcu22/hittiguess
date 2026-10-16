package org.dariusturcu.backend.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Applies V45 on top of the full migration history and checks the seeded pace rows. */
@Testcontainers
class StagePaceMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    private static final List<String> PACED_STAGES =
            List.of("DISCOGS", "MUSICBRAINZ", "WIKIDATA", "WIKIPEDIA", "YOUTUBE_METADATA");

    private static final int NO_DELAY_MS = 0;
    private static final int NEGATIVE_DELAY_MS = -1;

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @Test
    void everyStageThatCallsAnExternalSourceHasOneSeededRow() {
        List<String> stages = jdbcTemplate.queryForList("SELECT stage FROM stage_pace ORDER BY stage", String.class);

        assertThat(stages).containsExactlyElementsOf(PACED_STAGES);
    }

    @Test
    void seededRowsStartWithoutCooldownDelayOrResponseTime() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT next_slot_at, cooldown_until, current_delay_ms, avg_response_ms, updated_at
                FROM stage_pace
                """);

        assertThat(rows).hasSize(PACED_STAGES.size()).allSatisfy(row -> {
            assertThat(row.get("next_slot_at")).isNotNull();
            assertThat(row.get("updated_at")).isNotNull();
            assertThat(row.get("cooldown_until")).isNull();
            assertThat(row.get("current_delay_ms")).isEqualTo(NO_DELAY_MS);
            assertThat(row.get("avg_response_ms")).isNull();
        });
    }

    @Test
    void aStageWithoutAnExternalSourceIsRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stage_pace (stage) VALUES ('EMBEDDING')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aNegativeDelayIsRejected() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE stage_pace SET current_delay_ms = ? WHERE stage = 'MUSICBRAINZ'", NEGATIVE_DELAY_MS))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
