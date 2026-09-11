package org.dariusturcu.backend.migration;

import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.SongTag;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.SongRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A song row migrated from V1's original shape must still come back as a
 * valid SongDTO through the real repository and mapper, not just survive at
 * the raw-SQL level (SongSchemaMigrationTest covers that separately).
 */
@Testcontainers
@SpringBootTest
class SongApiCompatibilityAfterMigrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void migrateBaselineAndInsertLegacyRow() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target("1")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('legacy-user')");
            statement.execute("INSERT INTO playlists (invite_code) VALUES ('LEGACY1')");
            statement.execute(
                    "INSERT INTO songs (artist, title, release_year, youtube_id, song_tag, playlist_id, added_by) "
                            + "VALUES ('Legacy Artist', 'Legacy Title', 1999, 'dQw4w9WgXcQ', 'ANIME', "
                            + "(SELECT id FROM playlists WHERE invite_code = 'LEGACY1'), "
                            + "(SELECT id FROM users WHERE username = 'legacy-user'))"
            );
        }

        // Spring Boot's own Flyway auto-configuration applies V2-V4 when the context below starts,
        // since flyway_schema_history above already shows V1 as done, not baselining, migrating.
    }

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private SongMapper songMapper;

    @Test
    void migratedSongMapsToAValidDTO() {
        Song song = songRepository.findAll().stream()
                .filter(candidate -> "Legacy Title".equals(candidate.getTitle()))
                .findFirst()
                .orElseThrow();

        SongDTO dto = songMapper.toDTO(song);

        assertThat(dto.artists()).hasSize(1);
        assertThat(dto.artists().getFirst().name()).isEqualTo("Legacy Artist");
        assertThat(dto.tags()).containsExactly(SongTag.ANIME);
        assertThat(dto.verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(dto.confidence()).isNull();
    }
}
