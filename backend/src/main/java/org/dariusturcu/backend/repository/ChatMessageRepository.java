package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.group.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // History returns the most recent messages first, bounded by the Pageable the caller
    // supplies; the group-and-created-at index backs this ordering.
    List<ChatMessage> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);

    long countByGroupId(Long groupId);
}
