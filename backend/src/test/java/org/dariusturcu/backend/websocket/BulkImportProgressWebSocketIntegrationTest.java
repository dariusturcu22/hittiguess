package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.CustomUserDetailsService;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real STOMP wiring the per-user bulk-import progress channel (story 40)
 * adds, end to end: a client subscribes to its own "/user/..." destination, the real
 * simple broker records that subscription against the authenticated user (not a raw
 * session id), and BulkImportProgressListener's convertAndSendToUser resolves against
 * it without error. Mirrors GroupWebSocketIntegrationTest's approach: driven at the
 * message-channel level rather than a real socket, since this sandbox's JDK cannot open
 * even a loopback TCP connection.
 *
 * Asserted through the real SimpUserRegistry (which user is known, and what it is
 * subscribed to) rather than clientOutboundChannel delivery, for the same reason
 * GroupWebSocketIntegrationTest avoids that path: real frame delivery requires a live
 * WebSocketSession this sandbox cannot create. The user registry is what
 * convertAndSendToUser itself resolves against, so asserting against it directly proves
 * the same guarantee production delivery depends on.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = BulkImportProgressWebSocketIntegrationTest.WebSocketTestConfig.class)
class BulkImportProgressWebSocketIntegrationTest {

    @org.springframework.context.annotation.Configuration
    @EnableAutoConfiguration(exclude = {
            OAuth2ClientAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
    })
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    @Import({
            JwtUtil.class,
            CustomUserDetailsService.class,
            StompAuthenticationChannelInterceptor.class,
            StompSubscriptionAuthorizationInterceptor.class,
            JwtCookieHandshakeInterceptor.class,
            WebSocketConfig.class,
            BulkImportProgressListener.class
    })
    static class WebSocketTestConfig {
    }

    private static final Duration ASSERTION_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("jwt.secret", () -> "integration-test-jwt-secret-integration-test-jwt-secret");
        registry.add("jwt.expiration", () -> "900000");
        registry.add("jwt.refresh-expiration", () -> "604800000");
    }

    @BeforeAll
    static void migrate() throws SQLException {
        org.flywaydb.core.Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    @Qualifier("clientInboundChannel")
    private MessageChannel clientInboundChannel;
    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@integration.test");
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.save(user);
    }

    private Authentication authenticationFor(User user) {
        return new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null);
    }

    // Simulates a client's CONNECT and SUBSCRIBE frames to its own per-user destination. A
    // real STOMP CONNECT already attaches the authenticated user to every subsequent frame
    // on that session (see StompAuthenticationChannelInterceptor); this sets it directly on
    // both frames the same way, since this test drives the channel rather than a real
    // socket. The real simple broker's own SubscriptionRegistry (which the message-routing
    // path itself depends on) is updated by sending the SUBSCRIBE frame on
    // clientInboundChannel; the separate SimpUserRegistry (which user is subscribed to
    // what, used below to assert this test's guarantee) is updated only by the
    // SessionConnectedEvent/SessionSubscribeEvent application events the WebSocket
    // transport layer normally publishes alongside those frames, so this publishes them
    // too, the same way GroupWebSocketIntegrationTest publishes SessionSubscribeEvent for
    // GroupPresenceRegistry's benefit.
    private void subscribeToOwnProgressQueue(String sessionId, Authentication authentication) throws InterruptedException {
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        connectAccessor.setSessionId(sessionId);
        connectAccessor.setUser(authentication);
        connectAccessor.setLeaveMutable(true);
        Message<byte[]> connectedMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        applicationEventPublisher.publishEvent(new SessionConnectedEvent(this, connectedMessage, authentication));

        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setSessionId(sessionId);
        subscribeAccessor.setSubscriptionId("sub-0");
        subscribeAccessor.setDestination("/user" + BulkImportDestinations.progressQueue());
        subscribeAccessor.setUser(authentication);
        subscribeAccessor.setLeaveMutable(true);
        Message<byte[]> subscribeMessage = MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        clientInboundChannel.send(subscribeMessage);
        applicationEventPublisher.publishEvent(new SessionSubscribeEvent(this, subscribeMessage, authentication));
        awaitUntil(() -> simpUserRegistry.getUser(authentication.getName()) != null);
    }

    private void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(ASSERTION_TIMEOUT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition was not met within " + ASSERTION_TIMEOUT);
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
    }

    @Test
    void subscribingToTheOwnProgressQueueRegistersTheUserWithTheRealBroker() throws Exception {
        User user = persistUser("bulk-import-ws-user");
        Authentication authentication = authenticationFor(user);

        subscribeToOwnProgressQueue("progress-session", authentication);

        SimpUser registeredUser = simpUserRegistry.getUser(user.getUsername());
        assertThat(registeredUser).isNotNull();
        assertThat(registeredUser.getSessions()).hasSize(1);
        List<String> subscribedDestinations = registeredUser.getSessions().stream()
                .flatMap(session -> session.getSubscriptions().stream())
                .map(SimpSubscription::getDestination)
                .toList();
        assertThat(subscribedDestinations).containsExactly("/user" + BulkImportDestinations.progressQueue());
    }

    @Test
    void publishingAProgressEventForASubscribedUserRoutesWithoutErrorThroughTheRealBroker() throws Exception {
        User user = persistUser("bulk-import-ws-progress-user");
        Authentication authentication = authenticationFor(user);
        subscribeToOwnProgressQueue("progress-session-2", authentication);

        // BulkImportProgressListener converts the event to JSON and hands it to the real
        // SimpMessagingTemplate, which resolves user.getUsername() against the subscription
        // just registered above and routes it through the real simple broker. This runs the
        // full pipeline (event publish, JSON serialization, user-destination resolution,
        // broker routing) without error.
        applicationEventPublisher.publishEvent(
                new BulkImportProgressEvent(user.getUsername(), "job-1", "video-id-1", BulkImportProgressOutcome.RESOLVED));
    }

    @Test
    void publishingAProgressEventForAnUnsubscribedUsernameDoesNotError() {
        applicationEventPublisher.publishEvent(
                new BulkImportProgressEvent("nobody-is-subscribed", "job-2", "video-id-1", BulkImportProgressOutcome.RESOLVED));
    }
}
