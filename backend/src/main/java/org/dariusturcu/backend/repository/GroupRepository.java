package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByInviteCode(String inviteCode);

    Optional<Group> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    List<Group> findByExpiresAtBefore(Instant instant);
}
