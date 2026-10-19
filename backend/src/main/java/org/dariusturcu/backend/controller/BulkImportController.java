package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.song.BulkImportRequest;
import org.dariusturcu.backend.model.song.BulkImportResultDTO;
import org.dariusturcu.backend.model.song.ExpandPlaylistLinkRequest;
import org.dariusturcu.backend.service.BulkImportService;
import org.dariusturcu.backend.service.PlaylistExpansionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bulk-import")
@RequiredArgsConstructor
@Tag(name = "Bulk import", description = "User on-the-spot bulk import of a YouTube playlist link or a list of video IDs")
public class BulkImportController {

    private final BulkImportService bulkImportService;
    private final PlaylistExpansionService playlistExpansionService;

    @Operation(summary = "Import a list of YouTube video IDs or links immediately, optionally linking every "
            + "resolved or already-known song into a target playlist")
    @PostMapping
    public ResponseEntity<BulkImportResultDTO> importImmediately(@RequestBody BulkImportRequest request) {
        return ResponseEntity.ok(bulkImportService.importImmediately(request));
    }

    @Operation(summary = "Expand a YouTube playlist link or id into its video ids for review before importing")
    @PostMapping("/expand")
    public ResponseEntity<List<String>> expandPlaylist(@RequestBody ExpandPlaylistLinkRequest request) {
        return ResponseEntity.ok(playlistExpansionService.expandPlaylist(request.playlistLink()));
    }
}
