package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.song.GroundTruthSongDTO;
import org.dariusturcu.backend.model.song.GroundTruthSongsResponse;
import org.dariusturcu.backend.service.GroundTruthService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ground-truth")
@RequiredArgsConstructor
@Tag(name = "Ground-truth data", description = "Public verified song facts for reuse, no YouTube-sourced fields")
public class GroundTruthController {

    private final GroundTruthService groundTruthService;

    @Operation(summary = "Page through verified (artist, title, release year) triples")
    @GetMapping("/songs")
    public ResponseEntity<GroundTruthSongsResponse> verifiedSongs(Pageable pageable) {
        Page<GroundTruthSongDTO> verifiedPage = groundTruthService.verifiedSongs(pageable);
        return ResponseEntity.ok(new GroundTruthSongsResponse(
                verifiedPage.getContent(),
                verifiedPage.getNumber(),
                verifiedPage.getSize(),
                verifiedPage.getTotalElements()));
    }
}
