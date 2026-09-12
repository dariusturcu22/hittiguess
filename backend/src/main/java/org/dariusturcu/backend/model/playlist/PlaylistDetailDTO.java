package org.dariusturcu.backend.model.playlist;

import jakarta.validation.constraints.NotNull;
import org.dariusturcu.backend.model.song.SongDTO;

import java.util.List;
import java.util.Set;

public record PlaylistDetailDTO(
        @NotNull
        Long id,
        @NotNull
        String name,
        @NotNull
        String color,
        @NotNull
        String inviteCode,
        @NotNull
        int songCount,
        List<SongDTO> songs,
        @NotNull
        Long ownerId,
        @NotNull
        Set<PlaylistMemberDTO> members) {
}
