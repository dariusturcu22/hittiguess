package org.dariusturcu.backend.catalog;

import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.repository.AlternateYoutubeIdRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.dariusturcu.backend.service.SongMetadataService;
import org.dariusturcu.backend.service.SongResolutionService;
import org.dariusturcu.backend.service.SongCatalogService;
import org.dariusturcu.backend.service.YoutubeIdLookupService;
import org.dariusturcu.backend.pipeline.PipelineJobService;
import org.dariusturcu.backend.pipeline.PipelineJobRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
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

@Testcontainers
@SpringBootTest(classes = CatalogSeedingConcurrencyIntegrationTest.ConcurrencyTestConfig.class)
class CatalogSeedingConcurrencyIntegrationTest {

    private static final int CONCURRENT_CALLERS = 6;
    private static final int OVERLAPPING_ID_COUNT = 25;
    private static final long WAIT_SECONDS = 30;
    private static final String SHARED_VIDEO_ID = "sharedVideo1";
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
        SongCatalogService songCatalogService(SongRepository songRepository,
                                              AlternateYoutubeIdRepository alternateYoutubeIdRepository) {
            return new SongCatalogService(songRepository, alternateYoutubeIdRepository);
        }

        @Bean
        YoutubeIdLookupService youtubeIdLookupService(SongRepository songRepository,
                                                      AlternateYoutubeIdRepository alternateYoutubeIdRepository) {
            return new YoutubeIdLookupService(songRepository, alternateYoutubeIdRepository);
        }

        @Bean
        SongResolutionService songResolutionService(GatedMetadataResolver gatedMetadataResolver,
                                                     SongCatalogService songCatalogService) {
            return new SongResolutionService(gatedMetadataResolver, songCatalogService);
        }

        @Bean
        PlaylistExpansionService playlistExpansionService() {
            return new PlaylistExpansionService(null);
        }

        @Bean
        PipelineJobService pipelineJobService(JdbcTemplate jdbcTemplate,
                                              PlatformTransactionManager transactionManager) {
            return new PipelineJobService(new PipelineJobRepository(jdbcTemplate), transactionManager,
                    PipelineJobService.DEFAULT_MAX_ATTEMPTS);
        }

        @Bean
        CatalogSeedingService catalogSeedingService(PendingImportRepository pendingImportRepository,
                                                    YoutubeIdLookupService youtubeIdLookupService,
                                                    PlaylistExpansionService playlistExpansionService,
                                                    SongRepository songRepository,
                                                    PipelineJobService pipelineJobService) {
            return new CatalogSeedingService(pendingImportRepository, youtubeIdLookupService,
                    playlistExpansionService, songRepository, pipelineJobService);
        }
    }

    /** Resolves deterministically after every concurrent caller reaches the barrier. */
    static class GatedMetadataResolver extends SongMetadataService {
        volatile CyclicBarrier arrivalBarrier;

        GatedMetadataResolver() {
            super(null, null);
        }

        @Override
        public AiResponse resolveByYoutubeId(String youtubeId) {
            try {
                CyclicBarrier barrier = arrivalBarrier;
                if (barrier != null) {
                    barrier.await(WAIT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (Exception interruptedOrBroken) {
                throw new IllegalStateException(interruptedOrBroken);
            }
            SongMetadataResponse content = new SongMetadataResponse(
                    "Title for " + youtubeId, List.of("Artist for " + youtubeId), List.of(), PATIENT_YEAR,
                    "111111", "high", "musicbrainz", "stubbed", "NEEDS_REVIEW", null, null, null);
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
    }

    @BeforeAll
    static void migrate() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
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
        jdbcTemplate.execute("TRUNCATE pipeline_runs, import_batches CASCADE");
        pendingImportRepository.deleteAll();
        songRepository.deleteAll();
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

        List<String> queuedIds = jdbcTemplate.queryForList("SELECT youtube_id FROM pipeline_runs", String.class);
        Set<String> distinctIds = queuedIds.stream().collect(Collectors.toSet());
        assertThat(queuedIds).hasSameSizeAs(distinctIds);
        assertThat(queuedIds).containsExactlyInAnyOrderElementsOf(overlappingIds);
    }

    private <Result> void runConcurrently(int callerCount, Callable<Result> task) throws Exception {
        ExecutorService callers = Executors.newFixedThreadPool(callerCount);
        CountDownLatch startTogether = new CountDownLatch(1);
        try {
            List<Future<Result>> results = new ArrayList<>();
            for (int index = 0; index < callerCount; index++) {
                results.add(callers.submit(() -> {
                    startTogether.await();
                    return task.call();
                }));
            }
            startTogether.countDown();
            for (Future<Result> result : results) {
                result.get(WAIT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            callers.shutdownNow();
        }
    }
}
