package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.PasswordResetToken;
import org.dariusturcu.backend.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    @Modifying
    @Transactional
    void deletePasswordResetTokenByUser(User user);
}
