package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.GroundTruthSongDTO;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Serves the public ground-truth data: verified catalog facts only. The query
 * filters on VERIFIED status, and the triple shape carries no YouTube-sourced
 * fields, so unverified rows and video ids cannot leak through this path.
 */
@Service
@RequiredArgsConstructor
public class GroundTruthService {

    private static final String JOINT_ARTIST_SEPARATOR = " & ";

    private final SongRepository songRepository;

    @Transactional(readOnly = true)
    public Page<GroundTruthSongDTO> verifiedSongs(Pageable pageable) {
        return songRepository.findByVerificationStatus(VerificationStatus.VERIFIED, pageable)
                .map(this::toTriple);
    }

    private GroundTruthSongDTO toTriple(Song song) {
        List<String> mainArtists = song.getArtists().stream()
                .filter(artist -> artist.getRole() == ArtistRole.MAIN)
                .sorted(Comparator.comparing(SongArtist::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(SongArtist::getName)
                .toList();
        List<String> creditedArtists = mainArtists.isEmpty()
                ? song.getArtists().stream().map(SongArtist::getName).toList()
                : mainArtists;
        return new GroundTruthSongDTO(
                String.join(JOINT_ARTIST_SEPARATOR, creditedArtists),
                song.getTitle(),
                song.getReleaseYear());
    }
}
