package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves one YouTube ID through the metadata pipeline and persists the resulting
 * Song. Shared by the admin backlog drain and the user on-the-spot path so both
 * turn a resolved metadata response into a catalog row the same way. The release
 * year's verification status is left at the pipeline default here; story 18's
 * lock-evaluation is what promotes it, wired in that story.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongResolutionService {

    private static final String SUCCESS_STATUS = "SUCCESS";
    private static final int MAIN_ARTIST_DISPLAY_ORDER = 0;

    private final SongMetadataService songMetadataService;
    private final SongRepository songRepository;

    @Transactional
    public Optional<Song> resolveAndPersist(String youtubeId) {
        AiResponse aiResponse = songMetadataService.resolveByYoutubeId(youtubeId);
        if (!SUCCESS_STATUS.equals(aiResponse.status()) || aiResponse.content() == null) {
            return Optional.empty();
        }
        return Optional.of(persistResolvedSong(youtubeId, aiResponse.content()));
    }

    private Song persistResolvedSong(String youtubeId, SongMetadataResponse metadata) {
        Song song = new Song();
        song.setYoutubeId(youtubeId);
        song.setTitle(metadata.title());
        if (metadata.releaseYear() != null) {
            song.setReleaseYear(metadata.releaseYear());
        }
        song.setGradientColor1(metadata.gradientColor1());
        song.setGradientColor2(metadata.gradientColor2());
        song.setConfidence(metadata.confidence());

        if (metadata.artist() != null && !metadata.artist().isBlank()) {
            SongArtist mainArtist = new SongArtist();
            mainArtist.setSong(song);
            mainArtist.setName(metadata.artist());
            mainArtist.setRole(ArtistRole.MAIN);
            mainArtist.setDisplayOrder(MAIN_ARTIST_DISPLAY_ORDER);
            song.getArtists().add(mainArtist);
        }

        return songRepository.save(song);
    }
}
