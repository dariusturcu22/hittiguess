package org.dariusturcu.backend.service;


import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.UserMapper;
import org.dariusturcu.backend.model.playlist.JoinPlaylistRequest;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistMembership;
import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;
import org.dariusturcu.backend.model.user.UpdateUserRequest;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.model.user.UserDetailDTO;
import org.dariusturcu.backend.repository.PlaylistBanRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;

import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final UserMapper userMapper;
    private final PlaylistMapper playlistMapper;
    private final PlaylistMembershipRepository playlistMembershipRepository;
    private final PlaylistBanRepository playlistBanRepository;

    private List<PlaylistSummaryDTO> getPlaylistSummaries(Long userId) {
        return playlistMembershipRepository.findByUserId(userId).stream()
                .map(PlaylistMembership::getPlaylist)
                .peek(playlist -> playlist.getSongs().size())
                .map(playlistMapper::toSummaryDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDetailDTO getCurrentUser() {
        User contextUser = SecurityUtils.getCurrentUser();
        User user = userRepository.findById(contextUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userMapper.toDetailDTO(user, getPlaylistSummaries(user.getId()));
    }

    @Transactional(readOnly = true)
    public UserDetailDTO getUserById(Long userId) {
        if (!SecurityUtils.isCurrentUser(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, userId));
        return userMapper.toDetailDTO(user, getPlaylistSummaries(userId));
    }

    public UserDetailDTO updateUser(UpdateUserRequest request) {
        User user = SecurityUtils.getCurrentUser();

        if (request.username() != null
                && userRepository.existsUserByUsername(request.username())
                && !user.getUsername().equals(request.username())) {
            throw new ConflictException("Username or email already in use");
        }

        if (request.email() != null
                && userRepository.existsUserByEmail(request.email())
                && !user.getEmail().equals(request.email())) {
            throw new ConflictException("Username or email already in use");
        }

        if (request.username() != null) {
            user.setUsername(request.username());
        }

        if (request.email() != null) {
            user.setEmail(request.email());
        }

        User updatedUser = userRepository.save(user);
        return userMapper.toDetailDTO(updatedUser, getPlaylistSummaries(updatedUser.getId()));
    }

    public void deleteUser() {
        User user = SecurityUtils.getCurrentUser();
        userRepository.delete(user);
    }

    public PlaylistDetailDTO createPlaylist() {
        User user = SecurityUtils.getCurrentUser();

        Playlist playlist = new Playlist();
        playlist.setName("New playlist");
        playlist.setColor("000000");
        playlist.setInviteCode(UUID.randomUUID().toString());
        playlist.setOwner(user);

        PlaylistMembership ownerMembership = new PlaylistMembership();
        ownerMembership.setUser(user);
        ownerMembership.setCanRead(true);
        ownerMembership.setCanWrite(true);
        ownerMembership.setCanDelete(true);
        ownerMembership.setDisplayName(user.getUsername());
        ownerMembership.setAvatarUrl(user.getImageUrl());
        ownerMembership.setJoinedAt(Instant.now());
        playlist.addMembership(ownerMembership);

        Playlist savedPlaylist = playlistRepository.save(playlist);

        return playlistMapper.toDetailDTO(savedPlaylist);
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummaryDTO> getUserPlaylists() {
        User user = SecurityUtils.getCurrentUser();
        return getPlaylistSummaries(user.getId());
    }

    public PlaylistSummaryDTO joinPlaylist(String playlistInviteCode, JoinPlaylistRequest request) {
        User user = SecurityUtils.getCurrentUser();

        Playlist playlist = playlistRepository.findPlaylistByInviteCode(playlistInviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("Invite code {" + playlistInviteCode + "} not found"));

        if (playlistBanRepository.existsByPlaylistIdAndUserId(playlist.getId(), user.getId())) {
            throw new AccessDeniedException("You have been banned from this playlist");
        }

        if (playlistMembershipRepository.existsByPlaylistIdAndUserId(playlist.getId(), user.getId())) {
            throw new ConflictException("User is already a member of this playlist");
        }

        boolean hasCustomDisplayName = request != null && request.displayName() != null && !request.displayName().isBlank();
        boolean hasCustomAvatarUrl = request != null && request.avatarUrl() != null && !request.avatarUrl().isBlank();

        PlaylistMembership membership = new PlaylistMembership();
        membership.setUser(user);
        membership.setCanRead(true);
        membership.setCanWrite(true);
        membership.setCanDelete(true);
        membership.setDisplayName(hasCustomDisplayName ? request.displayName() : user.getUsername());
        membership.setAvatarUrl(hasCustomAvatarUrl ? request.avatarUrl() : user.getImageUrl());
        membership.setJoinedAt(Instant.now());
        playlist.addMembership(membership);

        playlistRepository.save(playlist);

        return playlistMapper.toSummaryDTO(playlist);
    }

    public void leavePlaylist(Long playlistId) {
        User currentUser = SecurityUtils.getCurrentUser();

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));

        PlaylistMembership membership = playlistMembershipRepository.findByPlaylistIdAndUserId(playlistId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST_MEMBER, currentUser.getId()));

        List<PlaylistMembership> remainingMembers = playlist.getMemberships().stream()
                .filter(candidate -> candidate != membership)
                .toList();

        if (remainingMembers.isEmpty()) {
            playlistRepository.delete(playlist);
            return;
        }

        // The owner can leave at any time; a playlist with other members left never goes
        // without an owner, so leadership passes automatically to whoever joined earliest.
        if (playlist.isOwnedBy(currentUser)) {
            PlaylistMembership nextOwnerMembership = remainingMembers.stream()
                    .min(Comparator.comparing(PlaylistMembership::getJoinedAt))
                    .orElseThrow();
            playlist.setOwner(nextOwnerMembership.getUser());
        }

        playlist.removeMembership(membership);
        playlistRepository.save(playlist);
    }
}
