package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.AlternateYoutubeIdRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SongCatalogServiceTest {
    private static final String YOUTUBE_ID = "video1234567";
    private static final int ORIGINAL_YEAR = 2015;
    private static final int PROPOSED_YEAR = 2014;
    private static final int DURATION = 210;

    private final SongRepository songRepository = mock(SongRepository.class);
    private final AlternateYoutubeIdRepository alternateRepository = mock(AlternateYoutubeIdRepository.class);
    private final SongCatalogService catalogService = new SongCatalogService(songRepository, alternateRepository);

    @Test
    void completionOnVerifiedSongWithDifferentYearSetsProposalAndKeepsYear() {
        Song verifiedSong = new Song();
        verifiedSong.setId(1L);
        verifiedSong.setYoutubeId(YOUTUBE_ID);
        verifiedSong.setTitle("Hey Mama");
        verifiedSong.setReleaseYear(ORIGINAL_YEAR);
        verifiedSong.setVerificationStatus(VerificationStatus.VERIFIED);

        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of(verifiedSong));
        when(songRepository.save(any(Song.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SongMetadataResponse metadata = new SongMetadataResponse(
                "Hey Mama", List.of("David Guetta"), List.of(), PROPOSED_YEAR, "#FF0000",
                "high", "MUSICBRAINZ", "Album release in 2014", "VERIFIED", 50, DURATION, null);

        Song result = catalogService.persist(YOUTUBE_ID, metadata, null);

        assertThat(result.getReleaseYear()).isEqualTo(ORIGINAL_YEAR);
        assertThat(result.getProposedReleaseYear()).isEqualTo(PROPOSED_YEAR);
        assertThat(result.getProposedAt()).isNotNull();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(result.getDurationSeconds()).isEqualTo(DURATION);
        verify(songRepository).save(verifiedSong);
    }

    @Test
    void completionOnVerifiedSongWithSameYearClearsExistingProposal() {
        Song verifiedSong = new Song();
        verifiedSong.setId(1L);
        verifiedSong.setYoutubeId(YOUTUBE_ID);
        verifiedSong.setTitle("Hey Mama");
        verifiedSong.setReleaseYear(ORIGINAL_YEAR);
        verifiedSong.setProposedReleaseYear(PROPOSED_YEAR);
        verifiedSong.setProposedAt(Instant.now());
        verifiedSong.setVerificationStatus(VerificationStatus.VERIFIED);

        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of(verifiedSong));
        when(songRepository.save(any(Song.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SongMetadataResponse metadata = new SongMetadataResponse(
                "Hey Mama", List.of("David Guetta"), List.of(), ORIGINAL_YEAR, "#FF0000",
                "high", "MUSICBRAINZ", "Agreed year", "VERIFIED", 50, DURATION, null);

        Song result = catalogService.persist(YOUTUBE_ID, metadata, null);

        assertThat(result.getReleaseYear()).isEqualTo(ORIGINAL_YEAR);
        assertThat(result.getProposedReleaseYear()).isNull();
        assertThat(result.getProposedAt()).isNull();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(songRepository).save(verifiedSong);
    }

    @Test
    void completionOnNeedsReviewSongOverwritesStoredYearAndStatus() {
        Song needsReviewSong = new Song();
        needsReviewSong.setId(2L);
        needsReviewSong.setYoutubeId(YOUTUBE_ID);
        needsReviewSong.setTitle("Old Title");
        needsReviewSong.setReleaseYear(ORIGINAL_YEAR);
        needsReviewSong.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);

        when(songRepository.findByYoutubeId(YOUTUBE_ID)).thenReturn(List.of(needsReviewSong));
        when(songRepository.save(any(Song.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SongMetadataResponse metadata = new SongMetadataResponse(
                "Correct Title", List.of("Artist"), List.of(), PROPOSED_YEAR, "#00FF00",
                "high", "WIKIDATA", "Resolved", "VERIFIED", 20, DURATION, null);

        Song result = catalogService.persist(YOUTUBE_ID, metadata, null);

        assertThat(result.getTitle()).isEqualTo("Correct Title");
        assertThat(result.getReleaseYear()).isEqualTo(PROPOSED_YEAR);
        assertThat(result.getProposedReleaseYear()).isNull();
        assertThat(result.getProposedAt()).isNull();
        assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(songRepository).save(needsReviewSong);
    }
}
