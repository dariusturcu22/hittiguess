package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUser(User user);

    boolean existsByUser(User user);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    long countByGroupId(Long groupId);

    @Query("select m from Member m where m.isInVoice = true and m.isConnected = false and m.disconnectedAt < :cutoff")
    List<Member> findInVoiceDisconnectedBefore(@Param("cutoff") Instant cutoff);
}
