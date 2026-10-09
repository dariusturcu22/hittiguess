package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.model.ai.AiFastDateResponse;
import org.dariusturcu.backend.model.ai.AiIdentifiedSong;
import org.dariusturcu.backend.model.ai.AiMetadataContent;
import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final String FAST_TIER_REASONING =
            "Provisional fast-tier answer from one source, queued for the patient pipeline to recheck.";

    private final SongMetadataService songMetadataService;
    private final SongCatalogService songCatalogService;

    @Transactional
    public Optional<Song> resolveAndPersist(String youtubeId, User addedBy) {
        AiResponse aiResponse = songMetadataService.resolveByYoutubeId(youtubeId);
        if (!SUCCESS_STATUS.equals(aiResponse.status()) || aiResponse.content() == null) {
            return Optional.empty();
        }
        return Optional.of(songCatalogService.persist(youtubeId, aiResponse.content(), addedBy));
    }

    // Saves a fast-tier answer: the identified title, artists, and color with the one
    // lane's provisional year, left UNVERIFIED until the patient recheck runs.
    @Transactional
    public Song persistFastTierAnswer(String youtubeId, AiIdentifiedSong identified, AiFastDateResponse fastDate, User addedBy) {
        SongMetadataResponse metadata = new SongMetadataResponse(
                identified.title(),
                identified.mainArtists(),
                identified.featuredArtists(),
                fastDate.releaseYear(),
                identified.color(),
                fastDate.confidence(),
                fastDate.source(),
                FAST_TIER_REASONING,
                VerificationStatus.UNVERIFIED.name(),
                null,
                identified.durationSeconds(), null);
        return songCatalogService.persist(youtubeId, metadata, addedBy);
    }

    // Saves a verified duplicate's answer the identify pass already found, which needs
    // no year lookup of its own.
    @Transactional
    public Song persistDuplicateAnswer(String youtubeId, AiMetadataContent duplicate, User addedBy) {
        return songCatalogService.persist(youtubeId, SongMetadataService.toSongMetadataResponse(duplicate), addedBy);
    }

}
