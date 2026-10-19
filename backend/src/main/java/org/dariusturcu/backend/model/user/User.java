package org.dariusturcu.backend.model.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(unique = true, nullable = true)
    private String email;

    private String password;

    private String imageUrl;

    @Column(name = "avatar_image")
    private byte[] avatarImage;

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    private String authProviderId;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private boolean emailVerified = false;

    // Holds the pending secret from /auth/2fa/setup before twoFactorEnabled flips true on
    // /auth/2fa/confirm, and the confirmed secret afterward. Never returned by any DTO. See
    // DECISIONS.md for why this is a plain column rather than encrypted at rest.
    private String totpSecret;

    @Column(nullable = false)
    private boolean twoFactorEnabled = false;

    // The TOTP time step of the last accepted code; a code for this step or an earlier
    // one is refused, so a code can't be used twice within its validity window.
    private Long totpLastUsedStep;

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private Instant loginLockedUntil;
}
