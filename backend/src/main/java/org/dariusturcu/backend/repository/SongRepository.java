package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.Song;
import org.dariusturcu.backend.model.song.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Song song set song.durationSeconds = null where song.durationFetchedAt <= :cutoff")
    int clearExpiredDurations(@Param("cutoff") java.time.Instant cutoff);

    @Query("select distinct song.youtubeId from Song song where song.durationFetchedAt <= :cutoff and song.youtubeId is not null order by song.youtubeId")
    List<String> findDurationRefreshCandidates(@Param("cutoff") java.time.Instant cutoff, Pageable pageable);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Song song set song.durationSeconds = :durationSeconds, song.durationFetchedAt = :checkedAt where song.youtubeId = :youtubeId and song.durationFetchedAt <= :cutoff")
    int updateRefreshedDuration(@Param("youtubeId") String youtubeId, @Param("durationSeconds") Integer durationSeconds,
                               @Param("checkedAt") java.time.Instant checkedAt, @Param("cutoff") java.time.Instant cutoff);

    List<Song> findByYoutubeId(String youtubeId);

    // Holds a Postgres advisory lock until the surrounding transaction ends, so work keyed on
    // the same string runs one transaction at a time, here and across replicas.
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:lockKey))) as acquired", nativeQuery = true)
    int acquireTransactionLock(@Param("lockKey") String lockKey);

    List<Song> findByYoutubeIdIn(Collection<String> youtubeIds);

    List<Song> findByVerificationStatus(VerificationStatus verificationStatus);

    Page<Song> findByVerificationStatus(VerificationStatus verificationStatus, Pageable pageable);

    @Query("select song.youtubeId from Song song where song.youtubeId in :youtubeIds")
    List<String> findKnownYoutubeIds(@Param("youtubeIds") Collection<String> youtubeIds);

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
