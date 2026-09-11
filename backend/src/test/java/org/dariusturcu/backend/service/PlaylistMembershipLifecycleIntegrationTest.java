package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.mapper.UserMapper;
import org.dariusturcu.backend.model.playlist.JoinPlaylistRequest;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.UpdateMembershipGrantsRequest;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.user.AuthProvider;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the full playlist membership lifecycle against a real Postgres
 * database, wiring PlaylistService and UserService together the way a real
 * request would, rather than mocking the collaborators between them: join
 * with a custom identity, a revoked grant blocking the action it gates, kick
 * allowing a rejoin, and ban blocking one.
 *
 * Uses a minimal JPA-only context (JpaTestConfig below) rather than the whole
 * BackendApplication: this project's OAuth2 client and AI-service RestClient
 * beans both build a java.net.http.HttpClient, which this sandbox's JDK can't
 * construct (a platform loopback-socket limitation, unrelated to this test).
 */
@Testcontainers
@SpringBootTest(classes = PlaylistMembershipLifecycleIntegrationTest.JpaTestConfig.class)
class PlaylistMembershipLifecycleIntegrationTest {

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
                PlaylistBanRepository playlistBanRepository) {
            return new UserService(userRepository, playlistRepository, userMapper, playlistMapper,
                    playlistMembershipRepository, playlistBanRepository);
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistMembershipRepository playlistMembershipRepository;
    @Autowired
    private PlaylistBanRepository playlistBanRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private PlaylistService playlistService;

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

    private CreateSongRequest anySongRequest() {
        return new CreateSongRequest(
                "Artist",
                "Title",
                2000,
                "dQw4w9WgXcQ",
                "abcdef",
                "abcdef",
                Set.of(),
                null);
    }

    @Test
    void fullMembershipLifecycle() {
        User owner = persistUser("owner-" + UUID.randomUUID());
        User firstMember = persistUser("first-member-" + UUID.randomUUID());
        User secondMember = persistUser("second-member-" + UUID.randomUUID());

        actAs(owner);
        PlaylistDetailDTO createdPlaylist = userService.createPlaylist();
        Long playlistId = createdPlaylist.id();
        String inviteCode = playlistRepository.findById(playlistId).orElseThrow().getInviteCode();

        actAs(firstMember);
        userService.joinPlaylist(inviteCode, new JoinPlaylistRequest("First Nickname", "first-avatar.png"));

        assertThat(playlistMembershipRepository.findByPlaylistIdAndUserId(playlistId, firstMember.getId()))
                .hasValueSatisfying(membership -> {
                    assertThat(membership.getDisplayName()).isEqualTo("First Nickname");
                    assertThat(membership.getAvatarUrl()).isEqualTo("first-avatar.png");
                });

        actAs(owner);
        playlistService.updateMemberGrants(playlistId, firstMember.getId(),
                new UpdateMembershipGrantsRequest(null, false, null));

        actAs(firstMember);
        assertThatThrownBy(() -> playlistService.createSong(playlistId, anySongRequest()))
                .isInstanceOf(AccessDeniedException.class);

        actAs(owner);
        playlistService.kickMember(playlistId, firstMember.getId());
        assertThat(playlistMembershipRepository.existsByPlaylistIdAndUserId(playlistId, firstMember.getId())).isFalse();
        assertThat(playlistBanRepository.existsByPlaylistIdAndUserId(playlistId, firstMember.getId())).isFalse();

        actAs(firstMember);
        userService.joinPlaylist(inviteCode, null);
        assertThat(playlistMembershipRepository.existsByPlaylistIdAndUserId(playlistId, firstMember.getId())).isTrue();

        actAs(secondMember);
        userService.joinPlaylist(inviteCode, null);

        actAs(owner);
        playlistService.banMember(playlistId, secondMember.getId());
        assertThat(playlistMembershipRepository.existsByPlaylistIdAndUserId(playlistId, secondMember.getId())).isFalse();
        assertThat(playlistBanRepository.existsByPlaylistIdAndUserId(playlistId, secondMember.getId())).isTrue();

        actAs(secondMember);
        assertThatThrownBy(() -> userService.joinPlaylist(inviteCode, null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
