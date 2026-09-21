package org.dariusturcu.backend.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {
    // Marks a token issued mid-login for a twoFactorEnabled account: narrowly scoped to
    // /auth/2fa/verify, and rejected by name if presented anywhere a real access token is
    // expected, since JwtAuthenticationFilter never inspects this claim itself.
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String TWO_FACTOR_PENDING_TOKEN_TYPE = "two_factor_pending";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    @Value("${jwt.refresh-expiration-remember-me}")
    private Long rememberedRefreshExpiration;

    @Value("${jwt.two-factor-pending-expiration}")
    private Long twoFactorPendingExpiration;

    public SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();

        if (userDetails instanceof UserPrincipal) {
            User user = ((UserPrincipal) userDetails).getUser();
            claims.put("userId", user.getId());
            claims.put("email", user.getEmail());
            claims.put("role", user.getRole());
            return createToken(claims, user.getEmail());
        }

        return createToken(claims, userDetails.getUsername());
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole());
        return createToken(claims, user.getEmail());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        if (userDetails instanceof UserPrincipal principal) {
            return email.equals(principal.getUser().getEmail()) && !isTokenExpired(token);
        }
        return (email.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public String generateTwoFactorPendingToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put(TOKEN_TYPE_CLAIM, TWO_FACTOR_PENDING_TOKEN_TYPE);
        return Jwts.builder()
                .claims(claims)
                .subject(user.getEmail())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + twoFactorPendingExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    // Throws io.jsonwebtoken.JwtException (including ExpiredJwtException) for a malformed,
    // tampered, or expired token, same as every other extractClaim caller relies on.
    public boolean isTwoFactorPendingToken(String token) {
        String tokenType = extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class));
        return TWO_FACTOR_PENDING_TOKEN_TYPE.equals(tokenType);
    }

    public long getExpirationSeconds() {
        return expiration / 1000;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpiration / 1000;
    }

    public long getRememberedRefreshExpirationSeconds() {
        return rememberedRefreshExpiration / 1000;
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }
}
