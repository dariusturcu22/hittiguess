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
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Time-window request-rate limiting, layered on top of any endpoint-specific gate
 * (such as SongMetadataService's single-in-flight-request check). Requests are keyed
 * by authenticated user where available, by client IP otherwise, except the login and
 * register endpoints, which are always keyed by IP since they have no authenticated
 * user yet and are the ones credential-stuffing and enumeration attempts target.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int GENERAL_MAX_REQUESTS_PER_WINDOW = 60;
    private static final int AUTH_MAX_REQUESTS_PER_WINDOW = 5;
    private static final Set<String> IP_ONLY_RATE_LIMITED_PATHS = Set.of("/auth/login", "/auth/register");
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Too many requests, try again later";

    private final ObjectMapper objectMapper;

    private final RateLimiterRegistry generalLimiter =
            new RateLimiterRegistry(GENERAL_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);
    private final RateLimiterRegistry authEndpointLimiter =
            new RateLimiterRegistry(AUTH_MAX_REQUESTS_PER_WINDOW, RATE_LIMIT_WINDOW);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        boolean isIpOnlyRateLimitedPath = IP_ONLY_RATE_LIMITED_PATHS.contains(request.getRequestURI());

        RateLimiterRegistry limiterForRequest = isIpOnlyRateLimitedPath ? authEndpointLimiter : generalLimiter;
        String rateLimitKey = isIpOnlyRateLimitedPath
                ? "ip:" + clientIpAddress(request)
                : rateLimitKeyForRequest(request);

        if (!limiterForRequest.tryConsume(rateLimitKey)) {
            writeRateLimitExceededResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String rateLimitKeyForRequest(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return "user:" + userPrincipal.getUser().getId();
        }
        return "ip:" + clientIpAddress(request);
    }

    // X-Forwarded-For is not trusted here: this deployment has no reverse proxy in front of
    // it to set that header honestly (hosting platform is still undecided, see stories 7/8),
    // so it's fully attacker-controlled. Keying on it would let an attacker bypass the limit
    // by sending a different value on every request, defeating the point of IP-based keying.
    private String clientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    // Test-only: see RateLimiterRegistry.resetForTesting.
    public void resetForTesting() {
        generalLimiter.resetForTesting();
        authEndpointLimiter.resetForTesting();
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
