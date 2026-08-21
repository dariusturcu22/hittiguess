package org.dariusturcu.backend.service;

import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.RefreshToken;
import org.dariusturcu.backend.model.auth.AuthResult;
import org.dariusturcu.backend.model.auth.LoginRequest;
import org.dariusturcu.backend.model.auth.RegisterRequest;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.RefreshTokenRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.security.util.TokenHasher;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthResult register(RegisterRequest request) {
        boolean usernameOrEmailTaken = userRepository.existsUserByUsername(request.username())
                || userRepository.existsUserByEmail(request.email());

        if (usernameOrEmailTaken) {
            throw new RuntimeException("Username or email already in use");
            // TODO change to custom exception
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER);

        User savedUser = userRepository.save(user);

        String accessToken = jwtUtil.generateToken(savedUser);
        String refreshToken = createAndSaveRefreshToken(savedUser);

        return new AuthResult(
                accessToken,
                refreshToken,
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail()
        );
    }

    public AuthResult login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        User user = userRepository.findUserByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));
        // TODO change to custom exception

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = createAndSaveRefreshToken(user);

        return new AuthResult(
                accessToken,
                refreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
    }

    public AuthResult refreshTokens(String refreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(TokenHasher.hash(refreshToken))
                .orElseThrow(() -> new RuntimeException("Invalid refresh accessToken"));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new RuntimeException("Refresh accessToken expired, please log in again");
        }

        User user = storedToken.getUser();
        refreshTokenRepository.delete(storedToken);

        String newAccessToken = jwtUtil.generateToken(user);
        String newRefreshToken = createAndSaveRefreshToken(user);

        return new AuthResult(
                newAccessToken,
                newRefreshToken,
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
    }

    public void revokeRefreshToken(String refreshToken) {
        refreshTokenRepository.findByToken(TokenHasher.hash(refreshToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    @Modifying
    public String createAndSaveRefreshToken(User user) {
        refreshTokenRepository.deleteRefreshTokenByUser(user);
        refreshTokenRepository.flush();
        String tokenValue = jwtUtil.generateRefreshToken();
        RefreshToken newToken = new RefreshToken();
        newToken.setToken(TokenHasher.hash(tokenValue));
        newToken.setUser(user);
        newToken.setExpiresAt(Instant.now().plusSeconds(jwtUtil.getRefreshExpirationSeconds()));
        refreshTokenRepository.save(newToken);
        return tokenValue;
    }
}
