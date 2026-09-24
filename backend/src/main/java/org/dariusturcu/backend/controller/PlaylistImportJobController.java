package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.playlist.PlaylistImportJobDTO;
import org.dariusturcu.backend.model.playlist.StartPlaylistImportRequest;
import org.dariusturcu.backend.service.PlaylistImportJobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
@Tag(name = "Playlist import jobs", description = "Runs YouTube imports into a playlist in the background while the user keeps browsing")
public class PlaylistImportJobController {

    private final PlaylistImportJobService playlistImportJobService;

    @Operation(summary = "Start a background import into a playlist, returning the job id immediately")
    @PostMapping("/{playlistId}/import-jobs")
    public ResponseEntity<Map<String, String>> startImport(
            @PathVariable Long playlistId,
            @RequestBody StartPlaylistImportRequest request) {
        String jobId = playlistImportJobService.startImport(playlistId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("importJobId", jobId));
    }

    @Operation(summary = "Read the playlist's running import with per-video progress, if any")
    @GetMapping("/{playlistId}/import-jobs/active")
    public ResponseEntity<PlaylistImportJobDTO> activeImport(@PathVariable Long playlistId) {
        return playlistImportJobService.findActiveImport(playlistId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Read one import job of the playlist, running or finished, with per-video results")
    @GetMapping("/{playlistId}/import-jobs/{importJobId}")
    public ResponseEntity<PlaylistImportJobDTO> importJob(@PathVariable Long playlistId, @PathVariable String importJobId) {
        return ResponseEntity.ok(playlistImportJobService.findImport(playlistId, importJobId));
    }
}
