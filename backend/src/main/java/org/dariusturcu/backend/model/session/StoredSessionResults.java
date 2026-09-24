package org.dariusturcu.backend.model.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "session_results")
public class StoredSessionResults {

    @Id
    @Column(name = "group_id")
    private Long groupId;

    // The SessionResultsDTO serialized as JSON.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String results;

    @Column(name = "stored_at", nullable = false)
    private Instant storedAt;
}
