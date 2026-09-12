package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.session.Guess;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuessRepository extends JpaRepository<Guess, Long> {
}
