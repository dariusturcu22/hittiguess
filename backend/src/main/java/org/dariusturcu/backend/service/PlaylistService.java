package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistBan;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMemberDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.playlist.UpdateMembershipGrantsRequest;
import org.dariusturcu.backend.model.playlist.UpdatePlaylistRequest;
import org.dariusturcu.backend.model.song.CreateSongRequest;
import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.SongDTO;
import org.dariusturcu.backend.model.song.UpdateSongRequest;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;

import org.dariusturcu.backend.repository.SongRepository;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional

public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final PlaylistMapper playlistMapper;
    private final SongMapper songMapper;
    private final PlaylistAccessService playlistAccessService;
    private final PlaylistMembershipRepository playlistMembershipRepository;
    private final PlaylistBanRepository playlistBanRepository;

    // VERIFIED is a pipeline-established lock and NEEDS_REVIEW is an LLM-reconciled year;
    // hand-editing either undermines the trust tier the pipeline already assigned it.
    // UNVERIFIED hasn't been through the pipeline at all yet (today, every song's actual
    // status, since story 40's pipeline doesn't exist to move it anywhere else), so there's
    // no established trust tier to protect there, same as the least-trusted MANUAL_ENTRY tier.
    private static final Set<VerificationStatus> EDITABLE_VERIFICATION_STATUSES =
            Set.of(VerificationStatus.UNVERIFIED, VerificationStatus.MANUAL_ENTRY);

    private Playlist findPlaylist(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));
    }

    private Song findSong(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.SONG, songId));
    }

    private PlaylistMembership findMembership(Long playlistId, Long userId) {
        return playlistMembershipRepository.findByPlaylistIdAndUserId(playlistId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST_MEMBER, userId));
    }

    private void checkSongBelongsToPlaylist(Song song, Long playlistId) {
        if (!song.getPlaylist().getId().equals(playlistId)) {
            throw new ResourceNotFoundException(ResourceType.SONG_NOT_IN_PLAYLIST, song.getId(), playlistId);
        }
    }

    private void checkSongEditable(Song song) {
        if (!EDITABLE_VERIFICATION_STATUSES.contains(song.getVerificationStatus())) {
            throw new AccessDeniedException("This song has been verified and can no longer be edited directly");
        }
    }

    private void checkTargetIsNotOwner(Playlist playlist, Long targetUserId, String action) {
        if (playlist.getOwner().getId().equals(targetUserId)) {
            throw new ConflictException("The playlist owner can't " + action);
        }
    }

    @Transactional(readOnly = true)
    public PlaylistDetailDTO getPlaylist(
            Long playlistId) {

        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireRead(playlist, SecurityUtils.getCurrentUser());
        return playlistMapper.toDetailDTO(playlist);
    }

    public PlaylistDetailDTO updatePlaylist(
            Long playlistId,
            UpdatePlaylistRequest request) {

        Playlist playlist = findPlaylist(playlistId);

        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        playlist = playlistMapper.updateEntity(playlist, request);

        playlistRepository.save(playlist);

        return playlistMapper.toDetailDTO(playlist);
    }

    @Transactional(readOnly = true)
    public SongDTO getSong(
            Long playlistId,
            Long songId) {

        Playlist playlist = findPlaylist(playlistId);

        playlistAccessService.requireRead(playlist, SecurityUtils.getCurrentUser());
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);

        return songMapper.toDTO(song);
    }

    public SongDTO createSong(
            Long playlistId,
            CreateSongRequest request) {

        Playlist playlist = findPlaylist(playlistId);
        User user = SecurityUtils.getCurrentUser();
        playlistAccessService.requireWrite(playlist, user);

        Song newSong = songMapper.toEntity(request);
        newSong.setPlaylist(playlist);
        newSong.setAddedBy(user);

        Song savedSong = songRepository.save(newSong);

        return songMapper.toDTO(savedSong);
    }

    public SongDTO updateSong(
            Long playlistId,
            Long songId,
            UpdateSongRequest request) {

        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireWrite(playlist, SecurityUtils.getCurrentUser());
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);
        checkSongEditable(song);

        song = songMapper.updateEntity(song, request);

        playlistRepository.save(playlist);

        return songMapper.toDTO(song);
    }

    public void deleteSong(
            Long playlistId,
            Long songId) {

        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireDelete(playlist, SecurityUtils.getCurrentUser());
        Song song = findSong(songId);

        checkSongBelongsToPlaylist(song, playlistId);

        playlist.removeSong(song);

        playlistRepository.save(playlist);
    }

    @Transactional(readOnly = true)
    public List<PlaylistMemberDTO> getMembers(Long playlistId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireRead(playlist, SecurityUtils.getCurrentUser());

        Long ownerId = playlist.getOwner().getId();
        return playlist.getMemberships().stream()
                .map(membership -> playlistMapper.toMemberDTO(membership, membership.getUser().getId().equals(ownerId)))
                .toList();
    }

    public PlaylistMemberDTO updateMemberGrants(Long playlistId, Long targetUserId, UpdateMembershipGrantsRequest request) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        checkTargetIsNotOwner(playlist, targetUserId, "have their own grants changed");

        PlaylistMembership membership = findMembership(playlistId, targetUserId);

        if (request.canRead() != null) {
            membership.setCanRead(request.canRead());
        }
        if (request.canWrite() != null) {
            membership.setCanWrite(request.canWrite());
        }
        if (request.canDelete() != null) {
            membership.setCanDelete(request.canDelete());
        }

        PlaylistMembership savedMembership = playlistMembershipRepository.save(membership);
        return playlistMapper.toMemberDTO(savedMembership, false);
    }

    public void kickMember(Long playlistId, Long targetUserId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        checkTargetIsNotOwner(playlist, targetUserId, "be kicked");

        PlaylistMembership membership = findMembership(playlistId, targetUserId);

        playlist.removeMembership(membership);
        playlistRepository.save(playlist);
    }

    public void banMember(Long playlistId, Long targetUserId) {
        Playlist playlist = findPlaylist(playlistId);
        playlistAccessService.requireOwner(playlist, SecurityUtils.getCurrentUser());
        checkTargetIsNotOwner(playlist, targetUserId, "be banned");

        PlaylistMembership membership = findMembership(playlistId, targetUserId);
        User bannedUser = membership.getUser();

        playlist.removeMembership(membership);
        playlistRepository.save(playlist);

        if (!playlistBanRepository.existsByPlaylistIdAndUserId(playlistId, targetUserId)) {
            PlaylistBan ban = new PlaylistBan();
            ban.setPlaylist(playlist);
            ban.setUser(bannedUser);
            ban.setBannedAt(Instant.now());
            playlistBanRepository.save(ban);
        }
    }
}
