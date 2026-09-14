package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {

    List<Song> findByYoutubeId(String youtubeId);

    List<Song> findByAddedById(Long userId);

    @Query("""
            select distinct song
            from Song song
            left join song.artists artist
            where lower(song.title) like lower(concat('%', :keyword, '%'))
               or lower(artist.name) like lower(concat('%', :keyword, '%'))
            """)
    List<Song> searchByTitleOrArtistKeyword(@Param("keyword") String keyword);

    // A direct exists query against the join table, rather than loading a song's full playlists
    // collection into memory just to check membership in one of them.
    boolean existsByIdAndPlaylistsId(Long songId, Long playlistId);
}
