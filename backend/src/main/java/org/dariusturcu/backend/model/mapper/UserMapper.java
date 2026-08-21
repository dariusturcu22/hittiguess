package org.dariusturcu.backend.model.mapper;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.user.*;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {
    private final PlaylistMapper playlistMapper;

    public UserSummaryDTO toSummaryDTO(User user) {
        return new UserSummaryDTO(
                user.getId(),
                user.getUsername()
        );
    }

    public UserDetailDTO toDetailDTO(User user) {
        return new UserDetailDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getImageUrl(),
                user.getAuthProvider().name(),
                user.getPlaylists().stream()
                        .map(playlistMapper::toSummaryDTO)
                        .toList()
        );
    }

    public User toEntity(CreateUserRequest request) {
        return new User(

        );
    }
}
