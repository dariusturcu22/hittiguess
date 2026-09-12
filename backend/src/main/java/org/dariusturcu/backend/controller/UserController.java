package org.dariusturcu.backend.controller;


import jakarta.validation.Valid;
import org.dariusturcu.backend.model.playlist.JoinPlaylistRequest;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;
import org.dariusturcu.backend.model.user.UpdateUserRequest;
import org.dariusturcu.backend.model.user.UserDetailDTO;
import org.dariusturcu.backend.model.user.UserSummaryDTO;
import org.dariusturcu.backend.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User management", description = "Handles operations regarding user management")

public class UserController {
    private final UserService userService;

    @Operation(summary = "Get current user information")
    @GetMapping("/me")
    public ResponseEntity<UserDetailDTO> getCurrentUser() {
        UserDetailDTO user = userService.getCurrentUser();
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Get user information by ID")
    @GetMapping("/{userId}")
    public ResponseEntity<UserDetailDTO> getUser(
            @PathVariable Long userId) {
        UserDetailDTO user = userService.getUserById(userId);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Update current user information")
    @PatchMapping("/me")
    public ResponseEntity<UserDetailDTO> updateUser(
            @Valid @RequestBody UpdateUserRequest request) {
        UserDetailDTO updatedUser = userService.updateUser(request);
        return ResponseEntity.ok().body(updatedUser);
    }

    @Operation(summary = "Delete current user")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser() {
        userService.deleteUser();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Create new playlist for current user")
    @PostMapping("/me/playlists")
    public ResponseEntity<PlaylistDetailDTO> createPlaylist() {
        PlaylistDetailDTO newPlaylist = userService.createPlaylist();
        return ResponseEntity.status(HttpStatus.CREATED).body(newPlaylist);
    }

    @Operation(summary = "Get all playlists of current user")
    @GetMapping("/me/playlists")
    public ResponseEntity<List<PlaylistSummaryDTO>> getUserPlaylists() {
        List<PlaylistSummaryDTO> userPlaylists = userService.getUserPlaylists();
        return ResponseEntity.ok(userPlaylists);
    }

    @Operation(summary = "Join an existing playlist, optionally with a per-playlist display name and avatar")
    @PostMapping("/me/playlists/{playlistInviteCode}")
    public ResponseEntity<PlaylistSummaryDTO> joinPlaylist(
            @PathVariable String playlistInviteCode,
            @RequestBody(required = false) JoinPlaylistRequest request) {
        PlaylistSummaryDTO joinedPlaylist = userService.joinPlaylist(playlistInviteCode, request);
        return ResponseEntity.ok(joinedPlaylist);
    }

    @Operation(summary = "Leave playlist")
    @DeleteMapping("/me/playlists/{playlistId}")
    public ResponseEntity<Void> leavePlaylist(
            @PathVariable Long playlistId) {
        userService.leavePlaylist(playlistId);
        return ResponseEntity.noContent().build();
    }
}
