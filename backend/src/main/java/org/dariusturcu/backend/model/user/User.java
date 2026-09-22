package org.dariusturcu.backend.model.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
}
