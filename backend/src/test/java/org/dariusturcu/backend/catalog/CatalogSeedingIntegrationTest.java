package org.dariusturcu.backend.catalog;

import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.AlternateYoutubeId;
import org.dariusturcu.backend.model.song.BacklogStatusDTO;
import org.dariusturcu.backend.model.song.BulkImportRequest;
import org.dariusturcu.backend.model.song.BulkImportResultDTO;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.AlternateYoutubeIdRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.BulkImportService;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.dariusturcu.backend.service.MetadataPriorityCoordinator;
import org.dariusturcu.backend.service.PendingImportProcessor;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.dariusturcu.backend.service.PlaylistImportService;
import org.dariusturcu.backend.service.SongMetadataService;
import org.dariusturcu.backend.service.SongResolutionService;
import org.dariusturcu.backend.service.YoutubeIdLookupService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the catalog-seeding backlog and the on-the-spot import path against a real
 * Postgres instance, with the metadata resolver stubbed so no external call happens.
 * Uses the minimal JPA-only context, the same reason GroupLifecycleIntegrationTest does:
 * this project's OAuth2 and AI-service beans build an HttpClient this sandbox's JDK cannot
 * construct, and neither is needed to test the queueing and priority behavior.
 */
@Testcontainers
@SpringBootTest(classes = CatalogSeedingIntegrationTest.CatalogTestConfig.class)
class CatalogSeedingIntegrationTest {

    private static final long DAILY_DRAIN_QUOTA = 3;

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class CatalogTestConfig {

        @Bean
        StubMetadataResolver stubMetadataResolver() {
            return new StubMetadataResolver();
        }

        @Bean
        YoutubeIdLookupService youtubeIdLookupService(SongRepository songRepository,
                                                      AlternateYoutubeIdRepository alternateYoutubeIdRepository) {
            return new YoutubeIdLookupService(songRepository, alternateYoutubeIdRepository);
        }

        @Bean
        SongResolutionService songResolutionService(StubMetadataResolver stubMetadataResolver,
                                                     SongRepository songRepository) {
            return new SongResolutionService(stubMetadataResolver, songRepository);
        }

        @Bean
        PendingImportProcessor pendingImportProcessor(PendingImportRepository pendingImportRepository,
                                                      SongResolutionService songResolutionService) {
            return new PendingImportProcessor(pendingImportRepository, songResolutionService);
        }

        @Bean
        MetadataPriorityCoordinator metadataPriorityCoordinator() {
            return new MetadataPriorityCoordinator();
        }

        @Bean
        PlaylistExpansionService playlistExpansionService() {
            // No test here submits a playlist link, so the AI-service RestClient this
            // service would call is never actually invoked.
            return new PlaylistExpansionService(null);
        }

        @Bean
        PlaylistImportService playlistImportService() {
            // No test here submits a targetPlaylistId, so this service's dependencies
            // are never actually invoked.
            return new PlaylistImportService(null, null);
        }

        @Bean
        CatalogSeedingService catalogSeedingService(PendingImportRepository pendingImportRepository,
                                                    YoutubeIdLookupService youtubeIdLookupService,
                                                    PendingImportProcessor pendingImportProcessor,
                                                    MetadataPriorityCoordinator metadataPriorityCoordinator,
                                                    PlaylistExpansionService playlistExpansionService,
                                                    @Value("${catalog.seeding.daily-drain-quota}") long dailyDrainQuota) {
            return new CatalogSeedingService(pendingImportRepository, youtubeIdLookupService,
                    pendingImportProcessor, metadataPriorityCoordinator, playlistExpansionService, dailyDrainQuota);
        }

        @Bean
        BulkImportService bulkImportService(YoutubeIdLookupService youtubeIdLookupService,
                                            SongResolutionService songResolutionService,
                                            CatalogSeedingService catalogSeedingService,
                                            MetadataPriorityCoordinator metadataPriorityCoordinator,
                                            PlaylistExpansionService playlistExpansionService,
                                            PlaylistImportService playlistImportService,
                                            ApplicationEventPublisher applicationEventPublisher) {
            return new BulkImportService(youtubeIdLookupService, songResolutionService,
                    catalogSeedingService, metadataPriorityCoordinator, playlistExpansionService,
                    playlistImportService, applicationEventPublisher);
        }
    }

    /**
     * A SongMetadataService that resolves deterministically without any HTTP call, and
     * records the order in which it was asked to resolve so a test can assert the drain
     * yielded to on-the-spot traffic. IDs added to unresolvableYoutubeIds resolve to an
     * error response instead of a song.
     */
    static class StubMetadataResolver extends SongMetadataService {
        final CopyOnWriteArrayList<String> resolutionOrder = new CopyOnWriteArrayList<>();
        final Set<String> unresolvableYoutubeIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

        StubMetadataResolver() {
            super(null);
        }

        @Override
        public AiResponse resolveByYoutubeId(String youtubeId) {
            resolutionOrder.add(youtubeId);
            if (unresolvableYoutubeIds.contains(youtubeId)) {
                return new AiResponse(null, "stub-model", 0, LocalDateTime.now(), "ERROR", null, null);
            }
            SongMetadataResponse content = new SongMetadataResponse(
                    "Title for " + youtubeId, "Artist for " + youtubeId, 1999,
                    "111111", "high", "musicbrainz", "stubbed", "NEEDS_REVIEW");
            return new AiResponse(content, "stub-model", 0, LocalDateTime.now(), "SUCCESS", null, null);
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("catalog.seeding.daily-drain-quota", () -> DAILY_DRAIN_QUOTA);
    }

    @BeforeAll
    static void migrate() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private CatalogSeedingService catalogSeedingService;
    @Autowired
    private BulkImportService bulkImportService;
    @Autowired
    private YoutubeIdLookupService youtubeIdLookupService;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private AlternateYoutubeIdRepository alternateYoutubeIdRepository;
    @Autowired
    private PendingImportRepository pendingImportRepository;
    @Autowired
    private MetadataPriorityCoordinator metadataPriorityCoordinator;
    @Autowired
    private StubMetadataResolver stubMetadataResolver;

    @BeforeEach
    void resetState() {
        pendingImportRepository.deleteAll();
        alternateYoutubeIdRepository.deleteAll();
        songRepository.deleteAll();
        stubMetadataResolver.resolutionOrder.clear();
        stubMetadataResolver.unresolvableYoutubeIds.clear();
        while (metadataPriorityCoordinator.isOnTheSpotTrafficActive()) {
            metadataPriorityCoordinator.endOnTheSpotWork();
        }

        User submittingUser = new User();
        submittingUser.setUsername("bulk-import-submitter");
        submittingUser.setEmail("bulk-import-submitter@integration.test");
        submittingUser.setRole(Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(submittingUser), null, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void batchLookupPartitionsKnownAlternateAndUnknownIdsInOneBatch() {
        Song knownSong = persistSong("known-primary-id");
        alternateYoutubeIdRepository.save(alternateIdFor("alternate-known-id", knownSong));

        YoutubeIdLookupResult result = youtubeIdLookupService.partitionKnownAndUnknown(
                List.of("known-primary-id", "alternate-known-id", "genuinely-new-id"));

        assertThat(result.knownYoutubeIds()).containsExactlyInAnyOrder("known-primary-id", "alternate-known-id");
        assertThat(result.unknownYoutubeIds()).containsExactly("genuinely-new-id");
    }

    @Test
    void anAlternateIdResolvesToItsCanonicalSong() {
        Song canonicalSong = persistSong("canonical-primary-id");
        AlternateYoutubeId linked = youtubeIdLookupService.linkAlternateId("re-upload-id", canonicalSong);

        assertThat(linked.getSong().getId()).isEqualTo(canonicalSong.getId());
        assertThat(youtubeIdLookupService.isKnown("re-upload-id")).isTrue();
        assertThat(alternateYoutubeIdRepository.existsByYoutubeId("re-upload-id")).isTrue();
    }

    @Test
    void enqueueSkipsAlreadyKnownSongsAndOnlyAddsGenuinelyNewIds() {
        persistSong("already-known-id");

        EnqueueResultDTO result = catalogSeedingService.enqueue(
                List.of("already-known-id", "new-id-one", "new-id-two"));

        assertThat(result.skippedAlreadyKnownYoutubeIds()).containsExactly("already-known-id");
        assertThat(result.enqueuedYoutubeIds()).containsExactlyInAnyOrder("new-id-one", "new-id-two");
        assertThat(pendingImportRepository.countByStatus(PendingImportStatus.PENDING)).isEqualTo(2);
    }

    @Test
    void enqueueSkipsIdsAlreadySittingInTheBacklog() {
        catalogSeedingService.enqueue(List.of("backlog-id"));

        EnqueueResultDTO secondEnqueue = catalogSeedingService.enqueue(List.of("backlog-id", "another-new-id"));

        assertThat(secondEnqueue.skippedAlreadyQueuedYoutubeIds()).containsExactly("backlog-id");
        assertThat(secondEnqueue.enqueuedYoutubeIds()).containsExactly("another-new-id");
    }

    @Test
    void theDrainRespectsTheDailyQuotaAndDoesNotExceedIt() {
        catalogSeedingService.enqueue(List.of("id-1", "id-2", "id-3", "id-4", "id-5"));

        int resolvedThisRun = catalogSeedingService.drainBacklog();

        assertThat(resolvedThisRun).isEqualTo((int) DAILY_DRAIN_QUOTA);
        assertThat(pendingImportRepository.countByStatus(PendingImportStatus.DONE)).isEqualTo(DAILY_DRAIN_QUOTA);
        assertThat(pendingImportRepository.countByStatus(PendingImportStatus.PENDING)).isEqualTo(2);

        BacklogStatusDTO status = catalogSeedingService.backlogStatus();
        assertThat(status.processedTodayCount()).isEqualTo(DAILY_DRAIN_QUOTA);
        assertThat(status.quotaRemainingToday()).isZero();

        int resolvedSecondRun = catalogSeedingService.drainBacklog();
        assertThat(resolvedSecondRun).isZero();
    }

    @Test
    void anOnTheSpotRequestResolvesEvenWhenTheSameIdIsInTheAdminBacklog() {
        catalogSeedingService.enqueue(List.of("contendedID"));
        assertThat(pendingImportRepository.countByStatus(PendingImportStatus.PENDING)).isEqualTo(1);

        BulkImportResultDTO result = bulkImportService.importImmediately(
                new BulkImportRequest(null, List.of("contendedID"), null, null));

        assertThat(result.resolvedYoutubeIds()).contains("contendedID");
        assertThat(songRepository.findByYoutubeId("contendedID")).isNotEmpty();
    }

    @Test
    void anOnTheSpotResolvedSongIsReEnqueuedAndLaterResolvesThroughThePatientPipeline() {
        bulkImportService.importImmediately(new BulkImportRequest(null, List.of("fastTierID1"), null, null));

        List<PendingImport> reEnqueued = pendingImportRepository.findByStatusOrderByEnqueuedAtAsc(
                PendingImportStatus.PENDING, org.springframework.data.domain.Limit.of(10));
        assertThat(reEnqueued).extracting(PendingImport::getYoutubeId).contains("fastTierID1");

        int resolvedThisRun = catalogSeedingService.drainBacklog();

        assertThat(resolvedThisRun).isGreaterThanOrEqualTo(1);
        assertThat(pendingImportRepository.countByStatusAndProcessedAtAfter(
                PendingImportStatus.DONE, java.time.Instant.EPOCH)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theDrainPausesWhileOnTheSpotTrafficIsActiveAndResumesOnceItClears() {
        catalogSeedingService.enqueue(List.of("paused-id-1", "paused-id-2"));

        metadataPriorityCoordinator.beginOnTheSpotWork();
        int resolvedWhilePaused = catalogSeedingService.drainBacklog();
        assertThat(resolvedWhilePaused).isZero();
        assertThat(pendingImportRepository.countByStatus(PendingImportStatus.PENDING)).isEqualTo(2);

        metadataPriorityCoordinator.endOnTheSpotWork();
        int resolvedAfterClear = catalogSeedingService.drainBacklog();
        assertThat(resolvedAfterClear).isEqualTo(2);
    }

    private Song persistSong(String youtubeId) {
        Song song = new Song();
        song.setTitle("Song " + youtubeId);
        song.setReleaseYear(2000);
        song.setYoutubeId(youtubeId);
        return songRepository.save(song);
    }

    private AlternateYoutubeId alternateIdFor(String youtubeId, Song song) {
        AlternateYoutubeId alternateYoutubeId = new AlternateYoutubeId();
        alternateYoutubeId.setYoutubeId(youtubeId);
        alternateYoutubeId.setSong(song);
        return alternateYoutubeId;
    }
}
