package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.user.PersonalDataExportDTO;
import org.dariusturcu.backend.model.user.PlaylistMembershipExportDTO;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Builds the GDPR personal-data export: the requesting user's own account fields, every
// playlist they own or are a member of, and every song they've submitted. A separate concern
// from ExportService, which generates printable PDFs of a playlist's song content.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalDataExportService {
    private final UserRepository userRepository;
    private final PlaylistMembershipRepository playlistMembershipRepository;
    private final SongRepository songRepository;
    private final SongMapper songMapper;

    public PersonalDataExportDTO exportCurrentUser() {
        User contextUser = SecurityUtils.getCurrentUser();
        User user = userRepository.findById(contextUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, contextUser.getId()));

        List<PlaylistMembershipExportDTO> playlists = playlistMembershipRepository.findByUserId(user.getId()).stream()
                .map(membership -> toMembershipExportDTO(membership, user))
                .toList();

        List<SongDTO> submittedSongs = songRepository.findByAddedById(user.getId()).stream()
                .map(songMapper::toDTO)
                .toList();

        return new PersonalDataExportDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getImageUrl(),
                user.getAuthProvider() != null ? user.getAuthProvider().name() : null,
                user.getAuthProviderId(),
                playlists,
                submittedSongs
        );
    }

    private PlaylistMembershipExportDTO toMembershipExportDTO(PlaylistMembership membership, User user) {
        return new PlaylistMembershipExportDTO(
                membership.getPlaylist().getId(),
                membership.getPlaylist().getName(),
                membership.getPlaylist().isOwnedBy(user),
                membership.isCanRead(),
                membership.isCanWrite(),
                membership.isCanDelete(),
                membership.getDisplayName(),
                membership.getAvatarUrl(),
                membership.getJoinedAt()
        );
    }
}
