package org.dariusturcu.backend.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.AuthService;
import org.dariusturcu.backend.util.CookieUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private static final String RETURN_TO_PARAMETER = "returnTo";

    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Lazy
    private final AuthService authService;

    public OAuth2AuthenticationSuccessHandler(
            JwtUtil jwtUtil,
            CookieUtil cookieUtil,
            HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository,
            @Lazy AuthService authService) {
        this.jwtUtil = jwtUtil;
        this.cookieUtil = cookieUtil;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.authService = authService;
    }

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication)
            throws IOException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = authService.createAndSaveRefreshToken(user, false);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtil.createAccessTokenCookie(
                        accessToken,
                        jwtUtil.getExpirationSeconds()
                ).toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtil.createRefreshTokenCookie(
                        refreshToken,
                        jwtUtil.getRefreshExpirationSeconds()
                ).toString()
        );
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieUtil.createSessionHintCookie(
                        jwtUtil.getRefreshExpirationSeconds()
                ).toString()
        );

        getRedirectStrategy().sendRedirect(request, response, targetUrl(request));
    }

    private String targetUrl(HttpServletRequest request) {
        String returnTo = carriedReturnTo(request);
        if (returnTo == null) {
            return redirectUri;
        }
        return UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam(RETURN_TO_PARAMETER, returnTo)
                .build()
                .toUriString();
    }

    private String carriedReturnTo(HttpServletRequest request) {
        OAuth2AuthorizationRequest savedRequest = authorizationRequestRepository.loadAuthorizationRequest(request);
        if (savedRequest == null) {
            return null;
        }
        Object candidate = savedRequest.getAdditionalParameters().get(RETURN_TO_PARAMETER);
        if (!(candidate instanceof String returnToCandidate)) {
            return null;
        }
        return safeReturnToPath(returnToCandidate);
    }

    private static String safeReturnToPath(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//") || value.contains("\\")) {
            return null;
        }
        return value;
    }
}
