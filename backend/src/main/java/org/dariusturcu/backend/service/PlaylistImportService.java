package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.playlist.ImportFromPlaylistResultDTO;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copies the songs of one playlist into another by linking the source
 * playlist's existing song rows into the target through the shared
 * song_playlists join table. No metadata pipeline runs: every song is
 * already a resolved catalog row, so the copy only adds join links.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlaylistImportService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistAccessService playlistAccessService;
    private final SongRepository songRepository;

    private Playlist findPlaylist(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));
    }

    public ImportFromPlaylistResultDTO importFromPlaylist(Long targetPlaylistId, Long sourcePlaylistId) {
        User currentUser = SecurityUtils.getCurrentUser();

        Playlist source = findPlaylist(sourcePlaylistId);
        playlistAccessService.requireRead(source, currentUser);

        Playlist target = findPlaylist(targetPlaylistId);
        playlistAccessService.requireWrite(target, currentUser);

        Set<Long> existingTargetSongIds = new HashSet<>();
        for (Song targetSong : target.getSongs()) {
            existingTargetSongIds.add(targetSong.getId());
        }

        int importedCount = 0;
        int skippedCount = 0;
        for (Song sourceSong : source.getSongs()) {
            if (existingTargetSongIds.contains(sourceSong.getId())) {
                skippedCount++;
                continue;
            }
            target.addSong(sourceSong);
            existingTargetSongIds.add(sourceSong.getId());
            importedCount++;
        }

        playlistRepository.save(target);

        return new ImportFromPlaylistResultDTO(importedCount, skippedCount);
    }

    @Transactional(readOnly = true)
    public void requireWritableTarget(Long targetPlaylistId) {
        playlistAccessService.requireWrite(findPlaylist(targetPlaylistId), SecurityUtils.getCurrentUser());
    }

    /**
     * Links already-resolved catalog songs into a playlist, skipping any already
     * present, for a caller (such as an on-the-spot bulk import) that resolved the
     * songs itself and only needs them attached to a specific playlist afterward.
     */
    public void addResolvedSongs(Long targetPlaylistId, List<Song> resolvedSongs) {
        User currentUser = SecurityUtils.getCurrentUser();

        Playlist target = findPlaylist(targetPlaylistId);
        playlistAccessService.requireWrite(target, currentUser);

        Set<Long> existingTargetSongIds = new HashSet<>();
        for (Song targetSong : target.getSongs()) {
            existingTargetSongIds.add(targetSong.getId());
        }

        for (Song resolvedSong : resolvedSongs) {
            if (existingTargetSongIds.add(resolvedSong.getId())) {
                target.addSong(resolvedSong);
            }
        }

        playlistRepository.save(target);
    }

    /**
     * Links already-resolved catalog songs into a playlist by id, reloading each
     * into the current persistence context first. Callers running off the request
     * thread (a background import has no open-in-view session) must use this
     * instead of addResolvedSongs, whose entities would arrive detached and fail
     * lazy loading when the bidirectional link sync touches them.
     */
    public void addResolvedSongIds(Long targetPlaylistId, List<Long> resolvedSongIds) {
        addResolvedSongs(targetPlaylistId, songRepository.findAllById(resolvedSongIds));
    }
}
