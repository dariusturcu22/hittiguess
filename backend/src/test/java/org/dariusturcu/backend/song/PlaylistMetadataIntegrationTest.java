package org.dariusturcu.backend.song;

import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.dariusturcu.backend.service.SongDurationRefreshService;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = GroundTruthDataIntegrationTest.JpaTestConfig.class)
@Transactional
class PlaylistMetadataIntegrationTest {
    private static final int OFFICIAL_DURATION_SECONDS = 210;
    private static final int REFRESH_BATCH_SIZE = 50;
    private static final int FIRST_PAGE = 0;
    private static final Duration EXPIRED_AGE = SongDurationRefreshService.RETENTION.plus(Duration.ofHours(1));
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test
    void freshMigrationPersistsMetadataAndExposesApiFieldsWithoutChangingCreationTime() {
        User owner = new User();
        owner.setUsername("metadata-owner");
        owner.setEmail("metadata-owner@example.com");
        owner.setAuthProvider(AuthProvider.LOCAL);
        owner.setRole(Role.USER);
        owner = userRepository.save(owner);
        Song song = new Song();
        song.setYoutubeId("duration-video");
        song.setTitle("Duration track");
        song.recordOfficialDuration(OFFICIAL_DURATION_SECONDS);
        song = songRepository.save(song);
        Playlist playlist = new Playlist();
        playlist.setOwner(owner);
        playlist.setName("Metadata playlist");
        playlist.setColor("cba6f7");
        playlist.setInviteCode("METADATA");
        playlist.addSong(song);
        playlist = playlistRepository.saveAndFlush(playlist);
        Instant createdAt = playlist.getCreatedAt();
        Long playlistId = playlist.getId();
        entityManager.clear();
        Playlist reloaded = playlistRepository.findById(playlistId).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        reloaded.setName("Renamed playlist");
        playlistRepository.saveAndFlush(reloaded);
        entityManager.clear();
        reloaded = playlistRepository.findById(playlistId).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        var payload = new ObjectMapper().valueToTree(new PlaylistMapper(new SongMapper()).toDetailDTO(reloaded));
        assertThat(payload.get("createdAt").asString()).isNotBlank();
        var firstSong = payload.get("songs").iterator().next();
        assertThat(firstSong.get("durationSeconds").asInt()).isEqualTo(OFFICIAL_DURATION_SECONDS);
        songRepository.clearExpiredDurations(Instant.now());
        entityManager.clear();
        assertThat(songRepository.findById(song.getId()).orElseThrow().getDurationSeconds()).isNull();
    }

    @Test
    void refreshSelectionDeduplicatesUploadsAndProtectsConcurrentFreshMetadata() {
        Instant checkedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant expiredAt = checkedAt.minus(EXPIRED_AGE);
        Instant cutoff = checkedAt.minus(SongDurationRefreshService.RETENTION);
        Song expired = new Song();
        expired.setYoutubeId("expired-upload");
        expired.setDurationSeconds(OFFICIAL_DURATION_SECONDS);
        expired.setDurationFetchedAt(expiredAt);
        Song duplicate = new Song();
        duplicate.setYoutubeId("expired-upload");
        duplicate.setDurationSeconds(OFFICIAL_DURATION_SECONDS);
        duplicate.setDurationFetchedAt(expiredAt);
        Song fresh = new Song();
        fresh.setYoutubeId("fresh-upload");
        fresh.recordOfficialDuration(OFFICIAL_DURATION_SECONDS);
        songRepository.saveAllAndFlush(List.of(expired, duplicate, fresh));
        assertThat(songRepository.findDurationRefreshCandidates(cutoff, PageRequest.of(FIRST_PAGE, REFRESH_BATCH_SIZE)))
                .containsExactly("expired-upload");
        songRepository.clearExpiredDurations(cutoff);
        entityManager.clear();
        Song pending = songRepository.findById(expired.getId()).orElseThrow();
        assertThat(pending.getDurationSeconds()).isNull();
        assertThat(pending.getDurationFetchedAt()).isEqualTo(expiredAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(songRepository.updateRefreshedDuration("fresh-upload", null, checkedAt, cutoff)).isZero();
        assertThat(songRepository.updateRefreshedDuration("expired-upload", OFFICIAL_DURATION_SECONDS, checkedAt, cutoff))
                .isEqualTo(List.of(expired, duplicate).size());
        entityManager.clear();
        assertThat(songRepository.findDurationRefreshCandidates(cutoff, PageRequest.of(FIRST_PAGE, REFRESH_BATCH_SIZE))).isEmpty();
        assertThat(songRepository.findById(expired.getId()).orElseThrow().getDurationSeconds()).isEqualTo(OFFICIAL_DURATION_SECONDS);
        assertThat(songRepository.findById(fresh.getId()).orElseThrow().getDurationSeconds()).isEqualTo(OFFICIAL_DURATION_SECONDS);
        songRepository.updateRefreshedDuration("expired-upload", null, checkedAt, checkedAt);
        entityManager.clear();
        assertThat(songRepository.findById(expired.getId()).orElseThrow().getDurationSeconds()).isNull();
    }
}
