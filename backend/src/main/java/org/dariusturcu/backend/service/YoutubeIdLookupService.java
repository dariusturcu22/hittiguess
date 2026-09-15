package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.song.AlternateYoutubeId;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.YoutubeIdLookupResult;
import org.dariusturcu.backend.repository.AlternateYoutubeIdRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The shared cheap first step both catalog-seeding paths run: given a batch of
 * YouTube IDs, partition them into already-known and genuinely-new without any
 * external API call. A known ID matches either a Song's own youtubeId or a row
 * in the alternate-ID table.
 */
@Service
@RequiredArgsConstructor
public class YoutubeIdLookupService {

    private final SongRepository songRepository;
    private final AlternateYoutubeIdRepository alternateYoutubeIdRepository;

    @Transactional(readOnly = true)
    public YoutubeIdLookupResult partitionKnownAndUnknown(Collection<String> submittedYoutubeIds) {
        Set<String> distinctSubmittedIds = new LinkedHashSet<>(submittedYoutubeIds);

        Set<String> knownIds = new LinkedHashSet<>(songRepository.findKnownYoutubeIds(distinctSubmittedIds));
        alternateYoutubeIdRepository.findByYoutubeIdIn(distinctSubmittedIds).stream()
                .map(AlternateYoutubeId::getYoutubeId)
                .forEach(knownIds::add);

        Set<String> unknownIds = new LinkedHashSet<>(distinctSubmittedIds);
        unknownIds.removeAll(knownIds);

        return new YoutubeIdLookupResult(knownIds, unknownIds);
    }

    /**
     * Links a new upload's YouTube ID to an existing canonical Song, used when a
     * near-duplicate check (story 16's pgvector match) identifies an ID that passed
     * the exact-ID check as new but is the same track under a different upload. This
     * is the terminal step for that ID: no full pipeline run follows.
     */
    @Transactional
    public AlternateYoutubeId linkAlternateId(String youtubeId, Song canonicalSong) {
        AlternateYoutubeId alternateYoutubeId = new AlternateYoutubeId();
        alternateYoutubeId.setYoutubeId(youtubeId);
        alternateYoutubeId.setSong(canonicalSong);
        return alternateYoutubeIdRepository.save(alternateYoutubeId);
    }

    @Transactional(readOnly = true)
    public boolean isKnown(String youtubeId) {
        List<Song> songsByPrimaryId = songRepository.findByYoutubeId(youtubeId);
        return !songsByPrimaryId.isEmpty() || alternateYoutubeIdRepository.existsByYoutubeId(youtubeId);
    }
}
