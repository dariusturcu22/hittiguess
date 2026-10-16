package org.dariusturcu.backend.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {
    private static final String GENERIC_FAILURE_CODE = "oauth2_failed";

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {

        String failureCode = failureCodeFor(exception);
        log.warn("OAuth2 sign-in failed: {} ({})", failureCode, exception.getClass().getSimpleName());

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", failureCode)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String failureCodeFor(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception
                && CustomOAuth2UserService.ACCOUNT_EXISTS_ERROR_CODE.equals(oAuth2Exception.getError().getErrorCode())) {
            return CustomOAuth2UserService.ACCOUNT_EXISTS_ERROR_CODE;
        }
        return GENERIC_FAILURE_CODE;
    }
}
