package org.dariusturcu.backend.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.exception.ErrorResponse;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.util.RequestPaths;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Time-window request rate limiting applied across sensitive actions and general traffic.
 * Requests are keyed by authenticated user ID where available or by client IP otherwise.
 * Authentication endpoints (login, registration, password reset, email verification, 2FA)
 * are always keyed by client IP to block credential stuffing and enumeration attacks, each
 * with its own bucket so one endpoint's traffic cannot lock a shared network out of another.
 * Paths are matched in decoded, normalized form.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int GENERAL_MAX_REQUESTS_PER_WINDOW = 60;
    private static final int LOGIN_MAX_REQUESTS_PER_WINDOW = 10;
    private static final int REGISTER_MAX_REQUESTS_PER_WINDOW = 5;
    private static final int EMAIL_SENDING_MAX_REQUESTS_PER_WINDOW = 3;
    private static final int TOKEN_SUBMISSION_MAX_REQUESTS_PER_WINDOW = 10;
    private static final int SONG_AND_IMPORT_MAX_REQUESTS_PER_WINDOW = 10;
    private static final int REPORT_MAX_REQUESTS_PER_WINDOW = 5;
    private static final int AVATAR_UPLOAD_MAX_REQUESTS_PER_WINDOW = 5;

    private static final Map<String, Integer> AUTH_PATH_LIMITS = Map.of(
            "/auth/login", LOGIN_MAX_REQUESTS_PER_WINDOW,
            "/auth/register", REGISTER_MAX_REQUESTS_PER_WINDOW,
            "/auth/resend-verification", EMAIL_SENDING_MAX_REQUESTS_PER_WINDOW,
            "/auth/password-reset/request", EMAIL_SENDING_MAX_REQUESTS_PER_WINDOW,
            "/auth/password-reset/confirm", TOKEN_SUBMISSION_MAX_REQUESTS_PER_WINDOW,
            "/auth/verify-email", TOKEN_SUBMISSION_MAX_REQUESTS_PER_WINDOW,
            "/auth/2fa/verify", TOKEN_SUBMISSION_MAX_REQUESTS_PER_WINDOW
    );

    private static final List<String> SONG_AND_IMPORT_POST_PATH_PATTERNS = List.of(
            "/api/playlists/*/songs",
            "/api/playlists/*/imports",
            "/api/playlists/*/import-jobs",
            "/api/bulk-import",
            "/api/bulk-import/expand"
    );

    private static final String METADATA_LOOKUP_PATH = "/api/metadata/song";

    private static final List<String> REPORT_PATH_PATTERNS = List.of(
            "/api/songs/*/reports",
            "/api/songs/*/confirmations"
    );

    private static final String AVATAR_UPLOAD_PATH = "/api/users/me/avatar";
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Too many requests, try again later";

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final RateLimiterRegistry generalLimiter =
            new RateLimiterRegistry(GENERAL_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);
    private final Map<String, RateLimiterRegistry> authEndpointLimiters = buildAuthEndpointLimiters();
    private final RateLimiterRegistry songAndImportLimiter =
            new RateLimiterRegistry(SONG_AND_IMPORT_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);
    private final RateLimiterRegistry reportLimiter =
            new RateLimiterRegistry(REPORT_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);
    private final RateLimiterRegistry avatarUploadLimiter =
            new RateLimiterRegistry(AVATAR_UPLOAD_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);

    private static Map<String, RateLimiterRegistry> buildAuthEndpointLimiters() {
        Map<String, RateLimiterRegistry> limitersByPath = new HashMap<>();
        AUTH_PATH_LIMITS.forEach((path, maxRequests) ->
                limitersByPath.put(path, new RateLimiterRegistry(maxRequests, RATE_LIMIT_WINDOW)));
        return Map.copyOf(limitersByPath);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = RequestPaths.normalizedPath(request);
        String method = request.getMethod();

        RateLimiterRegistry limiter;
        String rateLimitKey;

        if (authEndpointLimiters.containsKey(requestUri)) {
            limiter = authEndpointLimiters.get(requestUri);
            rateLimitKey = "auth:" + clientIpAddress(request);
        } else if (isSongOrImportRequest(method, requestUri)) {
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

    private boolean isSongOrImportRequest(String method, String requestUri) {
        return ("POST".equalsIgnoreCase(method) && matchesAny(requestUri, SONG_AND_IMPORT_POST_PATH_PATTERNS))
                || ("GET".equalsIgnoreCase(method) && METADATA_LOOKUP_PATH.equals(requestUri));
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
        return ClientAddressKey.of(request.getRemoteAddr());
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
