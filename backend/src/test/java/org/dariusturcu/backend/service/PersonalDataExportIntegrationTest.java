package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.mapper.UserMapper;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.PersonalDataExportDTO;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the GDPR personal-data export endpoint's backing service against a real Postgres
 * database: the returned payload carries the requesting user's own account fields, every
 * playlist they belong to with its membership details, and every song they've submitted.
 *
 * Uses a minimal JPA-only context (JpaTestConfig below), the same reasoning as
 * PlaylistMembershipLifecycleIntegrationTest: this project's OAuth2 client and AI-service
 * RestClient beans both build a java.net.http.HttpClient, which this sandbox's JDK can't
 * construct.
 */
@Testcontainers
@SpringBootTest(classes = PersonalDataExportIntegrationTest.JpaTestConfig.class)
class PersonalDataExportIntegrationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class JpaTestConfig {
        @Bean
        SongMapper songMapper() {
            return new SongMapper();
        }

        @Bean
        PlaylistMapper playlistMapper(SongMapper songMapper) {
            return new PlaylistMapper(songMapper);
        }

        @Bean
        UserMapper userMapper() {
            return new UserMapper();
        }

        @Bean
        PlaylistAccessService playlistAccessService(PlaylistMembershipRepository playlistMembershipRepository) {
            return new PlaylistAccessService(playlistMembershipRepository);
        }

        @Bean
        PlaylistService playlistService(
                PlaylistRepository playlistRepository,
                SongRepository songRepository,
                PlaylistMapper playlistMapper,
                SongMapper songMapper,
                PlaylistAccessService playlistAccessService,
                PlaylistMembershipRepository playlistMembershipRepository,
                PlaylistBanRepository playlistBanRepository) {
            return new PlaylistService(playlistRepository, songRepository, playlistMapper, songMapper,
                    playlistAccessService, playlistMembershipRepository, playlistBanRepository);
        }

        @Bean
        UserService userService(
                UserRepository userRepository,
                PlaylistRepository playlistRepository,
                UserMapper userMapper,
                PlaylistMapper playlistMapper,
                PlaylistMembershipRepository playlistMembershipRepository,
                PlaylistBanRepository playlistBanRepository,
                SongRepository songRepository) {
            return new UserService(userRepository, playlistRepository, userMapper, playlistMapper,
                    playlistMembershipRepository, playlistBanRepository, songRepository);
        }

        @Bean
        PersonalDataExportService personalDataExportService(
                UserRepository userRepository,
                PlaylistMembershipRepository playlistMembershipRepository,
                SongRepository songRepository,
                SongMapper songMapper) {
            return new PersonalDataExportService(userRepository, playlistMembershipRepository, songRepository, songMapper);
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
    private UserRepository userRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private PlaylistService playlistService;
    @Autowired
    private PersonalDataExportService personalDataExportService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private CreateSongRequest anySongRequest(String title) {
        return new CreateSongRequest(
                "Artist",
                title,
                2000,
                "dQw4w9WgXcQ",
                "abcdef",
                "abcdef",
                null);
    }

    @Test
    void exportReturnsTheAccountEveryPlaylistMembershipAndEverySubmittedSong() {
        User exportingUser = persistUser("exporter-" + UUID.randomUUID());
        actAs(exportingUser);

        PlaylistDetailDTO ownedPlaylist = userService.createPlaylist();
        SongDTO submittedSong = playlistService.createSong(ownedPlaylist.id(), anySongRequest("Exported Title"));

        PersonalDataExportDTO export = personalDataExportService.exportCurrentUser();

        assertThat(export.id()).isEqualTo(exportingUser.getId());
        assertThat(export.username()).isEqualTo(exportingUser.getUsername());
        assertThat(export.email()).isEqualTo(exportingUser.getEmail());
        assertThat(export.authProvider()).isEqualTo(AuthProvider.LOCAL.name());
        assertThat(export.playlists()).hasSize(1);
        assertThat(export.playlists().getFirst().playlistId()).isEqualTo(ownedPlaylist.id());
        assertThat(export.playlists().getFirst().owner()).isTrue();
        assertThat(export.submittedSongs()).extracting(SongDTO::id).containsExactly(submittedSong.id());
    }
}
