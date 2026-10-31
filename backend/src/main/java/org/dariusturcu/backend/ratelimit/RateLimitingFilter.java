package org.dariusturcu.backend.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ErrorResponse;
import org.dariusturcu.backend.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Time-window request rate limiting applied across sensitive actions and general traffic.
 * Requests are keyed by authenticated user ID where available or by client IP otherwise.
 * Authentication endpoints (login, registration, password reset, email verification, 2FA)
 * are always keyed by client IP to block credential stuffing and enumeration attacks.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int GENERAL_MAX_REQUESTS_PER_WINDOW = 60;
    private static final int AUTH_MAX_REQUESTS_PER_WINDOW = 5;
    private static final int SONG_AND_IMPORT_MAX_REQUESTS_PER_WINDOW = 10;
    private static final int REPORT_MAX_REQUESTS_PER_WINDOW = 5;
    private static final int AVATAR_UPLOAD_MAX_REQUESTS_PER_WINDOW = 5;

    private static final Set<String> AUTH_RATE_LIMITED_PATHS = Set.of(
            "/auth/login",
            "/auth/register",
            "/auth/resend-verification",
            "/auth/password-reset/request",
            "/auth/password-reset/confirm",
            "/auth/verify-email",
            "/auth/2fa/verify"
    );

    private static final List<String> SONG_AND_IMPORT_PATH_PATTERNS = List.of(
            "/api/playlists/*/songs",
            "/api/playlists/*/imports",
            "/api/playlists/*/import-jobs",
            "/api/bulk-import",
            "/api/bulk-import/expand",
            "/api/metadata/song"
    );

    private static final List<String> REPORT_PATH_PATTERNS = List.of(
            "/api/reports",
            "/api/confirmations"
    );

    private static final String AVATAR_UPLOAD_PATH = "/api/users/me/avatar";
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Too many requests, try again later";

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final RateLimiterRegistry generalLimiter =
            new RateLimiterRegistry(GENERAL_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);
    private final RateLimiterRegistry authEndpointLimiter =
            new RateLimiterRegistry(AUTH_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);
    private final RateLimiterRegistry songAndImportLimiter =
            new RateLimiterRegistry(SONG_AND_IMPORT_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);
    private final RateLimiterRegistry reportLimiter =
            new RateLimiterRegistry(REPORT_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);
    private final RateLimiterRegistry avatarUploadLimiter =
            new RateLimiterRegistry(AVATAR_UPLOAD_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String method = request.getMethod();

        RateLimiterRegistry limiter;
        String rateLimitKey;

        if (AUTH_RATE_LIMITED_PATHS.contains(requestUri)) {
            limiter = authEndpointLimiter;
            rateLimitKey = "auth:" + clientIpAddress(request);
        } else if ("POST".equalsIgnoreCase(method) && matchesAny(requestUri, SONG_AND_IMPORT_PATH_PATTERNS)) {
            limiter = songAndImportLimiter;
            rateLimitKey = "song-import:" + clientIdentityKey(request);
        } else if ("POST".equalsIgnoreCase(method) && matchesAny(requestUri, REPORT_PATH_PATTERNS)) {
            limiter = reportLimiter;
            rateLimitKey = "report:" + clientIdentityKey(request);
        } else if ("PUT".equalsIgnoreCase(method) && AVATAR_UPLOAD_PATH.equals(requestUri)) {
            limiter = avatarUploadLimiter;
            rateLimitKey = "avatar:" + clientIdentityKey(request);
        } else {
            limiter = generalLimiter;
            rateLimitKey = "general:" + clientIdentityKey(request);
        }

        if (!limiter.tryConsume(rateLimitKey)) {
            writeRateLimitExceededResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean matchesAny(String requestUri, List<String> patterns) {
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }

    private String clientIdentityKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return "user:" + userPrincipal.getUser().getId();
        }
        return "ip:" + clientIpAddress(request);
    }

    // When running behind Azure Container Apps or a reverse proxy, Tomcat's RemoteIpValve
    // (configured via server.forward-headers-strategy=native) processes X-Forwarded-For from
    // trusted internal proxies and updates request.getRemoteAddr() to the genuine client IP.
    // Untrusted direct connections do not pass internal proxy checks, leaving the remote address
    // as the socket IP and preventing header spoofing attacks.
    private String clientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    // Test-only: resets all token buckets across registries.
    public void resetForTesting() {
        generalLimiter.resetForTesting();
        authEndpointLimiter.resetForTesting();
        songAndImportLimiter.resetForTesting();
        reportLimiter.resetForTesting();
        avatarUploadLimiter.resetForTesting();
    }

    private void writeRateLimitExceededResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                RATE_LIMIT_EXCEEDED_MESSAGE,
                LocalDateTime.now()
        );
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
