package org.dariusturcu.backend.catalog;

import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.repository.AlternateYoutubeIdRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.dariusturcu.backend.service.MetadataPriorityCoordinator;
import org.dariusturcu.backend.service.PendingImportProcessor;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.dariusturcu.backend.service.SongMetadataService;
import org.dariusturcu.backend.service.SongResolutionService;
import org.dariusturcu.backend.service.YoutubeIdLookupService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Probes the admin seeding path for concurrency problems against a real Postgres: whether a
 * song is committed as soon as its own item finishes, and whether simultaneous work on the
 * same YouTube ID can create duplicate rows.
 */
@Testcontainers
@SpringBootTest(classes = CatalogSeedingConcurrencyIntegrationTest.ConcurrencyTestConfig.class)
class CatalogSeedingConcurrencyIntegrationTest {

    private static final long DAILY_DRAIN_QUOTA = 100;
    private static final int CONCURRENT_CALLERS = 6;
    private static final int OVERLAPPING_ID_COUNT = 25;
    private static final long WAIT_SECONDS = 30;
    private static final String SHARED_VIDEO_ID = "sharedVideo1";
    private static final String FIRST_PLAYLIST_VIDEO_ID = "playlistFirst";
    private static final String SECOND_PLAYLIST_VIDEO_ID = "playlistSecond";
    private static final int PATIENT_YEAR = 1999;

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class ConcurrencyTestConfig {

        @Bean
        GatedMetadataResolver gatedMetadataResolver() {
            return new GatedMetadataResolver();
        }

        @Bean
        YoutubeIdLookupService youtubeIdLookupService(SongRepository songRepository,
                                                      AlternateYoutubeIdRepository alternateYoutubeIdRepository) {
            return new YoutubeIdLookupService(songRepository, alternateYoutubeIdRepository);
        }

        @Bean
        SongResolutionService songResolutionService(GatedMetadataResolver gatedMetadataResolver,
                                                     SongRepository songRepository) {
            return new SongResolutionService(gatedMetadataResolver, songRepository);
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
            return new PlaylistExpansionService(null);
        }

        @Bean
        CatalogSeedingService catalogSeedingService(PendingImportRepository pendingImportRepository,
                                                    YoutubeIdLookupService youtubeIdLookupService,
                                                    PendingImportProcessor pendingImportProcessor,
                                                    MetadataPriorityCoordinator metadataPriorityCoordinator,
                                                    PlaylistExpansionService playlistExpansionService,
                                                    SongRepository songRepository,
                                                    @Value("${catalog.seeding.daily-drain-quota}") long dailyDrainQuota) {
            return new CatalogSeedingService(pendingImportRepository, youtubeIdLookupService,
                    pendingImportProcessor, metadataPriorityCoordinator, playlistExpansionService, songRepository,
                    new SyncTaskExecutor(), dailyDrainQuota);
        }
    }

    /** Resolves deterministically; can hold callers at a barrier or block one ID until released. */
    static class GatedMetadataResolver extends SongMetadataService {
        volatile CyclicBarrier arrivalBarrier;
        volatile String blockedYoutubeId;
        final CountDownLatch blockedItemReached = new CountDownLatch(1);
        final CountDownLatch blockedItemMayFinish = new CountDownLatch(1);

        GatedMetadataResolver() {
            super(null);
        }

        @Override
        public AiResponse resolveByYoutubeId(String youtubeId) {
            try {
                CyclicBarrier barrier = arrivalBarrier;
                if (barrier != null) {
                    barrier.await(WAIT_SECONDS, TimeUnit.SECONDS);
                }
                if (youtubeId.equals(blockedYoutubeId)) {
                    blockedItemReached.countDown();
                    blockedItemMayFinish.await(WAIT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (Exception interruptedOrBroken) {
                throw new IllegalStateException(interruptedOrBroken);
            }
            SongMetadataResponse content = new SongMetadataResponse(
                    "Title for " + youtubeId, List.of("Artist for " + youtubeId), List.of(), PATIENT_YEAR,
                    "111111", "high", "musicbrainz", "stubbed", "NEEDS_REVIEW", null, null);
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
    private SongResolutionService songResolutionService;
    @Autowired
    private PendingImportRepository pendingImportRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private GatedMetadataResolver gatedMetadataResolver;

    @AfterEach
    void clean() {
        gatedMetadataResolver.arrivalBarrier = null;
        gatedMetadataResolver.blockedYoutubeId = null;
        pendingImportRepository.deleteAll();
        songRepository.deleteAll();
    }

    @Test
    void aSongIsCommittedBeforeTheRestOfTheBacklogFinishes() throws Exception {
        catalogSeedingService.enqueue(List.of(FIRST_PLAYLIST_VIDEO_ID, SECOND_PLAYLIST_VIDEO_ID));
        gatedMetadataResolver.blockedYoutubeId = SECOND_PLAYLIST_VIDEO_ID;

        ExecutorService drainThread = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> drain = drainThread.submit(() -> catalogSeedingService.drainBacklog());
            assertThat(gatedMetadataResolver.blockedItemReached.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            // The second song is still being resolved; the first must already be visible to
            // every other connection, and the second must not be.
            assertThat(songRepository.findByYoutubeId(FIRST_PLAYLIST_VIDEO_ID)).hasSize(1);
            assertThat(songRepository.findByYoutubeId(SECOND_PLAYLIST_VIDEO_ID)).isEmpty();
            assertThat(pendingImportRepository.countByStatus(PendingImportStatus.DONE)).isEqualTo(1);

            gatedMetadataResolver.blockedItemMayFinish.countDown();
            assertThat(drain.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(2);
        } finally {
            gatedMetadataResolver.blockedItemMayFinish.countDown();
            drainThread.shutdownNow();
        }
    }

    @Test
    void simultaneousResolutionsOfOneVideoLeaveOneSongRow() throws Exception {
        gatedMetadataResolver.arrivalBarrier = new CyclicBarrier(CONCURRENT_CALLERS);

        runConcurrently(CONCURRENT_CALLERS, () -> songResolutionService.resolveAndPersist(SHARED_VIDEO_ID, null));

        assertThat(songRepository.findByYoutubeId(SHARED_VIDEO_ID)).hasSize(1);
    }

    @Test
    void overlappingEnqueuesQueueEachVideoOnce() throws Exception {
        List<String> overlappingIds = IntStream.range(0, OVERLAPPING_ID_COUNT).mapToObj(index -> "overlap" + index).toList();

        runConcurrently(CONCURRENT_CALLERS, () -> catalogSeedingService.enqueue(overlappingIds));

        List<String> queuedIds = pendingImportRepository.findAll().stream().map(PendingImport::getYoutubeId).toList();
        Set<String> distinctIds = queuedIds.stream().collect(Collectors.toSet());
        assertThat(queuedIds).hasSameSizeAs(distinctIds);
    }

    private <T> void runConcurrently(int callerCount, Callable<T> task) throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(callerCount);
        CountDownLatch startTogether = new CountDownLatch(1);
        try {
            List<Future<T>> results = new ArrayList<>();
            for (int index = 0; index < callerCount; index++) {
                results.add(callers.submit(() -> {
                    startTogether.await();
                    return task.call();
                }));
            }
            startTogether.countDown();
            for (Future<T> result : results) {
                try {
                    result.get(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException failedCaller) {
                    // A failed caller is itself a finding; the assertions below decide the verdict.
                }
            }
        } finally {
            callers.shutdownNow();
        }
    }
}
