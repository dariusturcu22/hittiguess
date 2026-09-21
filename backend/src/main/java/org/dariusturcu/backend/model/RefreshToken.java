package org.dariusturcu.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.dariusturcu.backend.model.user.User;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Data
public class RefreshToken {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    // Extends the refresh lifetime to the remembered window instead of the
    // standard one; preserved across refresh rotation so a remembered session
    // stays remembered until logout or expiry.
    @Column(nullable = false)
    private boolean rememberMe;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
