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

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    private String authProviderId;

    @Enumerated(EnumType.STRING)
    private Role role;
}
