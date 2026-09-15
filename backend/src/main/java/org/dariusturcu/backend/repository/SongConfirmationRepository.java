package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.SongConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SongConfirmationRepository extends JpaRepository<SongConfirmation, Long> {

    boolean existsByUserIdAndSongId(Long userId, Long songId);

    long countBySongId(Long songId);

    List<SongConfirmation> findBySongId(Long songId);
}
