package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.song.AlternateYoutubeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AlternateYoutubeIdRepository extends JpaRepository<AlternateYoutubeId, Long> {

    boolean existsByYoutubeId(String youtubeId);

    List<AlternateYoutubeId> findByYoutubeIdIn(Collection<String> youtubeIds);
}
