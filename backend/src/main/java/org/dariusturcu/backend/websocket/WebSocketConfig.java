package org.dariusturcu.backend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

// Base STOMP-over-WebSocket config for the core service. A single endpoint, a single
// simple broker prefix, and one application-destination prefix, shared by every
// per-group destination GroupDestinations defines and by whatever per-session
// destinations story 10 adds under the same "/topic/sessions/..." convention.
//
// Mirrors SecurityConfig's CORS allow-list: a browser's WebSocket handshake is still an
// HTTP request subject to the Origin check, this list has to match or the handshake is
// rejected before STOMP or the auth interceptor ever sees it.
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String STOMP_ENDPOINT = "/ws";
    private static final String APPLICATION_DESTINATION_PREFIX = "/app";
    private static final String BROADCAST_DESTINATION_PREFIX = "/topic";
    private static final List<String> ALLOWED_ORIGIN_PATTERNS =
            List.of("http://localhost:3000", "https://my-hitster.dariusturcu22.com");

    private final StompAuthenticationChannelInterceptor stompAuthenticationChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(STOMP_ENDPOINT)
                .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS.toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(APPLICATION_DESTINATION_PREFIX);
        registry.enableSimpleBroker(BROADCAST_DESTINATION_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthenticationChannelInterceptor);
    }
}
