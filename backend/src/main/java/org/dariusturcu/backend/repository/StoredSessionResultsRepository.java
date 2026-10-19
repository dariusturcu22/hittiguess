package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.session.StoredSessionResults;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredSessionResultsRepository extends JpaRepository<StoredSessionResults, Long> {
}
