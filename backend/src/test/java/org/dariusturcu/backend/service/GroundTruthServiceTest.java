package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.GroundTruthSongDTO;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroundTruthServiceTest {

    @Mock
    private SongRepository songRepository;

    private GroundTruthService service() {
        return new GroundTruthService(songRepository);
    }

    private static SongArtist artist(String name, ArtistRole role, int displayOrder) {
        SongArtist artist = new SongArtist();
        artist.setName(name);
        artist.setRole(role);
        artist.setDisplayOrder(displayOrder);
        return artist;
    }

    private static Song song(String title, int releaseYear, SongArtist... artists) {
        Song song = new Song();
        song.setTitle(title);
        song.setReleaseYear(releaseYear);
        song.setVerificationStatus(VerificationStatus.VERIFIED);
        song.setYoutubeId("video-" + title);
        song.getArtists().addAll(List.of(artists));
        return song;
    }

    @Test
    void queriesVerifiedSongsOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        when(songRepository.findByVerificationStatus(eq(VerificationStatus.VERIFIED), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        service().verifiedSongs(pageable);

        verify(songRepository).findByVerificationStatus(VerificationStatus.VERIFIED, pageable);
    }

    @Test
    void joinsMainArtistsInDisplayOrder() {
        Song jointCredit = song("Joint", 1985,
                artist("Second", ArtistRole.MAIN, 1),
                artist("First", ArtistRole.MAIN, 0),
                artist("Guest", ArtistRole.FEATURED, 2));
        when(songRepository.findByVerificationStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of(jointCredit)));

        List<GroundTruthSongDTO> triples =
                service().verifiedSongs(PageRequest.of(0, 20)).getContent();

        assertThat(triples).containsExactly(new GroundTruthSongDTO("First & Second", "Joint", 1985));
    }

    @Test
    void fallsBackToAllArtistsWithoutAMainCredit() {
        Song featuredOnly = song("Lone", 1999, artist("Guest", ArtistRole.FEATURED, 0));
        when(songRepository.findByVerificationStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of(featuredOnly)));

        List<GroundTruthSongDTO> triples =
                service().verifiedSongs(PageRequest.of(0, 20)).getContent();

        assertThat(triples).containsExactly(new GroundTruthSongDTO("Guest", "Lone", 1999));
    }

    @Test
    void passesPaginationThrough() {
        Song firstSong = song("First", 2000, artist("Solo", ArtistRole.MAIN, 0));
        Song secondSong = song("Second", 2001, artist("Solo", ArtistRole.MAIN, 0));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(songRepository.findByVerificationStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of(firstSong, secondSong)));

        service().verifiedSongs(PageRequest.of(2, 2));

        verify(songRepository).findByVerificationStatus(eq(VerificationStatus.VERIFIED), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
    }
}
