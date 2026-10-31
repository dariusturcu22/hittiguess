package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.RefreshToken;
import org.dariusturcu.backend.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Transactional
    void deleteRefreshTokenByUser(User user);

    @Modifying
    @Transactional
    int deleteByExpiresAtBefore(Instant cutoff);
}
