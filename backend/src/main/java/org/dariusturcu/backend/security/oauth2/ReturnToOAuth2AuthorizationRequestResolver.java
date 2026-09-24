package org.dariusturcu.backend.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class ReturnToOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private static final String RETURN_TO_PARAMETER = "returnTo";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public ReturnToOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return carryReturnTo(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return carryReturnTo(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest carryReturnTo(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request) {
        if (authorizationRequest == null) {
            return null;
        }
        String returnTo = safeReturnToPath(request.getParameter(RETURN_TO_PARAMETER));
        if (returnTo == null) {
            return authorizationRequest;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(parameters -> parameters.put(RETURN_TO_PARAMETER, returnTo))
                .build();
    }

    static String safeReturnToPath(String value) {
        if (value == null || !value.startsWith("/") || value.startsWith("//") || value.contains("\\")) {
            return null;
        }
        return value;
    }
}
