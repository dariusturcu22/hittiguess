package org.dariusturcu.backend.service;


import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.UserMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.playlist.PlaylistDetailDTO;
import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;
import org.dariusturcu.backend.model.user.UpdateUserRequest;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.model.user.UserDetailDTO;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;

import org.dariusturcu.backend.security.util.SecurityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public UserDetailDTO getCurrentUser() {
        User contextUser = SecurityUtils.getCurrentUser();
        User user = userRepository.findById(contextUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.getPlaylists().forEach(playlist -> playlist.getSongs().size());
        return userMapper.toDetailDTO(user);
    }

    @Transactional(readOnly = true)
    public UserDetailDTO getUserById(Long userId) {
        if (!SecurityUtils.isCurrentUser(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, userId));
        user.getPlaylists().size();
        return userMapper.toDetailDTO(user);
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
        user.getPlaylists().size();
        return userMapper.toDetailDTO(updatedUser);
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
        playlist.addUser(user);

        Playlist savedPlaylist = playlistRepository.save(playlist);

        user.getPlaylists().add(savedPlaylist);
        userRepository.save(user);

        return playlistMapper.toDetailDTO(playlist);
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummaryDTO> getUserPlaylists() {
        User user = SecurityUtils.getCurrentUser();
        List<Playlist> playlists = playlistRepository.findPlaylistsByUsers(user);
        playlists.forEach(playlist -> playlist.getSongs().size());
        return playlists.stream()
                .map(playlistMapper::toSummaryDTO)
                .toList();
    }

    public PlaylistSummaryDTO joinPlaylist(String playlistInviteCode) {
        User user = SecurityUtils.getCurrentUser();

        Playlist playlist = playlistRepository.findPlaylistByInviteCode(playlistInviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("Invite code {" + playlistInviteCode + "} not found"));

        boolean alreadyMember = user.getPlaylists().stream()
                .anyMatch(p -> p.getId().equals(playlist.getId()));

        if (alreadyMember) {
            throw new RuntimeException("User is already a member of this playlist");
        }

        user.getPlaylists().add(playlist);
        playlist.addUser(user);
        userRepository.save(user);

        return playlistMapper.toSummaryDTO(playlist);
    }

    public void leavePlaylist(Long playlistId) {
        User contextUser = SecurityUtils.getCurrentUser();
        User user = userRepository.findById(contextUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.USER, contextUser.getId()));

        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.PLAYLIST, playlistId));

        boolean removed = user.getPlaylists().removeIf(p -> p.getId().equals(playlistId));

        if (!removed) {
            throw new RuntimeException("User is not a member of this playlist");
        }

        playlist.removeUser(user);

        if (playlist.getUserCount() == 0) {
            playlistRepository.delete(playlist);
        }

        userRepository.save(user);
    }
}
