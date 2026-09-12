package org.dariusturcu.backend.migration;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the song_playlists join table actually supports a song belonging
 * to more than one playlist at once, that unlinking it from one playlist
 * leaves it intact in the other, and that a song is independent of any
 * playlist: unlinking its last one leaves the row itself untouched, exercised
 * against a real Postgres database through the full migration chain rather
 * than mocks.
 */
@Testcontainers
@SpringBootTest(classes = SongMultiplePlaylistsIntegrationTest.JpaTestConfig.class)
@Transactional
class SongMultiplePlaylistsIntegrationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = SongRepository.class)
    static class JpaTestConfig {
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

    private User songOwner;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("catalog-owner");
        songOwner = userRepository.save(user);
    }

    private Playlist newPlaylist(String inviteCode) {
        Playlist playlist = new Playlist();
        playlist.setInviteCode(inviteCode);
        return playlistRepository.save(playlist);
    }

    private Song newSong() {
        Song song = new Song();
        song.setTitle("Shared Song");
        song.setReleaseYear(2020);
        song.setYoutubeId("dQw4w9WgXcQ");
        song.setAddedBy(songOwner);
        return songRepository.save(song);
    }

    @Test
    void aSongCanBelongToMultiplePlaylistsAtOnce() {
        Playlist firstPlaylist = newPlaylist("FIRST001");
        Playlist secondPlaylist = newPlaylist("SECOND01");
        Song song = newSong();

        firstPlaylist.addSong(song);
        secondPlaylist.addSong(song);
        playlistRepository.save(firstPlaylist);
        playlistRepository.save(secondPlaylist);

        Song reloadedSong = songRepository.findById(song.getId()).orElseThrow();
        assertThat(reloadedSong.getPlaylists()).containsExactlyInAnyOrder(firstPlaylist, secondPlaylist);
    }

    @Test
    void unlinkingFromOnePlaylistLeavesItIntactInTheOther() {
        Playlist firstPlaylist = newPlaylist("FIRST002");
        Playlist secondPlaylist = newPlaylist("SECOND02");
        Song song = newSong();

        firstPlaylist.addSong(song);
        secondPlaylist.addSong(song);
        playlistRepository.save(firstPlaylist);
        playlistRepository.save(secondPlaylist);

        firstPlaylist.removeSong(song);
        playlistRepository.save(firstPlaylist);

        Song reloadedSong = songRepository.findById(song.getId()).orElseThrow();
        assertThat(reloadedSong.getPlaylists()).containsExactly(secondPlaylist);
    }

    @Test
    void unlinkingASongsOnlyPlaylistLeavesTheSongRowIntact() {
        Playlist onlyPlaylist = newPlaylist("ONLY0001");
        Song song = newSong();

        onlyPlaylist.addSong(song);
        playlistRepository.save(onlyPlaylist);

        onlyPlaylist.removeSong(song);
        playlistRepository.save(onlyPlaylist);

        Song reloadedSong = songRepository.findById(song.getId()).orElseThrow();
        assertThat(reloadedSong.getPlaylists()).isEmpty();
    }
}
