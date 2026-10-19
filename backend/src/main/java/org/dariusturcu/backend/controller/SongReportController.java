package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.song.SubmitReportRequest;
import org.dariusturcu.backend.service.SongReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/songs/{songId}")
@RequiredArgsConstructor
@Tag(name = "Community song reports", description = "Report a song's metadata as wrong, or confirm it is correct")
public class SongReportController {

    private final SongReportService songReportService;

    @Operation(summary = "Report a song's metadata as wrong, available to any authenticated user on any card")
    @PostMapping("/reports")
    public ResponseEntity<Void> submitReport(@PathVariable Long songId,
                                             @Valid @RequestBody SubmitReportRequest request) {
        songReportService.submitReport(songId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Confirm a song's metadata is correct; available only on cards not yet at the verified tier")
    @PostMapping("/confirmations")
    public ResponseEntity<Void> submitConfirmation(@PathVariable Long songId) {
        songReportService.submitConfirmation(songId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
