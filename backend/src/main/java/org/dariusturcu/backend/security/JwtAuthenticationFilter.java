package org.dariusturcu.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.util.CookieUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    // Everything else under /auth/** is reached with no session yet (register, login, the
    // token-based verify-email/password-reset/2fa-verify endpoints), so this filter has
    // nothing useful to authenticate there. These three are the exception: 2FA setup,
    // confirm, and disable act on the caller's own account and need a real principal.
    private static final Set<String> AUTHENTICATED_AUTH_PATHS = Set.of(
            "/auth/2fa/setup", "/auth/2fa/confirm", "/auth/2fa/disable"
    );

    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final UserDetailsService userDetailsService;

    private String extractToken(HttpServletRequest request) {
        return cookieUtil.extractFromCookies(request, "access_token")
                .orElseGet(() -> {
                    String header = request.getHeader("Authorization");
                    if (header != null && header.startsWith("Bearer ")) {
                        return header.substring(7);
                    }
                    return null;
                });
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/auth/") && !AUTHENTICATED_AUTH_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractToken(request);
            if (jwt == null) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = jwtUtil.extractEmail(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    // Credentials are null because the JWT signature already proved identity,
                    // there's no password to carry forward once authenticated.
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
        }
        // Authorization happens later based on whether the SecurityContext got populated above,
        // this filter always lets the request continue either way.
        filterChain.doFilter(request, response);
    }
}
