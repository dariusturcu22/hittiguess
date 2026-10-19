package org.dariusturcu.backend.controller;

import jakarta.validation.Valid;
import org.dariusturcu.backend.model.playlist.ImportFromPlaylistRequest;
import org.dariusturcu.backend.model.playlist.ImportFromPlaylistResultDTO;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistInvitePreviewDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMemberDTO;
import org.dariusturcu.backend.model.playlist.PublicPlaylistSummaryDTO;
import org.dariusturcu.backend.model.playlist.UpdateMembershipGrantsRequest;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.service.PlaylistImportService;
import org.dariusturcu.backend.service.PlaylistService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.List;


@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
@Tag(name = "Playlist management", description = "Handles operations regarding playlist management")
public class PlaylistController {
    private final PlaylistService playlistService;
    private final PlaylistImportService playlistImportService;

    @Operation(summary = "Browse every playlist published publicly")
    @GetMapping("/public")
    public ResponseEntity<List<PublicPlaylistSummaryDTO>> getPublicPlaylists() {
        List<PublicPlaylistSummaryDTO> publicPlaylists = playlistService.getPublicPlaylists();
        return ResponseEntity.ok(publicPlaylists);
    }

    @Operation(summary = "Preview a playlist by invite code, no membership or authentication required")
    @GetMapping("/invites/{inviteCode}/preview")
    public ResponseEntity<PlaylistInvitePreviewDTO> getInvitePreview(
            @PathVariable String inviteCode) {
        PlaylistInvitePreviewDTO preview = playlistService.getInvitePreview(inviteCode);
        return ResponseEntity.ok(preview);
    }

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

    @Operation(summary = "Delete a playlist, owner only")
    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Void> deletePlaylist(@PathVariable Long playlistId) {
        playlistService.deletePlaylist(playlistId);
        return ResponseEntity.noContent().build();
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

    @Operation(summary = "Copy the songs of a source playlist into this playlist, skipping songs already present")
    @PostMapping("/{playlistId}/imports")
    public ResponseEntity<ImportFromPlaylistResultDTO> importFromPlaylist(
            @PathVariable Long playlistId,
            @Valid @RequestBody ImportFromPlaylistRequest request) {
        ImportFromPlaylistResultDTO result =
                playlistImportService.importFromPlaylist(playlistId, request.sourcePlaylistId());
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get the playlist's members and their per-playlist identity and grants")
    @GetMapping("/{playlistId}/members")
    public ResponseEntity<List<PlaylistMemberDTO>> getMembers(
            @PathVariable Long playlistId) {
        List<PlaylistMemberDTO> members = playlistService.getMembers(playlistId);
        return ResponseEntity.ok(members);
    }

    @Operation(summary = "Update a member's read, write, and delete grants, owner only")
    @PatchMapping("/{playlistId}/members/{userId}")
    public ResponseEntity<PlaylistMemberDTO> updateMemberGrants(
            @PathVariable Long playlistId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateMembershipGrantsRequest request) {
        PlaylistMemberDTO updatedMember = playlistService.updateMemberGrants(playlistId, userId, request);
        return ResponseEntity.ok(updatedMember);
    }

    @Operation(summary = "Transfer ownership to another member, owner only; the previous owner stays a regular member")
    @PostMapping("/{playlistId}/members/{userId}/promote")
    public ResponseEntity<PlaylistDetailDTO> promoteMember(
            @PathVariable Long playlistId,
            @PathVariable Long userId) {
        PlaylistDetailDTO updatedPlaylist = playlistService.transferOwnership(playlistId, userId);
        return ResponseEntity.ok(updatedPlaylist);
    }

    @Operation(summary = "Kick a member, owner only; the invite still lets them rejoin")
    @DeleteMapping("/{playlistId}/members/{userId}")
    public ResponseEntity<Void> kickMember(
            @PathVariable Long playlistId,
            @PathVariable Long userId) {
        playlistService.kickMember(playlistId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ban a member, owner only; blocks their future join attempts")
    @PostMapping("/{playlistId}/members/{userId}/ban")
    public ResponseEntity<Void> banMember(
            @PathVariable Long playlistId,
            @PathVariable Long userId) {
        playlistService.banMember(playlistId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Publish a playlist publicly, owner only")
    @PostMapping("/{playlistId}/publish")
    public ResponseEntity<PlaylistDetailDTO> publishPlaylist(
            @PathVariable Long playlistId) {
        PlaylistDetailDTO updatedPlaylist = playlistService.publishPlaylist(playlistId);
        return ResponseEntity.ok(updatedPlaylist);
    }

    @Operation(summary = "Unpublish a playlist, owner only")
    @PostMapping("/{playlistId}/unpublish")
    public ResponseEntity<PlaylistDetailDTO> unpublishPlaylist(
            @PathVariable Long playlistId) {
        PlaylistDetailDTO updatedPlaylist = playlistService.unpublishPlaylist(playlistId);
        return ResponseEntity.ok(updatedPlaylist);
    }

    @Operation(summary = "Save a public playlist into the current user's own library, without becoming a member")
    @PostMapping("/{playlistId}/save")
    public ResponseEntity<PublicPlaylistSummaryDTO> savePlaylist(
            @PathVariable Long playlistId) {
        PublicPlaylistSummaryDTO savedPlaylist = playlistService.savePlaylist(playlistId);
        return ResponseEntity.ok(savedPlaylist);
    }

    @Operation(summary = "Unsave a previously saved playlist")
    @DeleteMapping("/{playlistId}/save")
    public ResponseEntity<Void> unsavePlaylist(
            @PathVariable Long playlistId) {
        playlistService.unsavePlaylist(playlistId);
        return ResponseEntity.noContent().build();
    }
}
