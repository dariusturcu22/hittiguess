package org.dariusturcu.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dariusturcu.backend.model.admin.PipelineControlResultDTO;
import org.dariusturcu.backend.model.admin.PipelineRunDTO;
import org.dariusturcu.backend.model.admin.RecheckSongsRequest;
import org.dariusturcu.backend.model.admin.UpdateCatalogSongRequest;
import org.dariusturcu.backend.pipeline.AdminSongPipelineService;
import org.dariusturcu.backend.security.AdminAccessGuard;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/songs")
@Tag(name = "Admin song pipeline", description = "Admin-only catalog correction, deletion, and pipeline reruns")
public class AdminSongPipelineController {
    private final AdminSongPipelineService songs;
    private final AdminAccessGuard access;

    public AdminSongPipelineController(AdminSongPipelineService songs, AdminAccessGuard access) {
        this.songs = songs; this.access = access;
    }

    @PostMapping("/recheck")
    @Operation(summary = "Queue low-priority recheck runs for songs, skipping those in active games")
    public PipelineControlResultDTO recheckSongs(@RequestBody RecheckSongsRequest request) {
        access.requireAdmin();
        return songs.recheck(request);
    }

    @PostMapping("/{id}/rerun")
    @Operation(summary = "Run the pipeline again for a catalog song")
    public PipelineRunDTO rerunSongPipeline(@PathVariable long id) { access.requireAdmin(); return songs.rerun(id); }

    @PutMapping("/{id}")
    @Operation(summary = "Set the release year or verification status of a catalog song")
    public PipelineControlResultDTO updateCatalogSong(@PathVariable long id, @RequestBody UpdateCatalogSongRequest request) {
        access.requireAdmin(); return songs.update(id, request);
    }

    @DeleteMapping("/{id}/proposal")
    @Operation(summary = "Dismiss the proposed release year for a catalog song")
    public PipelineControlResultDTO dismissProposal(@PathVariable long id) {
        access.requireAdmin();
        return songs.dismissProposal(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a catalog song and its links, refused while an active game uses it")
    public PipelineControlResultDTO deleteCatalogSong(@PathVariable long id) { access.requireAdmin(); return songs.delete(id); }
}
