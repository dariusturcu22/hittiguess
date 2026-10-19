package org.dariusturcu.backend.websocket;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtCookieHandshakeInterceptor implements HandshakeInterceptor {

    static final String ACCESS_TOKEN_ATTRIBUTE = JwtCookieHandshakeInterceptor.class.getName() + ".accessToken";
    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    private static final String COOKIE_SEPARATOR = ";";
    private static final String NAME_VALUE_SEPARATOR = "=";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String cookieHeader = request.getHeaders().getFirst(HttpHeaders.COOKIE);
        if (cookieHeader != null) {
            extractCookie(cookieHeader).ifPresent(token -> attributes.put(ACCESS_TOKEN_ATTRIBUTE, token));
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }

    private java.util.Optional<String> extractCookie(String cookieHeader) {
        for (String cookie : cookieHeader.split(COOKIE_SEPARATOR)) {
            String[] nameAndValue = cookie.trim().split(NAME_VALUE_SEPARATOR, 2);
            if (nameAndValue.length == 2 && ACCESS_TOKEN_COOKIE_NAME.equals(nameAndValue[0])) {
                return java.util.Optional.of(nameAndValue[1]);
            }
        }
        return java.util.Optional.empty();
    }
}
