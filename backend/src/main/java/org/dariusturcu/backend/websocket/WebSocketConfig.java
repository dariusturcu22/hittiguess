package org.dariusturcu.backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

// Base STOMP-over-WebSocket config for the core service. A single endpoint, two simple
// broker prefixes, and one application-destination prefix. "/topic" carries every
// per-group and per-session broadcast destination GroupDestinations and
// SessionDestinations define; "/queue" carries per-user destinations
// (BulkImportDestinations, story 40) sent through
// SimpMessagingTemplate#convertAndSendToUser. Spring's UserDestinationMessageHandler
// rewrites a "/user/{username}/queue/..." send into a session-specific "/queue/..."
// destination before handing it to the broker, so "/queue" has to be a registered
// broker prefix the same as "/topic" is, or the simple broker has nothing to route it
// to and per-user delivery silently does nothing.
//
// Mirrors SecurityConfig's CORS allow-list: a browser's WebSocket handshake is still an
// HTTP request subject to the Origin check, this list has to match or the handshake is
// rejected before STOMP or the auth interceptor ever sees it.
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String STOMP_ENDPOINT = "/ws";
    private static final String APPLICATION_DESTINATION_PREFIX = "/app";
    private static final String BROADCAST_DESTINATION_PREFIX = "/topic";
    private static final String USER_QUEUE_DESTINATION_PREFIX = "/queue";
    // The browser client offers 10-second heartbeats each way by default. Meeting it
    // server-side lets Spring notice a dead socket (sleeping laptop, killed app) and
    // publish the disconnect that flips the member to Away, instead of leaving the
    // presence flag stuck until the next clean close.
    private static final long HEARTBEAT_INTERVAL_MILLISECONDS = 10_000;
    private final StompAuthenticationChannelInterceptor stompAuthenticationChannelInterceptor;
    private final JwtCookieHandshakeInterceptor jwtCookieHandshakeInterceptor;
    private final List<String> allowedFrontendOrigins;
    private final TaskScheduler messageBrokerTaskScheduler;

    public WebSocketConfig(
            StompAuthenticationChannelInterceptor stompAuthenticationChannelInterceptor,
            JwtCookieHandshakeInterceptor jwtCookieHandshakeInterceptor,
            @Value("${frontend.allowed-origins}") List<String> allowedFrontendOrigins,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler
    ) {
        this.stompAuthenticationChannelInterceptor = stompAuthenticationChannelInterceptor;
        this.jwtCookieHandshakeInterceptor = jwtCookieHandshakeInterceptor;
        this.allowedFrontendOrigins = allowedFrontendOrigins;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(STOMP_ENDPOINT)
                .addInterceptors(jwtCookieHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedFrontendOrigins.toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes(APPLICATION_DESTINATION_PREFIX);
        registry.enableSimpleBroker(BROADCAST_DESTINATION_PREFIX, USER_QUEUE_DESTINATION_PREFIX)
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL_MILLISECONDS, HEARTBEAT_INTERVAL_MILLISECONDS})
                .setTaskScheduler(messageBrokerTaskScheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthenticationChannelInterceptor);
    }
}
