package org.dariusturcu.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.dariusturcu.backend.model.user.User;

import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
@Data
public class EmailVerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    // Set when the token confirms an email change: the address only replaces the account's
    // current one once this token is redeemed.
    @Column(name = "pending_email")
    private String pendingEmail;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
