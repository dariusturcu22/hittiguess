package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {

    List<Song> findByYoutubeId(String youtubeId);

    @Query("""
            select distinct song
            from Song song
            left join song.artists artist
            where lower(song.title) like lower(concat('%', :keyword, '%'))
               or lower(artist.name) like lower(concat('%', :keyword, '%'))
            """)
    List<Song> searchByTitleOrArtistKeyword(@Param("keyword") String keyword);
}
