package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;

import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional

public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final PlaylistMapper playlistMapper;
    private final SongMapper songMapper;

    // VERIFIED is a pipeline-established lock and NEEDS_REVIEW is an LLM-reconciled year;
    // hand-editing either undermines the trust tier the pipeline already assigned it.
    // UNVERIFIED hasn't been through the pipeline at all yet (today, every song's actual
    // status, since story 40's pipeline doesn't exist to move it anywhere else), so there's
    // no established trust tier to protect there, same as the least-trusted MANUAL_ENTRY tier.
    private static final Set<VerificationStatus> EDITABLE_VERIFICATION_STATUSES =
            Set.of(VerificationStatus.UNVERIFIED, VerificationStatus.MANUAL_ENTRY);

    private Playlist findPlaylist(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));
    }

    private Song findSong(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.SONG, songId));
    }

    private void checkPlaylistAccess(Playlist playlist) {
        User currentUser = SecurityUtils.getCurrentUser();

        boolean hasAccess = playlist.getUsers().stream()
                .anyMatch(user -> user.getId().equals(currentUser.getId()));

        if (!hasAccess) {
            throw new AccessDeniedException("You are not a member of this playlist");
        }
    }

    private void checkSongBelongsToPlaylist(Song song, Long playlistId) {
        boolean belongsToPlaylist = song.getPlaylists().stream()
                .anyMatch(songPlaylist -> songPlaylist.getId().equals(playlistId));

        if (!belongsToPlaylist) {
            throw new ResourceNotFoundException(ResourceType.SONG_NOT_IN_PLAYLIST, song.getId(), playlistId);
        }
    }

    private void checkSongEditable(Song song) {
        if (!EDITABLE_VERIFICATION_STATUSES.contains(song.getVerificationStatus())) {
            throw new AccessDeniedException("This song has been verified and can no longer be edited directly");
        }
    }

    @Transactional(readOnly = true)
    public PlaylistDetailDTO getPlaylist(
            Long playlistId) {

        Playlist playlist = findPlaylist(playlistId);
        checkPlaylistAccess(playlist);
        return playlistMapper.toDetailDTO(playlist);
    }

    public PlaylistDetailDTO updatePlaylist(
            Long playlistId,
            UpdatePlaylistRequest request) {

        Playlist playlist = findPlaylist(playlistId);

        checkPlaylistAccess(playlist);
        playlist = playlistMapper.updateEntity(playlist, request);

        playlistRepository.save(playlist);

        return playlistMapper.toDetailDTO(playlist);
    }

    @Transactional(readOnly = true)
    public SongDTO getSong(
            Long playlistId,
            Long songId) {

        Playlist playlist = findPlaylist(playlistId);

        checkPlaylistAccess(playlist);
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);

        return songMapper.toDTO(song);
    }

    public SongDTO createSong(
            Long playlistId,
            CreateSongRequest request) {

        Playlist playlist = findPlaylist(playlistId);
        checkPlaylistAccess(playlist);

        User user = SecurityUtils.getCurrentUser();
        Song newSong = songMapper.toEntity(request);
        newSong.setAddedBy(user);

        Song savedSong = songRepository.save(newSong);
        playlist.addSong(savedSong);
        playlistRepository.save(playlist);

        return songMapper.toDTO(savedSong);
    }

    public SongDTO updateSong(
            Long playlistId,
            Long songId,
            UpdateSongRequest request) {

        Playlist playlist = findPlaylist(playlistId);
        checkPlaylistAccess(playlist);
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);
        checkSongEditable(song);

        song = songMapper.updateEntity(song, request);

        playlistRepository.save(playlist);

        return songMapper.toDTO(song);
    }

    public void deleteSong(
            Long playlistId,
            Long songId) {

        Playlist playlist = findPlaylist(playlistId);
        checkPlaylistAccess(playlist);
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);

        playlist.removeSong(song);
        playlistRepository.save(playlist);

        // A song only exists to belong to a playlist, there's no catalog view that can reach one
        // with zero playlists left. Unlinking the last playlist is a real delete, not just this
        // playlist's link, to avoid leaving unreachable rows behind (see DECISIONS.md's 2026-09
        // "Song deletion" entry).
        if (song.getPlaylists().isEmpty()) {
            songRepository.delete(song);
        }
    }
}
