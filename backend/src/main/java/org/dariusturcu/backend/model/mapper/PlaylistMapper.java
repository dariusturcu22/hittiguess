package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistInvitePreviewDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMemberDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;
import org.dariusturcu.backend.model.playlist.PublicPlaylistSummaryDTO;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;
import org.dariusturcu.backend.model.user.UserSummaryDTO;

import org.dariusturcu.backend.model.song.Song;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PlaylistMapper {
    private static final int COVER_PREVIEW_SONG_COUNT = 4;

    private final SongMapper songMapper;

    private List<String> previewYoutubeIds(Playlist playlist) {
        return playlist.getSongs().stream()
                .limit(COVER_PREVIEW_SONG_COUNT)
                .map(Song::getYoutubeId)
                .toList();
    }

    public PlaylistSummaryDTO toSummaryDTO(Playlist playlist) {
        return new PlaylistSummaryDTO(
                playlist.getId(),
                playlist.getName(),
                playlist.getColor(),
                playlist.getSongCount(),
                previewYoutubeIds(playlist)
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
                        .collect(Collectors.toSet()),
                playlist.isPublic()
        );
    }

    public PublicPlaylistSummaryDTO toPublicSummaryDTO(Playlist playlist) {
        return new PublicPlaylistSummaryDTO(
                playlist.getId(),
                playlist.getName(),
                playlist.getColor(),
                playlist.getSongCount(),
                new UserSummaryDTO(playlist.getOwner().getId(), playlist.getOwner().getUsername()),
                previewYoutubeIds(playlist)
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

    public PlaylistInvitePreviewDTO toInvitePreviewDTO(Playlist playlist) {
        Long ownerId = playlist.getOwner().getId();
        return new PlaylistInvitePreviewDTO(
                playlist.getName(),
                playlist.getColor(),
                playlist.getSongCount(),
                playlist.getMemberships().stream()
                        .map(membership -> toMemberDTO(membership, membership.getUser().getId().equals(ownerId)))
                        .toList()
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
