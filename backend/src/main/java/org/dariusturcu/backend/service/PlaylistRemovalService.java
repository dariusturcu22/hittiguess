package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.SavedPlaylistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PlaylistRemovalService {

    private final PlaylistRepository playlistRepository;
    private final SavedPlaylistRepository savedPlaylistRepository;
    private final PlaylistBanRepository playlistBanRepository;

    // Every row that points at the playlist is removed before the playlist itself, whether
    // the owner deletes it or its last member leaves or deletes their account.
    public void deleteWithDependents(Playlist playlist) {
        Long playlistId = playlist.getId();
        playlistRepository.deleteGroupPlaylistLinks(playlistId);
        savedPlaylistRepository.deleteByPlaylistId(playlistId);
        playlistBanRepository.deleteByPlaylistId(playlistId);
        playlist.getSongs().clear();
        playlistRepository.delete(playlist);
    }
}
