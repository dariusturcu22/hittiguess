package org.dariusturcu.backend.model.session;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_result_players", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id")
    private Set<Long> playerUserIds = new HashSet<>();
}
