package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PlaylistMapper {
    private final SongMapper songMapper;
    private final UserMapper userMapper;

    public PlaylistMapper(SongMapper songMapper, @Lazy UserMapper userMapper) {
        this.songMapper = songMapper;
        this.userMapper = userMapper;
    }

    public PlaylistSummaryDTO toSummaryDTO(Playlist playlist) {
        return new PlaylistSummaryDTO(
                playlist.getId(),
                playlist.getName(),
                playlist.getColor(),
                playlist.getSongCount()
        );
    }

    public PlaylistDetailDTO toDetailDTO(Playlist playlist) {
        return new PlaylistDetailDTO(
                playlist.getId(),
                playlist.getName(),
                playlist.getColor(),
                playlist.getInviteCode(),
                playlist.getSongCount(),
                playlist.getSongs().stream()
                        .map(songMapper::toDTO)
                        .toList(),
                playlist.getUsers().stream()
                        .map(userMapper::toSummaryDTO)
                        .collect(Collectors.toSet())
        );
    }

    public Playlist updateEntity(Playlist playlist, UpdatePlaylistRequest request) {
        if (request.name() != null && !request.name().isBlank()) {
            playlist.setName(request.name());
        }
        if (request.color() != null) {
            playlist.setColor(request.color());
        }
        return playlist;
    }
}
