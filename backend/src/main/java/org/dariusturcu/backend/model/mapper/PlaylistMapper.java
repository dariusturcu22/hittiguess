package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMemberDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PlaylistMapper {
    private final SongMapper songMapper;

    public PlaylistSummaryDTO toSummaryDTO(Playlist playlist) {
        return new PlaylistSummaryDTO(
                playlist.getId(),
                playlist.getName(),
                playlist.getColor(),
                playlist.getSongCount()
        );
    }

    public PlaylistDetailDTO toDetailDTO(Playlist playlist) {
        Long ownerId = playlist.getOwner().getId();
        return new PlaylistDetailDTO(
                playlist.getId(),
                playlist.getName(),
                playlist.getColor(),
                playlist.getInviteCode(),
                playlist.getSongCount(),
                playlist.getSongs().stream()
                        .map(songMapper::toDTO)
                        .toList(),
                ownerId,
                playlist.getMemberships().stream()
                        .map(membership -> toMemberDTO(membership, membership.getUser().getId().equals(ownerId)))
                        .collect(Collectors.toSet())
        );
    }

    public PlaylistMemberDTO toMemberDTO(PlaylistMembership membership, boolean owner) {
        return new PlaylistMemberDTO(
                membership.getUser().getId(),
                membership.getUser().getUsername(),
                membership.getDisplayName(),
                membership.getAvatarUrl(),
                owner,
                membership.isCanRead(),
                membership.isCanWrite(),
                membership.isCanDelete(),
                membership.getJoinedAt()
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
