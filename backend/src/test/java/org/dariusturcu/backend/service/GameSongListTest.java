package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class GameSongListTest {

    private static final long SEED = 42L;
    private static final long OTHER_SEED = 7L;
    private static final int SONG_COUNT = 20;
    private static final long FIRST_ID = 1L;

    @Test
    void normalizedKeyIgnoresCaseAndDiacritics() {
        assertThat(GameSongList.normalizedKey(song(1L, "Beyoncé", "Beyoncé")))
                .isEqualTo(GameSongList.normalizedKey(song(2L, "BEYONCE", "beyonce")));
    }

    @Test
    void normalizedKeyIgnoresPunctuation() {
        assertThat(GameSongList.normalizedKey(song(1L, "Don't Stop Me Now", "Queen")))
                .isEqualTo(GameSongList.normalizedKey(song(2L, "Dont Stop Me Now!", "Queen!")));
    }

    @Test
    void normalizedKeyIgnoresBracketedQualifiers() {
        assertThat(GameSongList.normalizedKey(song(1L, "Halo (Album Version)", "Beyoncé")))
                .isEqualTo(GameSongList.normalizedKey(song(2L, "Halo [Radio Edit]", "Beyoncé")));
    }

    @Test
    void normalizedKeyIgnoresDashSuffixedQualifiers() {
        assertThat(GameSongList.normalizedKey(song(1L, "Halo - Live", "Beyoncé")))
                .isEqualTo(GameSongList.normalizedKey(song(2L, "Halo – Remastered", "Beyoncé")));
    }

    @Test
    void normalizedKeyUsesThePrimaryArtistByDisplayOrder() {
        Song primaryOnly = song(1L, "Song", "Primary Artist");

        Song withFeatured = song(2L, "Song", "Primary Artist");
        SongArtist featured = new SongArtist();
        featured.setName("Featured Artist");
        featured.setRole(ArtistRole.FEATURED);
        featured.setDisplayOrder(1);
        featured.setSong(withFeatured);
        withFeatured.getArtists().add(featured);

        assertThat(GameSongList.normalizedKey(primaryOnly)).isEqualTo(GameSongList.normalizedKey(withFeatured));
    }

    @Test
    void buildKeepsTheFirstSongOnAKeyCollision() {
        Song original = song(1L, "Halo", "Beyoncé");
        Song duplicate = song(2L, "HALO (Album Version)", "beyonce");
        Song distinct = song(3L, "Other Song", "Someone Else");

        List<Song> built = new GameSongList(new Random(SEED)).build(List.of(original, duplicate, distinct));

        assertThat(built).extracting(Song::getId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void buildReturnsEveryDistinctSongOnce() {
        List<Song> songs = distinctSongs(SONG_COUNT);

        List<Song> built = new GameSongList(new Random(SEED)).build(songs);

        assertThat(built).hasSize(SONG_COUNT).doesNotHaveDuplicates();
        assertThat(built).extracting(Song::getId)
                .containsExactlyInAnyOrderElementsOf(songs.stream().map(Song::getId).toList());
    }

    @Test
    void seededShuffleIsDeterministic() {
        List<Song> songs = distinctSongs(SONG_COUNT);

        List<Long> firstOrder = idsOf(new GameSongList(new Random(SEED)).build(songs));
        List<Long> secondOrder = idsOf(new GameSongList(new Random(SEED)).build(songs));

        assertThat(firstOrder).isEqualTo(secondOrder);
    }

    @Test
    void shuffleOrderDependsOnTheRandomSource() {
        List<Song> songs = distinctSongs(SONG_COUNT);

        List<Long> seedOrder = idsOf(new GameSongList(new Random(SEED)).build(songs));
        List<Long> otherSeedOrder = idsOf(new GameSongList(new Random(OTHER_SEED)).build(songs));

        assertThat(seedOrder).isNotEqualTo(otherSeedOrder);
    }

    private List<Long> idsOf(List<Song> songs) {
        return songs.stream().map(Song::getId).toList();
    }

    private List<Song> distinctSongs(int count) {
        List<Song> songs = new ArrayList<>();
        for (long songId = FIRST_ID; songId <= count; songId++) {
            songs.add(song(songId, "Song " + songId, "Artist " + songId));
        }
        return songs;
    }

    private Song song(long id, String title, String artistName) {
        Song song = new Song();
        song.setId(id);
        song.setTitle(title);
        SongArtist artist = new SongArtist();
        artist.setName(artistName);
        artist.setRole(ArtistRole.MAIN);
        artist.setDisplayOrder(0);
        artist.setSong(song);
        song.getArtists().add(artist);
        return song;
    }
}
