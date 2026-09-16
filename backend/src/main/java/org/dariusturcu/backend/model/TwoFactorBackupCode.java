package org.dariusturcu.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.dariusturcu.backend.model.user.User;

@Entity
@Table(name = "two_factor_backup_codes")
@Data
public class TwoFactorBackupCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private boolean used = false;
}
