package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.service.SongService;
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

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Song search is catalog-wide rather than playlist-scoped, so the only access
 * control it needs is plain authentication, not any per-playlist membership
 * check. Uses a minimal JPA-plus-web security context rather than the whole
 * BackendApplication, for the same reason SongApiCompatibilityAfterMigrationTest
 * does: this sandbox's JDK can't construct the java.net.http.HttpClient the
 * real app's OAuth2 client and AI-service RestClient beans build.
 */
@Testcontainers
@SpringBootTest(classes = SongSearchIntegrationTest.WebTestConfig.class)
@AutoConfigureMockMvc
@Transactional
class SongSearchIntegrationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    @EnableWebSecurity
    static class WebTestConfig {
        @Bean
        SongMapper songMapper() {
            return new SongMapper();
        }

        @Bean
        SongService songService(SongRepository songRepository, SongMapper songMapper) {
            return new SongService(songRepository, songMapper);
        }

        @Bean
        SongController songController(SongService songService) {
            return new SongController(songService);
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
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
    }

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private SongRepository songRepository;

    private static final String SONG_OWNER_USERNAME_PREFIX = "song-owner";
    private static final int SOLE_ARTIST_DISPLAY_ORDER = 0;

    // The searching user is never a member of this playlist, only authenticated: the
    // search endpoint is catalog-wide, not gated by playlist membership like PlaylistService's
    // other operations are.
    private Song addSongToANewPlaylistTheSearchingUserIsNotAMemberOf(String title, String artistName, String youtubeId) {
        User owner = new User();
        owner.setUsername(SONG_OWNER_USERNAME_PREFIX + "-" + youtubeId);
        owner.setRole(Role.USER);
        owner = userRepository.save(owner);

        Playlist playlist = new Playlist();
        playlist.setInviteCode("INV-" + youtubeId);
        playlist = playlistRepository.save(playlist);

        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(2000);
        song.setYoutubeId(youtubeId);
        song.setAddedBy(owner);

        SongArtist artist = new SongArtist();
        artist.setSong(song);
        artist.setName(artistName);
        artist.setRole(ArtistRole.MAIN);
        artist.setDisplayOrder(SOLE_ARTIST_DISPLAY_ORDER);
        song.setArtists(List.of(artist));

        Song savedSong = songRepository.save(song);
        playlist.addSong(savedSong);
        playlistRepository.save(playlist);

        return savedSong;
    }

    @Test
    void authenticatedUserCanSearchTheWholeCatalogByKeywordAcrossPlaylistsTheyDoNotBelongTo() throws Exception {
        addSongToANewPlaylistTheSearchingUserIsNotAMemberOf("Bohemian Rhapsody", "Queen", "dQw4w9WgXcQ1");
        addSongToANewPlaylistTheSearchingUserIsNotAMemberOf("Some Other Song", "Some Other Artist", "dQw4w9WgXcQ2");

        mockMvc.perform(get("/api/songs/search")
                        .param("query", "bohemian")
                        .with(user("searching-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Bohemian Rhapsody"));
    }

    @Test
    void authenticatedUserCanSearchTheWholeCatalogByYoutubeLink() throws Exception {
        addSongToANewPlaylistTheSearchingUserIsNotAMemberOf("Bohemian Rhapsody", "Queen", "dQw4w9WgXcQ");

        mockMvc.perform(get("/api/songs/search")
                        .param("query", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
                        .with(user("searching-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].youtubeId").value("dQw4w9WgXcQ"));
    }

    @Test
    void unauthenticatedSearchRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/songs/search").param("query", "bohemian"))
                .andExpect(status().isUnauthorized());
    }
}
