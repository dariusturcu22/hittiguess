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
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistRepository;

import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional

public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final PlaylistMapper playlistMapper;
    private final SongMapper songMapper;

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
        if (!song.getPlaylist().getId().equals(playlistId)) {
            throw new ResourceNotFoundException(ResourceType.SONG_NOT_IN_PLAYLIST, song.getId(), playlistId);
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
        newSong.setPlaylist(playlist);
        newSong.setAddedBy(user);

        Song savedSong = songRepository.save(newSong);

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
    }
}
