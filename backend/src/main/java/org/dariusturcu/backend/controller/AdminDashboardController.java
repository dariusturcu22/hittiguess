package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.admin.AdminDashboardDTO;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.service.AdminDashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin dashboard", description = "Admin-only dashboard totals")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminAccessGuard adminAccessGuard;

    @Operation(summary = "Admin dashboard, admin only: catalog counts per status with the total and no filter, "
            + "queue rows waiting (PENDING) and in progress (PROCESSING) across every origin, and the same "
            + "usage view as the usage endpoint")
    @GetMapping
    public ResponseEntity<AdminDashboardDTO> dashboard() {
        adminAccessGuard.requireAdmin();
        return ResponseEntity.ok(adminDashboardService.dashboard());
    }
}
