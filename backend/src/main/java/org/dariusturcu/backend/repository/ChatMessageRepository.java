package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.group.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // History returns the most recent messages first, bounded by the Pageable the caller
    // supplies; the group-and-created-at index backs this ordering.
    List<ChatMessage> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);

    long countByGroupId(Long groupId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    void deleteBySenderId(Long senderId);

    List<ChatMessage> findBySenderIdOrderByCreatedAtAsc(Long senderId);
}
