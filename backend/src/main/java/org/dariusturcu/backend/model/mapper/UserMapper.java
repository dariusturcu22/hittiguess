package org.dariusturcu.backend.model.mapper;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.playlist.PlaylistSummaryDTO;
import org.dariusturcu.backend.model.user.*;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserMapper {

    public UserSummaryDTO toSummaryDTO(User user) {
        return new UserSummaryDTO(
                user.getId(),
                user.getUsername()
        );
    }

    public UserDetailDTO toDetailDTO(User user, List<PlaylistSummaryDTO> playlists) {
        return new UserDetailDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getImageUrl(),
                user.getAuthProvider().name(),
                playlists
        );
    }

    public User toEntity(CreateUserRequest request) {
        return new User(

        );
    }
}
