package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SongNormalizedKeyBackfillTest {
    private static final int FIRST_DISPLAY_ORDER = 0;

    private final SongRepository songRepository = mock(SongRepository.class);
    private final SongNormalizedKeyBackfill backfill = new SongNormalizedKeyBackfill(songRepository);

    @Test
    void songsWithoutAKeyGetTheGameSongListKey() {
        Song song = new Song();
        song.setTitle("Con Calma (Official Video)");
        SongArtist artist = new SongArtist();
        artist.setSong(song);
        artist.setName("Daddy Yankee");
        artist.setRole(ArtistRole.MAIN);
        artist.setDisplayOrder(FIRST_DISPLAY_ORDER);
        song.getArtists().add(artist);
        when(songRepository.findWithoutNormalizedKey()).thenReturn(List.of(song));

        backfill.run(null);

        assertThat(song.getNormalizedKey()).isEqualTo(GameSongList.normalizedKey("Daddy Yankee", "Con Calma"));
    }
}
