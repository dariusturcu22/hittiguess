package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.usage.AdminUsageDTO;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.dariusturcu.backend.service.ApiUsageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/usage")
@RequiredArgsConstructor
@Tag(name = "Admin usage", description = "Admin-only external API usage and estimated cost")
public class AdminUsageController {

    private final ApiUsageService apiUsageService;
    private final AdminAccessGuard adminAccessGuard;

    @Operation(summary = "External API usage, admin only: YouTube quota units for the current Pacific quota day, "
            + "and DeepInfra and OpenAI calls, tokens, and estimated cost for today and the month in UTC")
    @GetMapping
    public ResponseEntity<AdminUsageDTO> usage() {
        adminAccessGuard.requireAdmin();
        return ResponseEntity.ok(apiUsageService.usage());
    }
}
