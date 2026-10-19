package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.TwoFactorBackupCode;
import org.dariusturcu.backend.model.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TwoFactorBackupCodeRepository extends JpaRepository<TwoFactorBackupCode, Long> {
    List<TwoFactorBackupCode> findByUserAndUsedFalse(User user);

    @Modifying
    @Transactional
    void deleteByUser(User user);
}
