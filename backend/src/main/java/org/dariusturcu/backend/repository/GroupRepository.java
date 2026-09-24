package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.group.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByInviteCode(String inviteCode);

    Optional<Group> findByJoinCode(String joinCode);

    // Joins lock the group row so concurrent joins check the member cap one at a time. Only
    // for a read-write transaction: Postgres refuses a row lock in a read-only one.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select group from Group group where group.inviteCode = :inviteCode")
    Optional<Group> findByInviteCodeForUpdate(String inviteCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select group from Group group where group.joinCode = :joinCode")
    Optional<Group> findByJoinCodeForUpdate(String joinCode);

    boolean existsByJoinCode(String joinCode);

    List<Group> findByExpiresAtBefore(Instant instant);
}
