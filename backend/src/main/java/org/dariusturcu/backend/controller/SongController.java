package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.RecommendedSongsDTO;
import org.dariusturcu.backend.service.SongService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RestController
@RequestMapping("/api/songs")
@RequiredArgsConstructor
@Tag(name = "Song search", description = "Handles catalog-wide song search by keyword or YouTube link/ID")
public class SongController {
    private final SongService songService;

    @Operation(summary = "Search the song catalog by artist/title keyword or YouTube link/ID")
    @GetMapping("/search")
    public ResponseEntity<List<SongDTO>> searchSongs(
            @RequestParam String query) {
        List<SongDTO> results = songService.searchCatalog(query);
        return ResponseEntity.ok(results);
    }

    @Operation(summary = "Recommend newest verified catalog songs, paged")
    @GetMapping("/recommended")
    public ResponseEntity<RecommendedSongsDTO> recommendSongs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(songService.recommendSongs(page, size));
    }
}
