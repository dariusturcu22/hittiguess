package org.dariusturcu.backend.catalog;

import org.dariusturcu.backend.controller.SongMetadataController;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.ai.AiFastDateResponse;
import org.dariusturcu.backend.model.ai.AiIdentifiedSong;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.dariusturcu.backend.service.PlaylistAccessService;
import org.dariusturcu.backend.service.PlaylistService;
import org.dariusturcu.backend.service.SongCatalogService;
import org.dariusturcu.backend.service.SongMetadataService;
import org.dariusturcu.backend.service.SongResolutionService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = VerifiedSongCatalogIntegrationTest.CatalogTestConfig.class)
class VerifiedSongCatalogIntegrationTest {
    private static final String VIDEO_ID = "catalogSong";
    private static final String ALTERNATE_VIDEO_ID = "otherUpload";
    private static final String YOUTUBE_URL_PREFIX = "https://www.youtube.com/watch?v=";
    private static final String RESOLVE_ENDPOINT = "/metadata/resolve";
    private static final int RELEASE_YEAR = 1999;
    private static final int PRIMARY_DURATION_SECONDS = 210;
    private static final int ALTERNATE_DURATION_SECONDS = 245;
    private static final int CONCURRENT_SUBMISSIONS = 6;
    private static final long WAIT_SECONDS = 30;
    private static final String SUCCESS_RESPONSE = """
            {"status":"SUCCESS","model":"fixture","content":{
              "title":"Track","main_artists":["Artist"],"featured_artists":["Guest"],
              "release_year":%s,"color":"abcdef","confidence":"high","source":"sources",
              "reasoning":"Agreement","verification_status":"%s","duration_seconds":%s,
              "canonical_song_id":%s}}
            """;

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    @Import({SongCatalogService.class, SongMetadataService.class, SongResolutionService.class,
            PlaylistService.class, PlaylistAccessService.class, SongMapper.class, PlaylistMapper.class})
    static class CatalogTestConfig {
        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        MockRestServiceServer mockServer(RestClient.Builder restClientBuilder) {
            return MockRestServiceServer.bindTo(restClientBuilder).build();
        }

        @Bean
        RestClient aiServiceRestClient(RestClient.Builder restClientBuilder, MockRestServiceServer mockServer) {
            return restClientBuilder.build();
        }

        @Bean
        CatalogSeedingService catalogSeedingService() {
            return mock(CatalogSeedingService.class);
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load().migrate();
    }

    @Autowired private SongMetadataService songMetadataService;
    @Autowired private SongCatalogService songCatalogService;
    @Autowired private SongResolutionService songResolutionService;
    @Autowired private PlaylistService playlistService;
    @Autowired private PlaylistRepository playlistRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockRestServiceServer mockServer;

    private User currentUser;
    private Playlist playlist;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE users, songs, playlists RESTART IDENTITY CASCADE");
        mockServer.reset();
        currentUser = new User();
        currentUser.setUsername("catalog-user");
        currentUser.setEmail("catalog-user@integration.test");
        currentUser.setRole(Role.USER);
        currentUser = userRepository.save(currentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(currentUser), null, List.of()));
        playlist = new Playlist();
        playlist.setName("Catalog confirmation");
        playlist.setInviteCode("CATALOGX");
        playlist.setOwner(currentUser);
        playlist = playlistRepository.save(playlist);
        mockMvc = MockMvcBuilders.standaloneSetup(new SongMetadataController(songMetadataService)).build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        mockServer.verify();
    }

    private void expectMetadata(VerificationStatus verificationStatus, Long matchedSongId, int durationSeconds) {
        mockServer.expect(requestTo(RESOLVE_ENDPOINT)).andRespond(withSuccess(
                SUCCESS_RESPONSE.formatted(RELEASE_YEAR, verificationStatus.name(), durationSeconds, matchedSongId),
                MediaType.APPLICATION_JSON));
    }

    private SongMetadataResponse metadata(Long matchedSongId, int durationSeconds) {
        return new SongMetadataResponse("Track", List.of("Artist"), List.of("Guest"), RELEASE_YEAR,
                "abcdef", "high", "sources", "Agreement", VerificationStatus.VERIFIED.name(),
                null, durationSeconds, matchedSongId);
    }

    private CreateSongRequest confirmation(String youtubeId) {
        return new CreateSongRequest("Artist", "Track", RELEASE_YEAR, youtubeId, "abcdef", null, true);
    }

    @Test
    void verifiedLookupSavesTheCatalogSongBeforeAnyPlaylistConfirmation() throws Exception {
        expectMetadata(VerificationStatus.VERIFIED, null, PRIMARY_DURATION_SECONDS);

        mockMvc.perform(get("/api/metadata/song").param("youtubeUrl", YOUTUBE_URL_PREFIX + VIDEO_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.verificationStatus").value("VERIFIED"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from songs", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from song_playlists", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select added_by from songs", Long.class)).isEqualTo(currentUser.getId());

        playlistService.createSong(playlist.getId(), confirmation(VIDEO_ID));

        assertThat(jdbcTemplate.queryForObject("select count(*) from songs", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from song_playlists", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList("select name from song_artists order by display_order", String.class))
                .containsExactly("Artist", "Guest");
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, names = {"UNVERIFIED", "NEEDS_REVIEW", "MANUAL_ENTRY"})
    void unverifiedPreviewWaitsForExplicitConfirmationAndReusesItsCachedResult(VerificationStatus verificationStatus) {
        expectMetadata(verificationStatus, null, PRIMARY_DURATION_SECONDS);
        songMetadataService.fetchMetadata(YOUTUBE_URL_PREFIX + VIDEO_ID);

        assertThat(jdbcTemplate.queryForObject("select count(*) from songs", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from song_playlists", Long.class)).isZero();

        playlistService.createSong(playlist.getId(), confirmation(VIDEO_ID));

        assertThat(jdbcTemplate.queryForObject("select count(*) from songs", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select verification_status from songs", String.class))
                .isEqualTo(verificationStatus.name());
        assertThat(jdbcTemplate.queryForObject("select count(*) from song_playlists", Long.class)).isEqualTo(1L);
    }

    @Test
    void alternateUploadPreviewAndConfirmationReuseTheExistingSongWithoutChangingItsMetadata() {
        Song originalSong = songCatalogService.persist(VIDEO_ID, metadata(null, PRIMARY_DURATION_SECONDS), currentUser);
        expectMetadata(VerificationStatus.VERIFIED, originalSong.getId(), ALTERNATE_DURATION_SECONDS);

        songMetadataService.fetchMetadata(YOUTUBE_URL_PREFIX + ALTERNATE_VIDEO_ID);

        assertThat(jdbcTemplate.queryForObject("select count(*) from song_playlists", Long.class)).isZero();
        assertThat(playlistService.createSong(playlist.getId(), confirmation(ALTERNATE_VIDEO_ID)).id())
                .isEqualTo(originalSong.getId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from songs", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select song_id from alternate_youtube_ids", Long.class))
                .isEqualTo(originalSong.getId());
        assertThat(jdbcTemplate.queryForObject("select youtube_id from songs", String.class)).isEqualTo(VIDEO_ID);
        assertThat(jdbcTemplate.queryForObject("select duration_seconds from songs", Integer.class))
                .isEqualTo(PRIMARY_DURATION_SECONDS);
        assertThat(jdbcTemplate.queryForList("select name from song_artists order by display_order", String.class))
                .containsExactly("Artist", "Guest");
    }

    @Test
    void patientResolutionOfAnAlternateUploadAlsoReusesTheExistingSong() {
        Song originalSong = songCatalogService.persist(VIDEO_ID, metadata(null, PRIMARY_DURATION_SECONDS), currentUser);
        expectMetadata(VerificationStatus.VERIFIED, originalSong.getId(), ALTERNATE_DURATION_SECONDS);

        assertThat(songResolutionService.resolveAndPersist(ALTERNATE_VIDEO_ID, currentUser).orElseThrow().getId())
                .isEqualTo(originalSong.getId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from songs", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from alternate_youtube_ids", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from song_playlists", Long.class)).isZero();
    }

    @Test
    void aLateProvisionalImportCannotOverwriteAVerifiedPreview() {
        Song originalSong = songCatalogService.persist(VIDEO_ID, metadata(null, PRIMARY_DURATION_SECONDS), currentUser);
        int provisionalYear = RELEASE_YEAR - 1;
        AiIdentifiedSong provisionalSong = new AiIdentifiedSong("Provisional title", List.of("Different artist"),
                List.of(), "123456", ALTERNATE_DURATION_SECONDS);
        AiFastDateResponse provisionalDate = new AiFastDateResponse(provisionalYear, "low", "source", "lane");

        assertThat(songResolutionService.persistFastTierAnswer(VIDEO_ID, provisionalSong, provisionalDate, currentUser).getId())
                .isEqualTo(originalSong.getId());
        assertThat(jdbcTemplate.queryForObject("select title from songs", String.class)).isEqualTo("Track");
        assertThat(jdbcTemplate.queryForObject("select release_year from songs", Integer.class)).isEqualTo(RELEASE_YEAR);
        assertThat(jdbcTemplate.queryForObject("select verification_status from songs", String.class)).isEqualTo("VERIFIED");
        assertThat(jdbcTemplate.queryForObject("select duration_seconds from songs", Integer.class))
                .isEqualTo(PRIMARY_DURATION_SECONDS);
    }

    @Test
    void repeatedVerifiedLookupKeepsTheExistingYearAndArtists() {
        Song originalSong = songCatalogService.persist(VIDEO_ID, metadata(null, PRIMARY_DURATION_SECONDS), currentUser);
        int changedYear = RELEASE_YEAR + 1;
        SongMetadataResponse changedMetadata = new SongMetadataResponse("Changed title", List.of("Different artist"),
                List.of(), changedYear, "123456", "high", "source", "Agreement", "VERIFIED", null,
                PRIMARY_DURATION_SECONDS, null);

        assertThat(songCatalogService.persist(VIDEO_ID, changedMetadata, currentUser).getId()).isEqualTo(originalSong.getId());
        assertThat(jdbcTemplate.queryForObject("select title from songs", String.class)).isEqualTo("Track");
        assertThat(jdbcTemplate.queryForObject("select release_year from songs", Integer.class)).isEqualTo(RELEASE_YEAR);
        assertThat(jdbcTemplate.queryForList("select name from song_artists order by display_order", String.class))
                .containsExactly("Artist", "Guest");
    }

    @Test
    void concurrentNewSongResultsCreateOnlyOneCatalogRow() throws Exception {
        List<Long> resolvedIds = persistConcurrently(VIDEO_ID, metadata(null, PRIMARY_DURATION_SECONDS));

        assertThat(resolvedIds).hasSize(CONCURRENT_SUBMISSIONS).containsOnly(resolvedIds.getFirst());
        assertThat(jdbcTemplate.queryForObject("select count(*) from songs", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from song_artists", Long.class)).isEqualTo(2L);
    }

    @Test
    void concurrentDuplicateMatchesCreateOnlyOneAlternateLink() throws Exception {
        Song originalSong = songCatalogService.persist(VIDEO_ID, metadata(null, PRIMARY_DURATION_SECONDS), currentUser);

        List<Long> resolvedIds = persistConcurrently(ALTERNATE_VIDEO_ID,
                metadata(originalSong.getId(), ALTERNATE_DURATION_SECONDS));

        assertThat(resolvedIds).hasSize(CONCURRENT_SUBMISSIONS).containsOnly(originalSong.getId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from songs", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from alternate_youtube_ids", Long.class)).isEqualTo(1L);
    }

    private List<Long> persistConcurrently(String youtubeId, SongMetadataResponse metadata) throws Exception {
        CountDownLatch ready = new CountDownLatch(CONCURRENT_SUBMISSIONS);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(CONCURRENT_SUBMISSIONS)) {
            List<Future<Long>> submissions = new ArrayList<>();
            for (int submissionNumber = 0; submissionNumber < CONCURRENT_SUBMISSIONS; submissionNumber++) {
                submissions.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent submissions did not start");
                    }
                    return songCatalogService.persist(youtubeId, metadata, currentUser).getId();
                }));
            }
            try {
                assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            List<Long> resolvedIds = new ArrayList<>();
            for (Future<Long> submission : submissions) {
                resolvedIds.add(submission.get(WAIT_SECONDS, TimeUnit.SECONDS));
            }
            return resolvedIds;
        }
    }
}
