package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.ai.SongMetadataResponse;
import org.dariusturcu.backend.model.song.AlternateYoutubeId;
import org.dariusturcu.backend.model.song.ArtistRole;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongArtist;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.AlternateYoutubeIdRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SongCatalogService {
    private static final int MAIN_ARTIST_DISPLAY_ORDER_START = 0;
    private static final String SONG_LOCK_KEY_PREFIX = "song-youtube-id:";

    private final SongRepository songRepository;
    private final AlternateYoutubeIdRepository alternateYoutubeIdRepository;

    @Transactional
    public Optional<Song> findKnownSong(String youtubeId) {
        songRepository.acquireTransactionLock(SONG_LOCK_KEY_PREFIX + youtubeId);
        return findKnownSongWithoutLock(youtubeId);
    }

    private Optional<Song> findKnownSongWithoutLock(String youtubeId) {
        Optional<Song> primarySong = songRepository.findByYoutubeId(youtubeId).stream().findFirst();
        if (primarySong.isPresent()) {
            return primarySong;
        }
        return alternateYoutubeIdRepository.findByYoutubeIdIn(List.of(youtubeId)).stream()
                .map(AlternateYoutubeId::getSong).findFirst();
    }

    @Transactional
    public Song persist(String youtubeId, SongMetadataResponse metadata, User addedBy) {
        // Catalog inserts and alternate-ID links share the same transaction lock.
        songRepository.acquireTransactionLock(SONG_LOCK_KEY_PREFIX + youtubeId);
        Optional<Song> existingSong = findKnownSongWithoutLock(youtubeId);
        if (existingSong.isPresent() && !youtubeId.equals(existingSong.get().getYoutubeId())) {
            return existingSong.get();
        }
        if (existingSong.isPresent() && existingSong.get().getVerificationStatus() == VerificationStatus.VERIFIED
                && !VerificationStatus.VERIFIED.name().equals(metadata.verificationStatus())) {
            return existingSong.get();
        }
        if (metadata.canonicalSongId() != null) {
            Song matchedSong = songRepository.findById(metadata.canonicalSongId())
                    .filter(candidate -> candidate.getVerificationStatus() == VerificationStatus.VERIFIED)
                    .orElseThrow(() -> new IllegalStateException("The duplicate match no longer refers to a verified song"));
            if (existingSong.isPresent()) {
                return existingSong.get();
            }
            if (!youtubeId.equals(matchedSong.getYoutubeId())) {
                AlternateYoutubeId alternateId = new AlternateYoutubeId();
                alternateId.setYoutubeId(youtubeId);
                alternateId.setSong(matchedSong);
                alternateYoutubeIdRepository.save(alternateId);
            }
            return matchedSong;
        }
        Song song = existingSong.orElseGet(Song::new);

        if (song.getAddedBy() == null && addedBy != null) {
            song.setAddedBy(addedBy);
        }
        if (song.getVerificationStatus() == VerificationStatus.VERIFIED) {
            song.recordOfficialDuration(metadata.durationSeconds());
            return songRepository.save(song);
        }
        song.setYoutubeId(youtubeId);
        song.setTitle(metadata.title());
        if (metadata.releaseYear() != null) {
            song.setReleaseYear(metadata.releaseYear());
        }
        song.setColor(metadata.color());
        song.recordOfficialDuration(metadata.durationSeconds());
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
