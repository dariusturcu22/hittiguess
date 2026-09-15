package org.dariusturcu.backend.controller;

import tools.jackson.databind.ObjectMapper;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SubmitReportRequest;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.SongConfirmationRepository;
import org.dariusturcu.backend.repository.SongReportRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.AbuseVisibilityEvents;
import org.dariusturcu.backend.service.SongReportService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the report/confirmation submission endpoints and the admin review endpoints
 * through the full web stack against a real Postgres instance, on the minimal JPA-plus-web
 * security context SongSearchIntegrationTest uses. Authentication carries a real UserPrincipal
 * so the admin guard and the reporter identity both resolve the persisted user.
 */
@Testcontainers
@SpringBootTest(classes = SongReportEndpointIntegrationTest.WebTestConfig.class)
@AutoConfigureMockMvc
@Transactional
class SongReportEndpointIntegrationTest {

    private static final int SUGGESTED_YEAR = 1999;
    private static final int OTHER_SUGGESTED_YEAR = 1985;

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    @EnableWebSecurity
    static class WebTestConfig {

        @Bean
        AbuseVisibilityEvents abuseVisibilityEvents() {
            return new AbuseVisibilityEvents();
        }

        @Bean
        AdminAccessGuard adminAccessGuard() {
            return new AdminAccessGuard();
        }

        @Bean
        SongReportService songReportService(SongReportRepository songReportRepository,
                                            SongConfirmationRepository songConfirmationRepository,
                                            SongRepository songRepository,
                                            AbuseVisibilityEvents abuseVisibilityEvents) {
            return new SongReportService(songReportRepository, songConfirmationRepository,
                    songRepository, abuseVisibilityEvents);
        }

        @Bean
        SongReportController songReportController(SongReportService songReportService) {
            return new SongReportController(songReportService);
        }

        @Bean
        AdminSongReportController adminSongReportController(SongReportService songReportService,
                                                           AdminAccessGuard adminAccessGuard) {
            return new AdminSongReportController(songReportService, adminAccessGuard);
        }

        @Bean
        org.dariusturcu.backend.exception.GlobalExceptionHandler globalExceptionHandler() {
            return new org.dariusturcu.backend.exception.GlobalExceptionHandler();
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(exception -> exception
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .build();
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
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private SongReportRepository songReportRepository;

    @Test
    void submittingAReportEndToEndSurfacesItOnTheAdminQueue() throws Exception {
        User reporter = persistUser("reporter", Role.USER);
        User admin = persistUser("admin", Role.ADMIN);
        Song song = persistSong("Reported song", VerificationStatus.NEEDS_REVIEW);

        mockMvc.perform(post("/api/songs/{songId}/reports", song.getId())
                        .with(authentication(authFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SubmitReportRequest("Wrong year", SUGGESTED_YEAR, null))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/song-reports/queue")
                        .with(authentication(authFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].songId").value(song.getId()))
                .andExpect(jsonPath("$[0].priorityTier").value("REPORTED_NO_CONVERGENCE"))
                .andExpect(jsonPath("$[0].openReportCount").value(1));
    }

    @Test
    void aConvergentPairOutranksTwoReportsSuggestingDifferentYears() throws Exception {
        User admin = persistUser("admin", Role.ADMIN);
        Song convergent = persistSong("Convergent", VerificationStatus.NEEDS_REVIEW);
        Song split = persistSong("Split", VerificationStatus.NEEDS_REVIEW);

        report(persistUser("agree-one", Role.USER), convergent, SUGGESTED_YEAR);
        report(persistUser("agree-two", Role.USER), convergent, SUGGESTED_YEAR);
        report(persistUser("split-one", Role.USER), split, SUGGESTED_YEAR);
        report(persistUser("split-two", Role.USER), split, OTHER_SUGGESTED_YEAR);

        mockMvc.perform(get("/api/admin/song-reports/queue")
                        .with(authentication(authFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].songId").value(convergent.getId()))
                .andExpect(jsonPath("$[0].priorityTier").value("CONVERGING_REPORTS"))
                .andExpect(jsonPath("$[0].reportsConverge").value(true))
                .andExpect(jsonPath("$[0].convergingYear").value(SUGGESTED_YEAR))
                .andExpect(jsonPath("$[1].songId").value(split.getId()))
                .andExpect(jsonPath("$[1].priorityTier").value("REPORTED_NO_CONVERGENCE"))
                .andExpect(jsonPath("$[1].reportsConverge").value(false));
    }

    @Test
    void aNonAdminIsForbiddenFromTheReviewQueue() throws Exception {
        User plainUser = persistUser("plain", Role.USER);

        mockMvc.perform(get("/api/admin/song-reports/queue")
                        .with(authentication(authFor(plainUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNonAdminIsForbiddenFromUpholdingReports() throws Exception {
        User plainUser = persistUser("plain", Role.USER);
        Song song = persistSong("Song", VerificationStatus.NEEDS_REVIEW);

        mockMvc.perform(post("/api/admin/song-reports/{songId}/uphold", song.getId())
                        .with(authentication(authFor(plainUser))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminCanDismissOpenReports() throws Exception {
        User admin = persistUser("admin", Role.ADMIN);
        Song song = persistSong("Song", VerificationStatus.NEEDS_REVIEW);
        report(persistUser("reporter", Role.USER), song, SUGGESTED_YEAR);

        mockMvc.perform(post("/api/admin/song-reports/{songId}/dismiss", song.getId())
                        .with(authentication(authFor(admin))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/song-reports/queue")
                        .with(authentication(authFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void submittingAReportRequiresAuthentication() throws Exception {
        Song song = persistSong("Song", VerificationStatus.NEEDS_REVIEW);

        mockMvc.perform(post("/api/songs/{songId}/reports", song.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SubmitReportRequest("Wrong year", SUGGESTED_YEAR, null))))
                .andExpect(status().isUnauthorized());
    }

    private void report(User reporter, Song song, int suggestedYear) throws Exception {
        mockMvc.perform(post("/api/songs/{songId}/reports", song.getId())
                        .with(authentication(authFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SubmitReportRequest("Wrong year", suggestedYear, null))))
                .andExpect(status().isCreated());
    }

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken authFor(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
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
}
