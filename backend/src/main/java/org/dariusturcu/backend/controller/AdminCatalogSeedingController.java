package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.song.AdminCatalogSeedingRequest;
import org.dariusturcu.backend.model.song.BacklogStatusDTO;
import org.dariusturcu.backend.model.song.EnqueueResultDTO;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.service.CatalogSeedingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalog-seeding")
@RequiredArgsConstructor
@Tag(name = "Admin catalog seeding", description = "Admin-only bulk enqueue and backlog status for growing the song catalog")
public class AdminCatalogSeedingController {

    private final CatalogSeedingService catalogSeedingService;
    private final AdminAccessGuard adminAccessGuard;

    @Operation(summary = "Bulk-enqueue a YouTube playlist link and/or video IDs into the seeding backlog, "
            + "admin only; already-known songs are skipped")
    @PostMapping("/enqueue")
    public ResponseEntity<EnqueueResultDTO> enqueue(@RequestBody AdminCatalogSeedingRequest request) {
        adminAccessGuard.requireAdmin();
        return ResponseEntity.ok(catalogSeedingService.enqueue(request));
    }

    @Operation(summary = "Start draining the backlog now instead of waiting for the daily sweep, admin only; "
            + "409 when a drain is already running")
    @PostMapping("/drain")
    public ResponseEntity<BacklogStatusDTO> drainNow() {
        adminAccessGuard.requireAdmin();
        if (!catalogSeedingService.startDrainNow()) {
            throw new ConflictException("The backlog is already being drained");
        }
        return ResponseEntity.accepted().body(catalogSeedingService.backlogStatus());
    }

    @Operation(summary = "Backlog status, admin only: pending count, processed today, quota remaining, "
            + "and recent rechecks of provisional answers")
    @GetMapping("/status")
    public ResponseEntity<BacklogStatusDTO> backlogStatus() {
        adminAccessGuard.requireAdmin();
        return ResponseEntity.ok(catalogSeedingService.backlogStatus());
    }
}
