package org.dariusturcu.backend.catalog;

import org.dariusturcu.backend.exception.PlaylistImportException;
import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.model.ai.AiFastDateResponse;
import org.dariusturcu.backend.model.ai.AiIdentifiedSong;
import org.dariusturcu.backend.model.ai.AiIdentifyResponse;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.importquota.ImportQuotaUsage;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItem;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobItemStatus;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobStatus;
import org.dariusturcu.backend.model.playlist.StartPlaylistImportRequest;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.AlternateYoutubeId;
import org.dariusturcu.backend.model.song.BulkImportRequest;
import org.dariusturcu.backend.model.song.BulkImportResultDTO;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.model.song.PendingImport;
import org.dariusturcu.backend.model.song.PendingImportOrigin;
import org.dariusturcu.backend.model.song.PendingImportStatus;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.AlternateYoutubeIdRepository;
import org.dariusturcu.backend.repository.ImportQuotaUsageRepository;
import org.dariusturcu.backend.repository.PlaylistImportJobItemRepository;
import org.dariusturcu.backend.repository.PlaylistImportJobRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.PendingImportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.BulkImportService;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.dariusturcu.backend.service.ImportQuotaService;
import org.dariusturcu.backend.service.FastTierImportRunner;
import org.dariusturcu.backend.service.PlaylistAccessService;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.dariusturcu.backend.service.PlaylistImportJobService;
import org.dariusturcu.backend.service.PlaylistImportService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = CatalogSeedingIntegrationTest.CatalogTestConfig.class)
class CatalogSeedingIntegrationTest {

    private static final int FAST_TIER_YEAR = 1998;
    private static final int PARALLEL_IMPORT_SONG_COUNT = 12;
    private static final String KNOWN_IMPORT_VIDEO_ID = "knownImport";
    private static final long IMPORT_WAIT_MILLISECONDS = 30_000;
    private static final long IMPORT_POLL_MILLISECONDS = 100;
    private static final int PATIENT_YEAR = 1999;
    private static final String SUBMITTING_USER_USERNAME = "bulk-import-submitter";
    private static final String SUBMITTING_USER_EMAIL = "bulk-import-submitter@integration.test";
    private static final String PLAYLIST_OWNER_USERNAME = "playlist-owner";
    private static final String PLAYLIST_OWNER_EMAIL = "playlist-owner@integration.test";
    private static final String NEW_VIDEO_ID = "newVideoId1";
    private static final String SECOND_NEW_VIDEO_ID = "newVideoId2";
    private static final int SONGS_LEFT_IN_TODAYS_QUOTA = 1;
    private static final int ONE_NEW_SONG = 1;

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
        SongResolutionService songResolutionService(StubMetadataResolver stubMetadataResolver,
                                                     SongCatalogService songCatalogService) {
            return new SongResolutionService(stubMetadataResolver, songCatalogService);
        }

        @Bean
        PlaylistExpansionService playlistExpansionService() {
            // No test here submits a playlist link, so the AI-service RestClient this
            // service would call is never actually invoked.
            return new PlaylistExpansionService(null);
        }

        @Bean
        PlaylistImportService playlistImportService(PlaylistRepository playlistRepository,
                                                    PlaylistMembershipRepository playlistMembershipRepository,
                                                    SongRepository songRepository) {
            return new PlaylistImportService(playlistRepository,
                    new PlaylistAccessService(playlistMembershipRepository), songRepository);
        }

        @Bean
        ImportQuotaService importQuotaService(ImportQuotaUsageRepository importQuotaUsageRepository) {
            return new ImportQuotaService(importQuotaUsageRepository);
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

        @Bean
        FastTierImportRunner fastTierImportRunner(StubMetadataResolver stubMetadataResolver,
                                                  SongResolutionService songResolutionService,
                                                  CatalogSeedingService catalogSeedingService) {
            return new FastTierImportRunner(stubMetadataResolver, songResolutionService, catalogSeedingService,
                    new SimpleAsyncTaskExecutor(), new SimpleAsyncTaskExecutor());
        }

        @Bean
        PlaylistImportJobService playlistImportJobService(PlaylistImportJobRepository jobRepository,
                                                          PlaylistImportJobItemRepository itemRepository,
                                                          PlaylistRepository playlistRepository,
                                                          SongRepository songRepository,
                                                          UserRepository userRepository,
                                                          PlaylistMembershipRepository playlistMembershipRepository,
                                                          YoutubeIdLookupService youtubeIdLookupService,
                                                          FastTierImportRunner fastTierImportRunner,
                                                          PlaylistExpansionService playlistExpansionService,
                                                          PlaylistImportService playlistImportService,
                                                          ImportQuotaService importQuotaService,
                                                          ApplicationEventPublisher applicationEventPublisher) {
            return new PlaylistImportJobService(jobRepository, itemRepository, playlistRepository, songRepository,
                    userRepository, new PlaylistAccessService(playlistMembershipRepository), youtubeIdLookupService,
                    fastTierImportRunner, playlistExpansionService, playlistImportService, importQuotaService,
                    applicationEventPublisher, new SimpleAsyncTaskExecutor());
        }

        @Bean
        BulkImportService bulkImportService(YoutubeIdLookupService youtubeIdLookupService,
                                            FastTierImportRunner fastTierImportRunner,
                                            PlaylistExpansionService playlistExpansionService,
                                            PlaylistImportService playlistImportService,
                                            ImportQuotaService importQuotaService,
                                            ApplicationEventPublisher applicationEventPublisher) {
            return new BulkImportService(youtubeIdLookupService, fastTierImportRunner, playlistExpansionService,
                    playlistImportService, importQuotaService, applicationEventPublisher);
        }
    }

    static class StubMetadataResolver extends SongMetadataService {
        final CopyOnWriteArrayList<String> resolutionOrder = new CopyOnWriteArrayList<>();
        final Set<String> unresolvableYoutubeIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

        StubMetadataResolver() {
            super(null, null);
        }

        @Override
        public AiResponse resolveByYoutubeId(String youtubeId) {
            resolutionOrder.add(youtubeId);
            if (unresolvableYoutubeIds.contains(youtubeId)) {
                return new AiResponse(null, "stub-model", 0, LocalDateTime.now(), "ERROR", null, null);
            }
            SongMetadataResponse content = new SongMetadataResponse(
                    "Title for " + youtubeId, List.of("Artist for " + youtubeId), List.of(), PATIENT_YEAR,
                    "111111", "high", "musicbrainz", "stubbed", "NEEDS_REVIEW", null, null, null);
            return new AiResponse(content, "stub-model", 0, LocalDateTime.now(), "SUCCESS", null, null);
        }

        @Override
        public Optional<AiIdentifyResponse> identifyByYoutubeId(String youtubeId) {
            resolutionOrder.add(youtubeId);
            if (unresolvableYoutubeIds.contains(youtubeId)) {
                return Optional.of(new AiIdentifyResponse("ERROR", "stub-model", null, null, null, null));
            }
            AiIdentifiedSong identified = new AiIdentifiedSong(
                    "Title for " + youtubeId, List.of("Artist for " + youtubeId), List.of(), "111111", null);
            return Optional.of(new AiIdentifyResponse("SUCCESS", "stub-model", identified, null, null, null));
        }

        @Override
        public Optional<AiFastDateResponse> dateFast(String title, List<String> mainArtists) {
            return Optional.of(new AiFastDateResponse(FAST_TIER_YEAR, "low", "fast-tier-musicbrainz", "musicbrainz"));
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
    private BulkImportService bulkImportService;
    @Autowired
    private YoutubeIdLookupService youtubeIdLookupService;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AlternateYoutubeIdRepository alternateYoutubeIdRepository;
    @Autowired
    private PendingImportRepository pendingImportRepository;
    @Autowired
    private StubMetadataResolver stubMetadataResolver;
    @Autowired
    private ImportQuotaUsageRepository importQuotaUsageRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistImportJobService playlistImportJobService;
    @Autowired
    private PlaylistImportJobRepository importJobRepository;
    @Autowired
    private PlaylistImportJobItemRepository importJobItemRepository;

    private User submittingUser;

    @BeforeEach
    void resetState() {
        importJobItemRepository.deleteAll();
        importJobRepository.deleteAll();
        playlistRepository.deleteAll();
        jdbcTemplate.execute("TRUNCATE pipeline_runs, import_batches CASCADE");
        pendingImportRepository.deleteAll();
        importQuotaUsageRepository.deleteAll();
        alternateYoutubeIdRepository.deleteAll();
        songRepository.deleteAll();
        stubMetadataResolver.resolutionOrder.clear();
        stubMetadataResolver.unresolvableYoutubeIds.clear();

        // Persisted, not transient: an on-the-spot import records the submitting user as
        // each resolved song's addedBy, which has to reference a saved row.
        submittingUser = userRepository.findUserByEmail(SUBMITTING_USER_EMAIL).orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername(SUBMITTING_USER_USERNAME);
            newUser.setEmail(SUBMITTING_USER_EMAIL);
            newUser.setRole(Role.USER);
            return userRepository.save(newUser);
        });
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
        assertThat(jdbcTemplate.queryForList("SELECT youtube_id FROM pipeline_runs", String.class))
                .containsExactlyInAnyOrder("new-id-one", "new-id-two");
    }

    @Test
    void enqueueSkipsIdsAlreadySittingInTheBacklog() {
        catalogSeedingService.enqueue(List.of("backlog-id"));

        EnqueueResultDTO secondEnqueue = catalogSeedingService.enqueue(List.of("backlog-id", "another-new-id"));

        assertThat(secondEnqueue.skippedAlreadyQueuedYoutubeIds()).containsExactly("backlog-id");
        assertThat(secondEnqueue.enqueuedYoutubeIds()).containsExactly("another-new-id");
    }

    @Test
    void anOnTheSpotRequestResolvesEvenWhenTheSameIdIsInTheAdminBacklog() {
        catalogSeedingService.enqueue(List.of("contendedID"));
        assertThat(jdbcTemplate.queryForList("SELECT youtube_id FROM pipeline_runs", String.class))
                .containsExactly("contendedID");

        BulkImportResultDTO result = bulkImportService.importImmediately(
                new BulkImportRequest(null, List.of("contendedID"), null, null));

        assertThat(result.resolvedYoutubeIds()).contains("contendedID");
        assertThat(songRepository.findByYoutubeId("contendedID")).isNotEmpty();
    }

    @Test
    void aFastTierAnswerQueuesAStageRecheckAndKeepsItsProvisionalHistory() {
        bulkImportService.importImmediately(new BulkImportRequest(null, List.of("fastTierID1"), null, null));

        assertThat(songRepository.findByYoutubeId("fastTierID1").getFirst().getReleaseYear()).isEqualTo(FAST_TIER_YEAR);
        List<PendingImport> queued = pendingImportRepository.findByYoutubeIdOrderByEnqueuedAtAscIdAsc("fastTierID1");
        assertThat(queued).singleElement().satisfies(recheck -> {
            assertThat(recheck.getYoutubeId()).isEqualTo("fastTierID1");
            assertThat(recheck.getOrigin()).isEqualTo(PendingImportOrigin.FAST_TIER_RECHECK);
            assertThat(recheck.getStatus()).isEqualTo(PendingImportStatus.PENDING);
            assertThat(recheck.getProvisionalYear()).isEqualTo(FAST_TIER_YEAR);
        });

        assertThat(jdbcTemplate.queryForList(
                "SELECT provisional_year FROM pipeline_runs WHERE youtube_id = 'fastTierID1'", Integer.class))
                .containsExactly(FAST_TIER_YEAR);
        assertThat(jdbcTemplate.queryForList(
                "SELECT lane FROM pipeline_runs WHERE youtube_id = 'fastTierID1'", String.class))
                .containsExactly(org.dariusturcu.backend.pipeline.PipelineLane.USER_IMPORT.name());
    }

    @Test
    void aBackgroundPlaylistImportLinksEveryResolvedSongAndSettlesEveryItem() throws InterruptedException {
        Playlist playlist = new Playlist();
        playlist.setName("Fast tier import target");
        playlist.setInviteCode(UUID.randomUUID().toString());
        playlist.setOwner(submittingUser);
        Long playlistId = playlistRepository.save(playlist).getId();
        Song knownSong = persistSong(KNOWN_IMPORT_VIDEO_ID);
        List<String> newVideoIds = IntStream.rangeClosed(1, PARALLEL_IMPORT_SONG_COUNT)
                .mapToObj(position -> String.format("parallel%03d", position))
                .toList();
        String unresolvableVideoId = newVideoIds.getFirst();
        stubMetadataResolver.unresolvableYoutubeIds.add(unresolvableVideoId);
        List<String> submittedIds = new java.util.ArrayList<>(newVideoIds);
        submittedIds.add(KNOWN_IMPORT_VIDEO_ID);

        String jobId = playlistImportJobService.startImport(playlistId, new StartPlaylistImportRequest(submittedIds, null));
        long deadline = System.currentTimeMillis() + IMPORT_WAIT_MILLISECONDS;
        while (importJobRepository.findById(jobId).orElseThrow().getStatus() == PlaylistImportJobStatus.RUNNING
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(IMPORT_POLL_MILLISECONDS);
        }

        assertThat(importJobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(PlaylistImportJobStatus.DONE);
        Map<String, PlaylistImportJobItemStatus> itemStatuses = importJobItemRepository.findByJobIdOrderByIdAsc(jobId).stream()
                .collect(java.util.stream.Collectors.toMap(PlaylistImportJobItem::getYoutubeId, PlaylistImportJobItem::getStatus));
        assertThat(itemStatuses).containsEntry(KNOWN_IMPORT_VIDEO_ID, PlaylistImportJobItemStatus.ALREADY_KNOWN);
        assertThat(itemStatuses).containsEntry(unresolvableVideoId, PlaylistImportJobItemStatus.UNRESOLVED);
        assertThat(itemStatuses.values()).filteredOn(status -> status == PlaylistImportJobItemStatus.RESOLVED)
                .hasSize(PARALLEL_IMPORT_SONG_COUNT - 1);
        // Songs resolved on several threads at once are each linked, none lost to another's write.
        for (String youtubeId : newVideoIds.subList(1, newVideoIds.size())) {
            Song song = songRepository.findByYoutubeId(youtubeId).getFirst();
            assertThat(songRepository.existsByIdAndPlaylistsId(song.getId(), playlistId)).as(youtubeId).isTrue();
        }
        assertThat(songRepository.existsByIdAndPlaylistsId(knownSong.getId(), playlistId)).isTrue();
        assertThat(pendingImportRepository.findAll())
                .hasSize(PARALLEL_IMPORT_SONG_COUNT - 1)
                .allSatisfy(recheck -> assertThat(recheck.getOrigin()).isEqualTo(PendingImportOrigin.FAST_TIER_RECHECK));
    }

    @Test
    void anAdminSeedIsNotListedAmongTheRechecks() {
        catalogSeedingService.enqueue(List.of("seededID01"));

        assertThat(pendingImportRepository.findAll()).isEmpty();
        assertThat(jdbcTemplate.queryForList("SELECT youtube_id FROM pipeline_runs", String.class))
                .containsExactly("seededID01");
    }

    @Test
    void anImportLargerThanTheCapIsRefusedBeforeAnyLookup() {
        List<String> oversizedSubmission = IntStream.rangeClosed(1, ImportQuotaService.MAX_SONGS_PER_IMPORT + 1)
                .mapToObj(position -> String.format("capVideo%03d", position))
                .toList();

        assertThatThrownBy(() -> bulkImportService.importImmediately(
                new BulkImportRequest(null, oversizedSubmission, null, null)))
                .isInstanceOf(PlaylistImportException.class);
        assertThat(stubMetadataResolver.resolutionOrder).isEmpty();
    }

    @Test
    void anImportNeedingMoreNewSongsThanTodaysQuotaLeavesIsRefusedBeforeAnyLookup() {
        ImportQuotaUsage usage = new ImportQuotaUsage();
        usage.setUserId(submittingUser.getId());
        usage.setUsageDate(LocalDate.now(ZoneOffset.UTC));
        usage.setResolutionCount(ImportQuotaService.DAILY_NEW_SONG_LIMIT - SONGS_LEFT_IN_TODAYS_QUOTA);
        importQuotaUsageRepository.save(usage);

        assertThatThrownBy(() -> bulkImportService.importImmediately(
                new BulkImportRequest(null, List.of(NEW_VIDEO_ID, SECOND_NEW_VIDEO_ID), null, null)))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("only " + SONGS_LEFT_IN_TODAYS_QUOTA + " more");
        assertThat(stubMetadataResolver.resolutionOrder).isEmpty();
    }

    @Test
    void songsAlreadyInTheCatalogDontCountAgainstTheQuota() {
        persistSong(NEW_VIDEO_ID);

        bulkImportService.importImmediately(new BulkImportRequest(null, List.of(NEW_VIDEO_ID, SECOND_NEW_VIDEO_ID), null, null));

        assertThat(importQuotaUsageRepository.findResolutionCount(submittingUser.getId(), LocalDate.now(ZoneOffset.UTC)))
                .contains(ONE_NEW_SONG);
    }

    @Test
    void anImportIntoAPlaylistTheUserCantWriteIsRefusedBeforeAnyLookup() {
        User playlistOwner = userRepository.findUserByEmail(PLAYLIST_OWNER_EMAIL).orElseGet(() -> {
            User newUser = new User();
            newUser.setUsername(PLAYLIST_OWNER_USERNAME);
            newUser.setEmail(PLAYLIST_OWNER_EMAIL);
            newUser.setRole(Role.USER);
            return userRepository.save(newUser);
        });
        Playlist someoneElsesPlaylist = new Playlist();
        someoneElsesPlaylist.setName("Someone else's playlist");
        someoneElsesPlaylist.setInviteCode(UUID.randomUUID().toString());
        someoneElsesPlaylist.setOwner(playlistOwner);
        Long playlistId = playlistRepository.save(someoneElsesPlaylist).getId();

        assertThatThrownBy(() -> bulkImportService.importImmediately(
                new BulkImportRequest(null, List.of(NEW_VIDEO_ID), playlistId, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(stubMetadataResolver.resolutionOrder).isEmpty();
        assertThat(importQuotaUsageRepository.findResolutionCount(submittingUser.getId(), LocalDate.now(ZoneOffset.UTC)))
                .isEmpty();
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
