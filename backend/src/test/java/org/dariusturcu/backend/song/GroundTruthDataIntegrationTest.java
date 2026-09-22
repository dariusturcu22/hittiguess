package org.dariusturcu.backend.song;

import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.GroundTruthSongDTO;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.service.GroundTruthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the ground-truth path returns verified songs only, in a triple shape
 * with no YouTube-sourced fields, with working page boundaries, against a real
 * Postgres instance with the real Flyway migrations applied.
 *
 * Uses a minimal JPA-only context (JpaTestConfig below) rather than the whole
 * BackendApplication: this project's OAuth2 client and AI-service RestClient
 * beans both build a java.net.http.HttpClient, which this sandbox's JDK can't
 * construct (a platform loopback-socket limitation, unrelated to this test).
 */
@Testcontainers
@SpringBootTest(classes = GroundTruthDataIntegrationTest.JpaTestConfig.class)
@Transactional
class GroundTruthDataIntegrationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class JpaTestConfig {
        @Bean
        GroundTruthService groundTruthService(SongRepository songRepository) {
            return new GroundTruthService(songRepository);
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private GroundTruthService groundTruthService;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User persistUser() {
        User user = new User();
        user.setUsername("ground-truth-user-" + UUID.randomUUID());
        user.setEmail("ground-truth-" + UUID.randomUUID() + "@example.com");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private void persistSong(User addedBy, String title, int releaseYear, String youtubeId,
                             VerificationStatus status, String... artistNames) {
        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(releaseYear);
        song.setYoutubeId(youtubeId);
        song.setVerificationStatus(status);
        song.setAddedBy(addedBy);
        Song savedSong = songRepository.save(song);
        for (int artistIndex = 0; artistIndex < artistNames.length; artistIndex++) {
            SongArtist artist = new SongArtist();
            artist.setSong(savedSong);
            artist.setName(artistNames[artistIndex]);
            artist.setRole(ArtistRole.MAIN);
            artist.setDisplayOrder(artistIndex);
            savedSong.getArtists().add(artist);
        }
        songRepository.save(savedSong);
    }

    @Test
    void returnsOnlyVerifiedSongsWithoutYoutubeFields() throws Exception {
        User adder = persistUser();
        persistSong(adder, "Verified One", 1975, "verified-video-1", VerificationStatus.VERIFIED, "Queen");
        persistSong(adder, "Verified Two", 1985, "verified-video-2", VerificationStatus.VERIFIED, "First", "Second");
        persistSong(adder, "Needs Review", 1995, "review-video-1", VerificationStatus.NEEDS_REVIEW, "Unknown");
        persistSong(adder, "Manual Entry", 2005, "manual-video-1", VerificationStatus.MANUAL_ENTRY, "Unknown");

        Page<GroundTruthSongDTO> page = groundTruthService.verifiedSongs(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(GroundTruthSongDTO::title)
                .containsExactlyInAnyOrder("Verified One", "Verified Two");

        String payload = objectMapper.writeValueAsString(page.getContent());
        assertThat(payload).doesNotContain("youtubeId");
        assertThat(payload).doesNotContain("verified-video");
        List<Map<String, Object>> triples = objectMapper.readValue(payload, List.class);
        assertThat(triples).allSatisfy(triple -> assertThat(triple.keySet())
                .containsExactlyInAnyOrder("artist", "title", "releaseYear"));
    }

    @Test
    void honorsPageBoundaries() {
        User adder = persistUser();
        persistSong(adder, "Paged One", 1970, "paged-video-1", VerificationStatus.VERIFIED, "Solo");
        persistSong(adder, "Paged Two", 1971, "paged-video-2", VerificationStatus.VERIFIED, "Solo");
        persistSong(adder, "Paged Three", 1972, "paged-video-3", VerificationStatus.VERIFIED, "Solo");

        Page<GroundTruthSongDTO> firstPage = groundTruthService.verifiedSongs(PageRequest.of(0, 2));
        Page<GroundTruthSongDTO> secondPage = groundTruthService.verifiedSongs(PageRequest.of(1, 2));
        Page<GroundTruthSongDTO> pastEndPage = groundTruthService.verifiedSongs(PageRequest.of(5, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(pastEndPage.getContent()).isEmpty();
    }
}
