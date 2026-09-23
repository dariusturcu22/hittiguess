package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Resolves one YouTube ID through the metadata pipeline and persists the resulting
 * Song. Shared by the admin backlog drain and the user on-the-spot path so both
 * turn a resolved metadata response into a catalog row the same way. A YouTube ID
 * already backing a Song row (the patient backlog re-resolving what an earlier
 * on-the-spot call already saved) updates that row in place rather than inserting
 * a second one for the same video.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongResolutionService {

    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final int MAIN_ARTIST_DISPLAY_ORDER_START = 0;

    private final SongMetadataService songMetadataService;
    private final SongRepository songRepository;

    @Transactional
    public Optional<Song> resolveAndPersist(String youtubeId, User addedBy) {
        AiResponse aiResponse = songMetadataService.resolveByYoutubeId(youtubeId);
        if (!SUCCESS_STATUS.equals(aiResponse.status()) || aiResponse.content() == null) {
            return Optional.empty();
        }
        return Optional.of(persistResolvedSong(youtubeId, aiResponse.content(), addedBy));
    }

    private Song persistResolvedSong(String youtubeId, SongMetadataResponse metadata, User addedBy) {
        List<Song> existingSongs = songRepository.findByYoutubeId(youtubeId);
        Song song = existingSongs.isEmpty() ? new Song() : existingSongs.get(0);

        if (song.getAddedBy() == null && addedBy != null) {
            song.setAddedBy(addedBy);
        }
        song.setYoutubeId(youtubeId);
        song.setTitle(metadata.title());
        if (metadata.releaseYear() != null) {
            song.setReleaseYear(metadata.releaseYear());
        }
        song.setColor(metadata.color());
        song.setConfidence(metadata.confidence());
        song.setWikidataSitelinksCount(metadata.sitelinksCount());
        if (metadata.verificationStatus() != null) {
            song.setVerificationStatus(VerificationStatus.valueOf(metadata.verificationStatus()));
        }

        song.getArtists().clear();
        int displayOrder = MAIN_ARTIST_DISPLAY_ORDER_START;
        if (metadata.mainArtists() != null) {
            for (String mainName : metadata.mainArtists()) {
                if (mainName == null || mainName.isBlank()) {
                    continue;
                }
                SongArtist mainArtist = new SongArtist();
                mainArtist.setSong(song);
                mainArtist.setName(mainName);
                mainArtist.setRole(ArtistRole.MAIN);
                mainArtist.setDisplayOrder(displayOrder);
                song.getArtists().add(mainArtist);
                displayOrder++;
            }
        }
        if (metadata.featuredArtists() != null) {
            for (String featuredName : metadata.featuredArtists()) {
                if (featuredName == null || featuredName.isBlank()) {
                    continue;
                }
                SongArtist featuredArtist = new SongArtist();
                featuredArtist.setSong(song);
                featuredArtist.setName(featuredName);
                featuredArtist.setRole(ArtistRole.FEATURED);
                featuredArtist.setDisplayOrder(displayOrder);
                song.getArtists().add(featuredArtist);
                displayOrder++;
            }
        }

        return songRepository.save(song);
    }
}
