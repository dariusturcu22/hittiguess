package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.mapper.UserMapper;
import org.dariusturcu.backend.model.playlist.ImportFromPlaylistResultDTO;
import org.dariusturcu.backend.model.playlist.JoinPlaylistRequest;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.Song;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises copying songs from a source playlist into a target playlist
 * against a real Postgres database, wiring PlaylistImportService together with
 * the access service and the user/playlist services the way a real request
 * would: a full import into an empty target, duplicate skipping, the target
 * write grant and source read grant both enforced, and an empty source.
 *
 * Uses a minimal JPA-only context (JpaTestConfig below) rather than the whole
 * BackendApplication: this project's OAuth2 client and AI-service RestClient
 * beans both build a java.net.http.HttpClient, which this sandbox's JDK can't
 * construct (a platform loopback-socket limitation, unrelated to this test).
 */
@Testcontainers
@SpringBootTest(classes = PlaylistImportIntegrationTest.JpaTestConfig.class)
@Transactional
class PlaylistImportIntegrationTest {

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
        PlaylistImportService playlistImportService(
                PlaylistRepository playlistRepository,
                PlaylistAccessService playlistAccessService) {
            return new PlaylistImportService(playlistRepository, playlistAccessService);
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
    private PlaylistRepository playlistRepository;
    @Autowired
    private SongRepository songRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private PlaylistMembershipRepository playlistMembershipRepository;
    @Autowired
    private PlaylistImportService playlistImportService;

    private static final String KNOWN_YOUTUBE_ID = "dQw4w9WgXcQ";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String usernamePrefix) {
        User user = new User();
        user.setUsername(usernamePrefix + "-" + UUID.randomUUID());
        user.setEmail(usernamePrefix + "-" + UUID.randomUUID() + "@example.com");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    private void actAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private Playlist persistPlaylist(User owner, String inviteCode) {
        Playlist playlist = new Playlist();
        playlist.setName("Playlist " + inviteCode);
        playlist.setInviteCode(inviteCode);
        playlist.setOwner(owner);
        return playlistRepository.save(playlist);
    }

    private Song persistSong(User addedBy, String title) {
        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(2000);
        song.setYoutubeId(KNOWN_YOUTUBE_ID);
        song.setAddedBy(addedBy);
        return songRepository.save(song);
    }

    private void linkSong(Playlist playlist, Song song) {
        playlist.addSong(song);
        playlistRepository.save(playlist);
    }

    private Playlist reload(Long playlistId) {
        return playlistRepository.findById(playlistId).orElseThrow();
    }

    @Test
    void importsEverySourceSongIntoAnEmptyTarget() {
        User owner = persistUser("owner");
        actAs(owner);

        Playlist source = persistPlaylist(owner, "SRCFULL1");
        Playlist target = persistPlaylist(owner, "TGTEMPT1");
        Song firstSong = persistSong(owner, "First");
        Song secondSong = persistSong(owner, "Second");
        linkSong(source, firstSong);
        linkSong(source, secondSong);

        ImportFromPlaylistResultDTO result =
                playlistImportService.importFromPlaylist(target.getId(), source.getId());

        assertThat(result.importedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();
        assertThat(reload(target.getId()).getSongs())
                .extracting(Song::getId)
                .containsExactlyInAnyOrder(firstSong.getId(), secondSong.getId());
    }

    @Test
    void skipsSongsAlreadyPresentInTheTarget() {
        User owner = persistUser("owner");
        actAs(owner);

        Playlist source = persistPlaylist(owner, "SRCOVER1");
        Playlist target = persistPlaylist(owner, "TGTOVER1");
        Song sharedSong = persistSong(owner, "Shared");
        Song sourceOnlySong = persistSong(owner, "Source Only");
        linkSong(source, sharedSong);
        linkSong(source, sourceOnlySong);
        linkSong(target, sharedSong);

        ImportFromPlaylistResultDTO result =
                playlistImportService.importFromPlaylist(target.getId(), source.getId());

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(reload(target.getId()).getSongs())
                .extracting(Song::getId)
                .containsExactlyInAnyOrder(sharedSong.getId(), sourceOnlySong.getId());
    }

    @Test
    void rejectsARequesterWithoutWriteAccessToTheTarget() {
        User targetOwner = persistUser("target-owner");
        User sourceOwner = persistUser("source-owner");

        actAs(targetOwner);
        Playlist target = persistPlaylist(targetOwner, "TGTPROT1");
        String targetInviteCode = target.getInviteCode();

        actAs(sourceOwner);
        Playlist source = persistPlaylist(sourceOwner, "SRCOWN01");
        Song song = persistSong(sourceOwner, "Song");
        linkSong(source, song);

        userService.joinPlaylist(targetInviteCode, new JoinPlaylistRequest("Guest", null));

        var membership = playlistMembershipRepository
                .findByPlaylistIdAndUserId(target.getId(), sourceOwner.getId()).orElseThrow();
        membership.setCanWrite(false);
        playlistMembershipRepository.save(membership);

        actAs(sourceOwner);
        Long targetId = target.getId();
        Long sourceId = source.getId();
        assertThatThrownBy(() -> playlistImportService.importFromPlaylist(targetId, sourceId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsASourceTheRequesterCannotRead() {
        User sourceOwner = persistUser("source-owner");
        User importer = persistUser("importer");

        actAs(sourceOwner);
        Playlist source = persistPlaylist(sourceOwner, "SRCPRIV1");
        Song song = persistSong(sourceOwner, "Private Song");
        linkSong(source, song);

        actAs(importer);
        Playlist target = persistPlaylist(importer, "TGTOWN01");

        Long targetId = target.getId();
        Long sourceId = source.getId();
        assertThatThrownBy(() -> playlistImportService.importFromPlaylist(targetId, sourceId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void importsNothingFromAnEmptySource() {
        User owner = persistUser("owner");
        actAs(owner);

        Playlist source = persistPlaylist(owner, "SRCNONE1");
        Playlist target = persistPlaylist(owner, "TGTNONE1");

        ImportFromPlaylistResultDTO result =
                playlistImportService.importFromPlaylist(target.getId(), source.getId());

        assertThat(result.importedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(reload(target.getId()).getSongs()).isEmpty();
    }
}
