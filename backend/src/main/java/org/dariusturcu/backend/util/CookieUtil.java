package org.dariusturcu.backend.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class CookieUtil {
    public static final String SESSION_HINT_COOKIE_NAME = "session_hint";
    private static final String SESSION_HINT_COOKIE_VALUE = "1";

    @Value("${app.env}")
    private String appEnv;


    public ResponseCookie createCookie(
            String token,
            long maxAgeSeconds,
            String name,
            String path
    ) {
        boolean isProduction = appEnv.equals("prod");
        return ResponseCookie.from(name, token)
                .httpOnly(true)
                .secure(isProduction)
                .sameSite(isProduction ? "None" : "Strict")
                .path(path)
                .maxAge(maxAgeSeconds)
                .build();
    }

    public ResponseCookie createAccessTokenCookie(String token, long maxAgeSeconds) {
        return createCookie(token, maxAgeSeconds, "access_token", "/");
    }

    public ResponseCookie createRefreshTokenCookie(String token, long maxAgeSeconds) {
        return createCookie(token, maxAgeSeconds, "refresh_token", "/auth/refresh");
    }

    // refresh_token itself is scoped to /auth/refresh so it's only ever sent
    // where it's needed, which also keeps it invisible to Next.js's proxy
    // middleware on ordinary route requests. This cookie mirrors the
    // refresh token's lifetime at Path=/ purely so the middleware has
    // something to check for an active session; it carries no credential
    // value of its own.
    public ResponseCookie createSessionHintCookie(long maxAgeSeconds) {
        return createCookie(SESSION_HINT_COOKIE_VALUE, maxAgeSeconds, SESSION_HINT_COOKIE_NAME, "/");
    }

    public ResponseCookie deleteCookie(String name, String path) {
        return createCookie("", 0, name, path);
    }

    public Optional<String> extractFromCookies(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
