package org.dariusturcu.backend.model.user;

import org.dariusturcu.backend.model.song.SongDTO;

import java.util.List;

// The full personal-data dump for the GDPR export endpoint: every field this project holds
// about one account, distinct from UserDetailDTO which only carries what the app's own UI needs.
public record PersonalDataExportDTO(
        Long id,
        String username,
        String email,
        String imageUrl,
        String authProvider,
        String authProviderId,
        List<PlaylistMembershipExportDTO> playlists,
        List<SongDTO> submittedSongs
) {
}
