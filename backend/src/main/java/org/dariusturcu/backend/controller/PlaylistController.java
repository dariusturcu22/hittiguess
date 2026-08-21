package org.dariusturcu.backend.controller;

import jakarta.validation.Valid;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.service.PlaylistService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
@Tag(name = "Playlist management", description = "Handles operations regarding playlist management")
public class PlaylistController {
    private final PlaylistService playlistService;

    @Operation(summary = "Get playlist information")
    @GetMapping("/{playlistId}")
    public ResponseEntity<PlaylistDetailDTO> getPlaylist(
            @PathVariable Long playlistId) {
        PlaylistDetailDTO playlist = playlistService.getPlaylist(playlistId);
        return ResponseEntity.ok(playlist);
    }

    @Operation(summary = "Update playlist information")
    @PatchMapping("/{playlistId}")
    public ResponseEntity<PlaylistDetailDTO> updatePlaylist(
            @PathVariable Long playlistId,
            @Valid @RequestBody UpdatePlaylistRequest request) {
        PlaylistDetailDTO updatedPlaylist = playlistService.updatePlaylist(playlistId, request);
        return ResponseEntity.ok(updatedPlaylist);
    }

    @Operation(summary = "Get information about a particular song in the playlist")
    @GetMapping("/{playlistId}/songs/{songId}")
    public ResponseEntity<SongDTO> getSong(
            @PathVariable Long playlistId,
            @PathVariable Long songId) {
        SongDTO song = playlistService.getSong(playlistId, songId);
        return ResponseEntity.ok(song);
    }

    @Operation(summary = "Add a new song to the playlist")
    @PostMapping("/{playlistId}/songs")
    public ResponseEntity<SongDTO> createSong(
            @PathVariable Long playlistId,
            @Valid @RequestBody CreateSongRequest request) {
        SongDTO newSong = playlistService.createSong(playlistId, request);
        return ResponseEntity.ok(newSong);
    }

    @Operation(summary = "Edit information about a particular song in the playlist")
    @PatchMapping("/{playlistId}/songs/{songId}")
    public ResponseEntity<SongDTO> updateSong(
            @PathVariable Long playlistId,
            @PathVariable Long songId,
            @Valid @RequestBody UpdateSongRequest request) {
        SongDTO song = playlistService.updateSong(playlistId, songId, request);
        return ResponseEntity.ok(song);
    }

    @Operation(summary = "Delete song from the playlist")
    @DeleteMapping("/{playlistId}/songs/{songId}")
    public ResponseEntity<Void> deleteSong(
            @PathVariable Long playlistId,
            @PathVariable Long songId) {
        playlistService.deleteSong(playlistId, songId);
        return ResponseEntity.noContent().build();
    }
}
