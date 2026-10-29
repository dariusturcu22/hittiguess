package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.song.AdminCatalogPageDTO;
import org.dariusturcu.backend.model.song.AdminSongDetailDTO;
import org.dariusturcu.backend.model.song.CatalogSongStatus;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.service.AdminSongCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/songs")
@RequiredArgsConstructor
@Tag(name = "Admin song catalog", description = "Admin-only song catalog search and read-only song detail")
public class AdminSongCatalogController {

    private final AdminSongCatalogService adminSongCatalogService;
    private final AdminAccessGuard adminAccessGuard;

    @Operation(summary = "Song catalog, admin only: songs and backlog items without a song yet, newest first, "
            + "with counts per status; search text of 3 or more characters matches title or artist by "
            + "substring, or an exact YouTube ID; 400 for shorter search text, a negative page, or a size "
            + "outside 1 to 25")
    @GetMapping
    public ResponseEntity<AdminCatalogPageDTO> searchCatalog(
            @Parameter(description = "Search text, trimmed; at least 3 characters when present")
            @RequestParam(required = false) String query,
            @Parameter(description = "Only rows with this status; counts ignore it")
            @RequestParam(required = false) CatalogSongStatus status,
            @Parameter(description = "Only rows with a proposed release year")
            @RequestParam(required = false) Boolean proposedYear,
            @Parameter(description = "0-based page number, default 0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Rows per page, default and maximum 25")
            @RequestParam(required = false) Integer size) {
        adminAccessGuard.requireAdmin();
        return ResponseEntity.ok(adminSongCatalogService.search(query, status, proposedYear, page, size));
    }

    @Operation(summary = "Read-only song detail, admin only: catalog fields, stored verification status, "
            + "confidence, and Wikidata sitelinks, open report count, and the backlog history for its YouTube ID")
    @GetMapping("/{songId}")
    public ResponseEntity<AdminSongDetailDTO> songDetail(@PathVariable Long songId) {
        adminAccessGuard.requireAdmin();
        return ResponseEntity.ok(adminSongCatalogService.detail(songId));
    }
}
