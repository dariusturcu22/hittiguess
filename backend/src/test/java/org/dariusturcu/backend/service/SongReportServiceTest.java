package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.song.AdminReviewItemDTO;
import org.dariusturcu.backend.model.song.ReviewPriorityTier;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongReportStatus;
import org.dariusturcu.backend.model.song.SubmitReportRequest;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.SongConfirmationRepository;
import org.dariusturcu.backend.repository.SongReportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the report and confirmation service against a real Postgres instance with a
 * recording stand-in for the story-34 abuse-visibility event, on the minimal JPA-only
 * context, the same slice CatalogSeedingIntegrationTest uses.
 */
@Testcontainers
@SpringBootTest(classes = SongReportServiceTest.ReportTestConfig.class)
class SongReportServiceTest {

    private static final int NON_FUTURE_YEAR = 1999;
    private static final int OTHER_YEAR = 1988;

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class ReportTestConfig {

        @Bean
        RecordingAbuseVisibilityEvents recordingAbuseVisibilityEvents() {
            return new RecordingAbuseVisibilityEvents();
        }

        @Bean
        SongReportService songReportService(SongReportRepository songReportRepository,
                                            SongConfirmationRepository songConfirmationRepository,
                                            SongRepository songRepository,
                                            RecordingAbuseVisibilityEvents abuseVisibilityEvents) {
            return new SongReportService(songReportRepository, songConfirmationRepository,
                    songRepository, abuseVisibilityEvents);
        }
    }

    static class RecordingAbuseVisibilityEvents extends AbuseVisibilityEvents {
        final CopyOnWriteArrayList<Long> reportedSongIds = new CopyOnWriteArrayList<>();

        @Override
        public void recordReportSubmitted(long reportingUserId, long songId) {
            reportedSongIds.add(songId);
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
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private SongReportService songReportService;
    @Autowired
    private SongReportRepository songReportRepository;
    @Autowired
    private SongConfirmationRepository songConfirmationRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RecordingAbuseVisibilityEvents recordingAbuseVisibilityEvents;

    @BeforeEach
    void resetState() {
        songReportRepository.deleteAll();
        songConfirmationRepository.deleteAll();
        songRepository.deleteAll();
        userRepository.deleteAll();
        recordingAbuseVisibilityEvents.reportedSongIds.clear();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submittingAReportPersistsItAndFiresTheStubbedEvent() {
        User reporter = persistUser("reporter", Role.USER);
        Song song = persistSong("A song", VerificationStatus.NEEDS_REVIEW);
        authenticateAs(reporter);

        songReportService.submitReport(song.getId(), reportSuggesting(NON_FUTURE_YEAR));

        assertThat(songReportRepository.countBySongIdAndStatus(song.getId(), SongReportStatus.OPEN)).isEqualTo(1);
        assertThat(recordingAbuseVisibilityEvents.reportedSongIds).containsExactly(song.getId());
    }

    @Test
    void aSecondReportOnTheSameSongByTheSameUserIsRejected() {
        User reporter = persistUser("reporter", Role.USER);
        Song song = persistSong("A song", VerificationStatus.NEEDS_REVIEW);
        authenticateAs(reporter);
        songReportService.submitReport(song.getId(), reportSuggesting(NON_FUTURE_YEAR));

        assertThatThrownBy(() -> songReportService.submitReport(song.getId(), reportSuggesting(OTHER_YEAR)))
                .isInstanceOf(ConflictException.class);
        assertThat(songReportRepository.countBySongIdAndStatus(song.getId(), SongReportStatus.OPEN)).isEqualTo(1);
    }

    @Test
    void aSecondConfirmationOnTheSameSongByTheSameUserIsRejected() {
        User user = persistUser("confirmer", Role.USER);
        Song song = persistSong("A song", VerificationStatus.NEEDS_REVIEW);
        authenticateAs(user);
        songReportService.submitConfirmation(song.getId());

        assertThatThrownBy(() -> songReportService.submitConfirmation(song.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(songConfirmationRepository.countBySongId(song.getId())).isEqualTo(1);
    }

    @Test
    void aVerifiedSongCannotBeConfirmed() {
        User user = persistUser("confirmer", Role.USER);
        Song song = persistSong("Locked song", VerificationStatus.VERIFIED);
        authenticateAs(user);

        assertThatThrownBy(() -> songReportService.submitConfirmation(song.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void threeReportsFlagTheSongForReview() {
        Song song = persistSong("Contested song", VerificationStatus.NEEDS_REVIEW);
        submitReportAs("reporter-one", song, NON_FUTURE_YEAR);
        submitReportAs("reporter-two", song, NON_FUTURE_YEAR);
        submitReportAs("reporter-three", song, OTHER_YEAR);

        assertThat(songReportRepository.countBySongIdAndStatus(song.getId(), SongReportStatus.OPEN)).isEqualTo(3);
    }

    @Test
    void convergingReportsOutrankAConfirmedUnreportedCard() {
        Song convergentSong = persistSong("Convergent", VerificationStatus.NEEDS_REVIEW);
        submitReportAs("reporter-one", convergentSong, NON_FUTURE_YEAR);
        submitReportAs("reporter-two", convergentSong, NON_FUTURE_YEAR);

        Song confirmedSong = persistSong("Confirmed only", VerificationStatus.MANUAL_ENTRY);
        confirmAs("confirmer", confirmedSong);

        List<AdminReviewItemDTO> queue = songReportService.reviewQueue();

        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).songId()).isEqualTo(convergentSong.getId());
        assertThat(queue.get(0).priorityTier()).isEqualTo(ReviewPriorityTier.CONVERGING_REPORTS);
        assertThat(queue.get(0).reportsConverge()).isTrue();
        assertThat(queue.get(0).convergingYear()).isEqualTo(NON_FUTURE_YEAR);
        assertThat(queue.get(1).priorityTier()).isEqualTo(ReviewPriorityTier.CONFIRMED_UNREPORTED);
    }

    @Test
    void aConvergentReportOnAVerifiedCardStillOutranksANonConvergentReportOnALowerCard() {
        Song verifiedConvergent = persistSong("Verified convergent", VerificationStatus.VERIFIED);
        submitReportAs("reporter-one", verifiedConvergent, NON_FUTURE_YEAR);
        submitReportAs("reporter-two", verifiedConvergent, NON_FUTURE_YEAR);

        Song nonConvergent = persistSong("Disagreeing reports", VerificationStatus.MANUAL_ENTRY);
        submitReportAs("reporter-three", nonConvergent, NON_FUTURE_YEAR);
        submitReportAs("reporter-four", nonConvergent, OTHER_YEAR);

        List<AdminReviewItemDTO> queue = songReportService.reviewQueue();

        assertThat(queue.get(0).songId()).isEqualTo(verifiedConvergent.getId());
        assertThat(queue.get(0).priorityTier()).isEqualTo(ReviewPriorityTier.CONVERGING_REPORTS);
        assertThat(queue.get(1).songId()).isEqualTo(nonConvergent.getId());
        assertThat(queue.get(1).priorityTier()).isEqualTo(ReviewPriorityTier.REPORTED_NO_CONVERGENCE);
        assertThat(queue.get(1).reportsConverge()).isFalse();
    }

    @Test
    void twoReportsSuggestingDifferentYearsDoNotConverge() {
        Song song = persistSong("Split reports", VerificationStatus.NEEDS_REVIEW);
        submitReportAs("reporter-one", song, NON_FUTURE_YEAR);
        submitReportAs("reporter-two", song, OTHER_YEAR);

        List<AdminReviewItemDTO> queue = songReportService.reviewQueue();

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).priorityTier()).isEqualTo(ReviewPriorityTier.REPORTED_NO_CONVERGENCE);
        assertThat(queue.get(0).reportsConverge()).isFalse();
        assertThat(queue.get(0).convergingYear()).isNull();
    }

    @Test
    void aVerifiedCardWithNoReportNeverEntersTheQueue() {
        persistSong("Untouched verified", VerificationStatus.VERIFIED);

        assertThat(songReportService.reviewQueue()).isEmpty();
    }

    @Test
    void upholdingReportsOnAnEditableSongMovesItToManualEntry() {
        Song song = persistSong("Editable", VerificationStatus.UNVERIFIED);
        submitReportAs("reporter-one", song, NON_FUTURE_YEAR);

        songReportService.upholdReports(song.getId());

        Song reloaded = songRepository.findById(song.getId()).orElseThrow();
        assertThat(reloaded.getVerificationStatus()).isEqualTo(VerificationStatus.MANUAL_ENTRY);
        assertThat(songReportRepository.countBySongIdAndStatus(song.getId(), SongReportStatus.UPHELD)).isEqualTo(1);
    }

    @Test
    void upholdingReportsOnALockedSongLeavesItsYearAndStatusUntouched() {
        Song song = persistSong("Locked", VerificationStatus.VERIFIED);
        int originalYear = song.getReleaseYear();
        submitReportAs("reporter-one", song, OTHER_YEAR);

        songReportService.upholdReports(song.getId());

        Song reloaded = songRepository.findById(song.getId()).orElseThrow();
        assertThat(reloaded.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(reloaded.getReleaseYear()).isEqualTo(originalYear);
        assertThat(songReportRepository.countBySongIdAndStatus(song.getId(), SongReportStatus.UPHELD)).isEqualTo(1);
    }

    @Test
    void dismissingReportsClearsThemFromTheOpenSet() {
        Song song = persistSong("Dismissed", VerificationStatus.NEEDS_REVIEW);
        submitReportAs("reporter-one", song, NON_FUTURE_YEAR);

        songReportService.dismissReports(song.getId());

        assertThat(songReportRepository.countBySongIdAndStatus(song.getId(), SongReportStatus.OPEN)).isZero();
        assertThat(songReportRepository.countBySongIdAndStatus(song.getId(), SongReportStatus.DISMISSED)).isEqualTo(1);
        assertThatCode(() -> songReportService.reviewQueue()).doesNotThrowAnyException();
        assertThat(songReportService.reviewQueue()).isEmpty();
    }

    private SubmitReportRequest reportSuggesting(int year) {
        return new SubmitReportRequest("The release year looks wrong", year, "https://musicbrainz.org/example");
    }

    private void submitReportAs(String username, Song song, int suggestedYear) {
        User reporter = persistUser(username, Role.USER);
        authenticateAs(reporter);
        songReportService.submitReport(song.getId(), reportSuggesting(suggestedYear));
        SecurityContextHolder.clearContext();
    }

    private void confirmAs(String username, Song song) {
        User user = persistUser(username, Role.USER);
        authenticateAs(user);
        songReportService.submitConfirmation(song.getId());
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setRole(role);
        return userRepository.save(user);
    }

    private Song persistSong(String title, VerificationStatus verificationStatus) {
        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(2000);
        song.setYoutubeId("yt-" + title.replaceAll("\\s+", "-"));
        song.setVerificationStatus(verificationStatus);
        return songRepository.save(song);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null,
                        new UserPrincipal(user).getAuthorities()));
    }
}
