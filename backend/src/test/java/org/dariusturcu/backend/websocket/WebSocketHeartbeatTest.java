package org.dariusturcu.backend.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// Without a heartbeat the simple broker negotiates 0,0 with the browser client,
// so a dead socket (sleeping laptop, killed app) is never noticed server-side and
// the member never flips to Away. This pins the wiring that prevents that.
@ExtendWith(MockitoExtension.class)
class WebSocketHeartbeatTest {

    private static final long EXPECTED_HEARTBEAT_INTERVAL_MILLISECONDS = 10_000;

    static class ExposedRegistry extends MessageBrokerRegistry {
        ExposedRegistry() {
            super(new ExecutorSubscribableChannel(), new ExecutorSubscribableChannel());
        }

        SimpleBrokerMessageHandler simpleBroker(SubscribableChannel channel) {
            return getSimpleBroker(channel);
        }
    }

    @Test
    void simpleBrokerNegotiatesHeartbeatsOnTheScheduler() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        WebSocketConfig config = new WebSocketConfig(
                mock(StompAuthenticationChannelInterceptor.class),
                mock(StompSubscriptionAuthorizationInterceptor.class),
                mock(JwtCookieHandshakeInterceptor.class),
                List.of("http://localhost:3000"),
                scheduler);
        ExposedRegistry registry = new ExposedRegistry();

        config.configureMessageBroker(registry);
        SimpleBrokerMessageHandler broker = registry.simpleBroker(new ExecutorSubscribableChannel());

        assertThat(broker.getHeartbeatValue())
                .containsExactly(EXPECTED_HEARTBEAT_INTERVAL_MILLISECONDS, EXPECTED_HEARTBEAT_INTERVAL_MILLISECONDS);
        assertThat(broker.getTaskScheduler()).isSameAs(scheduler);
    }
}
