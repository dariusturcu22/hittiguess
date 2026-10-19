package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.song.AdminReviewItemDTO;
import org.dariusturcu.backend.model.song.ResolveReportRequest;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.service.SongReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/song-reports")
@RequiredArgsConstructor
@Tag(name = "Admin song report review", description = "Admin-only review queue over reported and confirmed songs, and report resolution")
public class AdminSongReportController {

    private final SongReportService songReportService;
    private final AdminAccessGuard adminAccessGuard;

    @Operation(summary = "Review queue ordered by priority tier, admin only; every ranking signal is exposed rather than a single score")
    @GetMapping("/queue")
    public ResponseEntity<List<AdminReviewItemDTO>> reviewQueue() {
        adminAccessGuard.requireAdmin();
        return ResponseEntity.ok(songReportService.reviewQueue());
    }

    @Operation(summary = "Resolve a song's open reports, admin only; applies the admin's chosen year and verification status unconditionally, overriding even a locked, VERIFIED song")
    @PostMapping("/{songId}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable Long songId, @Valid @RequestBody ResolveReportRequest request) {
        adminAccessGuard.requireAdmin();
        songReportService.resolveReport(songId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Dismiss a song's open reports, admin only")
    @PostMapping("/{songId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable Long songId) {
        adminAccessGuard.requireAdmin();
        songReportService.dismissReports(songId);
        return ResponseEntity.noContent().build();
    }
}
