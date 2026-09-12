package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.session.PlayerCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerCardRepository extends JpaRepository<PlayerCard, Long> {
}
