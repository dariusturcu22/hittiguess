package org.dariusturcu.backend.migration;

import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.SongRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A song row inserted at the raw-SQL level against the fully migrated schema
 * must still come back as a valid SongDTO through the real repository and
 * mapper, confirming the entity mapping matches the schema every migration
 * up to and including the latest one actually produces.
 *
 * Uses a minimal JPA-only context (JpaTestConfig below) rather than the whole
 * BackendApplication: this project's OAuth2 client and AI-service RestClient
 * beans both build a java.net.http.HttpClient, which this sandbox's JDK can't
 * construct (a platform loopback-socket limitation, unrelated to this test),
 * and neither bean has anything to do with what's under test here anyway.
 */
@Testcontainers
@SpringBootTest(classes = SongApiCompatibilityAfterMigrationTest.JpaTestConfig.class)
@Transactional
class SongApiCompatibilityAfterMigrationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class JpaTestConfig {
        @Bean
        SongMapper songMapper() {
            return new SongMapper();
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    // Flyway runs by hand below, entirely before the Spring context exists, rather than
    // relying on Spring Boot's autoconfigured Flyway bean: that bean's run is tied to context
    // refresh timing relative to @BeforeAll, which isn't guaranteed to land after this class's
    // own @BeforeAll finishes. Disabling it here removes that ordering question entirely, the
    // database is already in its final migrated state by the time the context starts.
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
    }

    @BeforeAll
    static void migrateThenInsertASongAgainstTheFinalSchema() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO users (username) VALUES ('song-owner')");
            statement.execute(
                    "INSERT INTO songs (title, release_year, youtube_id, added_by) "
                            + "VALUES ('A Song', 1999, 'dQw4w9WgXcQ', "
                            + "(SELECT id FROM users WHERE username = 'song-owner'))"
            );
            statement.execute(
                    "INSERT INTO song_artists (song_id, name, role, display_order) "
                            + "VALUES ((SELECT id FROM songs WHERE title = 'A Song'), 'An Artist', 'MAIN', 0)"
            );
        }
    }

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private SongMapper songMapper;

    @Test
    void aSongInsertedAgainstTheFinalSchemaMapsToAValidDTO() {
        Song song = songRepository.findAll().stream()
                .filter(candidate -> "A Song".equals(candidate.getTitle()))
                .findFirst()
                .orElseThrow();

        SongDTO dto = songMapper.toDTO(song);

        assertThat(dto.artists()).hasSize(1);
        assertThat(dto.artists().getFirst().name()).isEqualTo("An Artist");
        assertThat(dto.genre()).isNull();
        assertThat(dto.verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(dto.confidence()).isNull();
    }
}
