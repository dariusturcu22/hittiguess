package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.Song;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, Long> {
    // A direct exists query against the join table, rather than loading a song's full playlists
    // collection into memory just to check membership in one of them.
    boolean existsByIdAndPlaylistsId(Long songId, Long playlistId);
}
