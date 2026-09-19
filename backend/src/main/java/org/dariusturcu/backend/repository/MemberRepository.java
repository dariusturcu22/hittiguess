package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUser(User user);

    boolean existsByUser(User user);
}
