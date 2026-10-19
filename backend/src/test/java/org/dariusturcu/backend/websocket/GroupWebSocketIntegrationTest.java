package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.controller.VoiceSignalingController;
import org.dariusturcu.backend.model.group.CreateGroupRequest;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.model.voice.VoiceSignalRequest;
import org.dariusturcu.backend.model.voice.VoiceSignalType;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.CustomUserDetailsService;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.service.PlaylistAccessService;
import org.flywaydb.core.Flyway;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.AbstractSubscribableChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real STOMP message-broker wiring this story adds, end to end:
 * subscription tracking, group-event broadcast, and disconnect handling, all through the
 * actual Spring beans involved (GroupSessionEventListener, GroupBroadcastListener,
 * GroupService, the real simple broker's own subscription registry), backed by a real
 * Postgres Testcontainer.
 *
 * Driven at the message-channel level (clientInboundChannel) and by publishing the
 * SessionSubscribeEvent/SessionDisconnectEvent the WebSocket transport layer would
 * normally publish, rather than through a real network socket: this sandbox's JDK cannot
 * open even a loopback TCP connection (embedded Tomcat's NIO selector fails with "Unable
 * to establish loopback connection" under both the modern and the classic Windows
 * selector provider), the same class of constraint GroupLifecycleIntegrationTest already
 * works around for java.net.http.HttpClient.
 *
 * Correctness is asserted against the simple broker's own SubscriptionRegistry (which
 * session is subscribed to which destination right now) rather than by capturing frames
 * off clientOutboundChannel: actual frame delivery to a client also passes through
 * SubProtocolWebSocketHandler, which requires a live WebSocketSession this sandbox cannot
 * create. The subscription registry is what determines delivery in production; asserting
 * against it directly proves the guarantee (no stale or duplicate subscription across a
 * reconnect) without depending on that transport-facing code.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = GroupWebSocketIntegrationTest.WebSocketTestConfig.class)
class GroupWebSocketIntegrationTest {

    // WebSocketConfig, StompAuthenticationChannelInterceptor, and the two event
    // listeners are @Import-ed rather than built through @Bean factory methods:
    // WebSocketConfig's @EnableWebSocketMessageBroker only gets processed (registering
    // the STOMP message broker, clientInboundChannel, and the SimpMessagingTemplate
    // GroupBroadcastListener needs) when Spring parses the class as a configuration
    // class in its own right, which @Import does and a plain factory-method return
    // value would not.
    @Configuration
    @EnableAutoConfiguration(exclude = {
            OAuth2ClientAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class
    })
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = GroupRepository.class)
    @Import({
            JwtUtil.class,
            CustomUserDetailsService.class,
            GroupPresenceRegistry.class,
            StompAuthenticationChannelInterceptor.class,
            StompSubscriptionAuthorizationInterceptor.class,
            JwtCookieHandshakeInterceptor.class,
            VoiceSignalingController.class,
            UserSocketCloser.class,
            WebSocketConfig.class,
            GroupSessionEventListener.class,
            GroupBroadcastListener.class
    })
    static class WebSocketTestConfig {
        @Bean
        SongMapper songMapper() {
            return new SongMapper();
        }

        @Bean
        PlaylistMapper playlistMapper() {
            return new PlaylistMapper(null);
        }

        @Bean
        GroupMapper groupMapper(PlaylistMapper playlistMapper) {
            return new GroupMapper(playlistMapper);
        }

        @Bean
        PlaylistAccessService playlistAccessService(PlaylistMembershipRepository playlistMembershipRepository) {
            return new PlaylistAccessService(playlistMembershipRepository);
        }

        @Bean
        GroupService groupService(GroupRepository groupRepository, MemberRepository memberRepository, GameSessionRepository gameSessionRepository,
                                   PlaylistRepository playlistRepository, GroupMapper groupMapper,
                                   ApplicationEventPublisher eventPublisher, PlaylistAccessService playlistAccessService) {
            return new GroupService(groupRepository, memberRepository, gameSessionRepository, playlistRepository, groupMapper, eventPublisher, playlistAccessService);
        }
    }

    // Spring's user destination prefix a client subscribes with, and the suffix its
    // resolver appends before the subscribing session's id.
    private static final String USER_DESTINATION_PREFIX = "/user";
    private static final String RESOLVED_USER_DESTINATION_SUFFIX = "-user";
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
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    @Qualifier("clientInboundChannel")
    private MessageChannel clientInboundChannel;
    @Autowired
    private SimpleBrokerMessageHandler simpleBrokerMessageHandler;
    @Autowired
    @Qualifier("clientOutboundChannel")
    private AbstractSubscribableChannel clientOutboundChannel;
    @Autowired
    private VoiceSignalingController voiceSignalingController;

    private final Queue<Message<?>> outboundMessages = new ConcurrentLinkedQueue<>();
    private final ChannelInterceptor outboundCapture = new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            outboundMessages.add(message);
            return message;
        }
    };

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        clientOutboundChannel.removeInterceptor(outboundCapture);
        outboundMessages.clear();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@integration.test");
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.save(user);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private Authentication authenticationFor(User user) {
        return new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null);
    }

    private Message<byte[]> subscribeFrame(String sessionId, String destination, User subscriber) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId("sub-0");
        accessor.setDestination(destination);
        if (subscriber != null) {
            accessor.setUser(authenticationFor(subscriber));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // Simulates a client's SUBSCRIBE frame: registers the subscription with the real
    // simple broker's own SubscriptionRegistry, and publishes the SessionSubscribeEvent
    // the WebSocket transport layer would normally publish, so GroupSessionEventListener
    // registers presence too.
    private void subscribe(String sessionId, String destination, User subscriber) throws InterruptedException {
        Message<byte[]> message = subscribeFrame(sessionId, destination, subscriber);

        clientInboundChannel.send(message);
        applicationEventPublisher.publishEvent(new SessionSubscribeEvent(this, message, authenticationFor(subscriber)));
        awaitUntil(() -> subscribedSessionIds(destination).contains(sessionId));
    }

    private void assertSubscriptionRefused(String sessionId, String destination, User subscriber) {
        assertThatThrownBy(() -> clientInboundChannel.send(subscribeFrame(sessionId, destination, subscriber)))
                .isInstanceOf(MessagingException.class);
        assertThat(subscribedSessionIds(destination)).doesNotContain(sessionId);
    }

    // Connects the session to the broker and the user registry, as a real CONNECT does,
    // then subscribes it to the member's own voice signal queue through the "/user" prefix.
    private void connectAndSubscribeToVoiceSignals(String sessionId, Long groupId, User member) throws InterruptedException {
        Authentication authentication = authenticationFor(member);
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setSessionId(sessionId);
        connectAccessor.setUser(authentication);
        connectAccessor.setLeaveMutable(true);
        clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders()));

        StompHeaderAccessor connectedAccessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        connectedAccessor.setSessionId(sessionId);
        connectedAccessor.setUser(authentication);
        connectedAccessor.setLeaveMutable(true);
        applicationEventPublisher.publishEvent(new SessionConnectedEvent(
                this, MessageBuilder.createMessage(new byte[0], connectedAccessor.getMessageHeaders()), authentication));

        Message<byte[]> message = subscribeFrame(sessionId, USER_DESTINATION_PREFIX + GroupDestinations.voiceSignalQueue(groupId), member);
        clientInboundChannel.send(message);
        applicationEventPublisher.publishEvent(new SessionSubscribeEvent(this, message, authentication));
        String resolvedDestination = GroupDestinations.voiceSignalQueue(groupId) + RESOLVED_USER_DESTINATION_SUFFIX + sessionId;
        awaitUntil(() -> subscribedSessionIds(resolvedDestination).contains(sessionId));
    }

    private List<String> sessionIdsThatReceivedAMessage() {
        return outboundMessages.stream()
                .map(Message::getHeaders)
                .filter(headers -> SimpMessageHeaderAccessor.getMessageType(headers) == SimpMessageType.MESSAGE)
                .map(SimpMessageHeaderAccessor::getSessionId)
                .toList();
    }

    // Simulates the socket closing: sends the real DISCONNECT frame the simple broker
    // itself needs to clear its SubscriptionRegistry (mirroring what StompSubProtocolHandler
    // forwards through clientInboundChannel on a real transport), and publishes the
    // SessionDisconnectEvent GroupSessionEventListener reacts to for the group-member half.
    private void disconnect(String sessionId, Authentication authentication) throws InterruptedException {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        clientInboundChannel.send(message);
        applicationEventPublisher.publishEvent(
                new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL, authentication));
    }

    private List<String> subscribedSessionIds(String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        Message<byte[]> probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MultiValueMap<String, String> subscriptionsBySession =
                simpleBrokerMessageHandler.getSubscriptionRegistry().findSubscriptions(probe);
        return List.copyOf(subscriptionsBySession.keySet());
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
    void subscribingRegistersTheSessionWithTheRealBrokerAndABroadcastRunsWithoutError() throws Exception {
        User admin = persistUser("ws-broadcast-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        String destination = GroupDestinations.membershipTopic(created.id());

        subscribe("broadcast-session", destination, admin);
        assertThat(subscribedSessionIds(destination)).containsExactly("broadcast-session");

        // GroupService.joinGroup publishes a MEMBER_JOINED event; GroupBroadcastListener
        // converts it to JSON and hands it to the real SimpMessagingTemplate, which the
        // real simple broker routes against the subscription just asserted above. This
        // runs the full pipeline (event publish, JSON serialization, broker routing)
        // without error.
        User joiner = persistUser("ws-broadcast-joiner");
        authenticateAs(joiner);
        groupService.joinGroup(new JoinGroupRequest(created.inviteCode(), null, null, null));
    }

    @Test
    void reconnectingToAGroupsTopicLeavesNoStaleOrDuplicateSubscription() throws Exception {
        User admin = persistUser("ws-reconnect-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        String destination = GroupDestinations.membershipTopic(created.id());

        User observer = persistUser("ws-reconnect-observer");
        authenticateAs(observer);
        groupService.joinGroup(new JoinGroupRequest(created.inviteCode(), null, null, null));

        String firstSessionId = "reconnect-session-one";
        subscribe(firstSessionId, destination, observer);
        assertThat(subscribedSessionIds(destination)).containsExactly(firstSessionId);

        // The socket drops: the observer's session disconnects, flipping their
        // isConnected flag and clearing both the broker's and the presence registry's
        // subscription state for that session.
        disconnect(firstSessionId, authenticationFor(observer));
        awaitUntil(() -> memberRepository.findByUser(observer)
                .map(member -> !member.isConnected())
                .orElse(false));
        awaitUntil(() -> subscribedSessionIds(destination).isEmpty());

        // Reconnect: a new session subscribes to the same group topic.
        String secondSessionId = "reconnect-session-two";
        subscribe(secondSessionId, destination, observer);

        // Only the reconnected session is registered: no stale entry survived from the
        // first session, and the new one isn't duplicated.
        assertThat(subscribedSessionIds(destination)).containsExactly(secondSessionId);
    }

    @Test
    void disconnectingASessionFlipsTheMembersConnectionFlagWithoutEndingTheGroup() throws Exception {
        User admin = persistUser("ws-disconnect-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));

        String sessionId = "disconnect-session";
        subscribe(sessionId, GroupDestinations.membershipTopic(created.id()), admin);

        disconnect(sessionId, authenticationFor(admin));

        awaitUntil(() -> memberRepository.findByUser(admin)
                .map(member -> !member.isConnected())
                .orElse(false));
        assertThat(groupRepository.findById(created.id())).isPresent();
    }

    @Test
    void aNonMemberCannotSubscribeToAnyOfAGroupsTopics() {
        User admin = persistUser("ws-authorize-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        User outsider = persistUser("ws-authorize-outsider");

        for (String destination : List.of(
                GroupDestinations.membershipTopic(created.id()),
                GroupDestinations.settingsTopic(created.id()),
                GroupDestinations.chatTopic(created.id()),
                GroupDestinations.voiceTopic(created.id()))) {
            assertSubscriptionRefused("outsider-session", destination, outsider);
        }
    }

    @Test
    void aSubscriptionWithoutAnAuthenticatedUserIsRefused() {
        User admin = persistUser("ws-anonymous-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));

        assertSubscriptionRefused("anonymous-session", GroupDestinations.membershipTopic(created.id()), null);
    }

    @Test
    void aUserWhoIsNotAPlayerCannotSubscribeToASessionsTopics() {
        User outsider = persistUser("ws-session-outsider");
        Long unrelatedSessionId = Long.MAX_VALUE;

        assertSubscriptionRefused("session-outsider", SessionDestinations.roundTopic(unrelatedSessionId), outsider);
        assertSubscriptionRefused("session-outsider", SessionDestinations.endedTopic(unrelatedSessionId), outsider);
    }

    @Test
    void aDirectSubscriptionToAnotherSessionsResolvedUserQueueIsRefused() {
        User admin = persistUser("ws-direct-queue-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));

        assertSubscriptionRefused("eavesdropper-session",
                GroupDestinations.voiceSignalQueue(created.id()) + RESOLVED_USER_DESTINATION_SUFFIX + "target-session", admin);
    }

    @Test
    void aVoiceSignalReachesOnlyItsTargetMember() throws Exception {
        User sender = persistUser("ws-voice-sender");
        authenticateAs(sender);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        User target = persistUser("ws-voice-target");
        authenticateAs(target);
        groupService.joinGroup(new JoinGroupRequest(created.inviteCode(), null, null, null));
        User bystander = persistUser("ws-voice-bystander");
        authenticateAs(bystander);
        groupService.joinGroup(new JoinGroupRequest(created.inviteCode(), null, null, null));
        connectAndSubscribeToVoiceSignals("voice-target-session", created.id(), target);
        connectAndSubscribeToVoiceSignals("voice-bystander-session", created.id(), bystander);
        clientOutboundChannel.addInterceptor(outboundCapture);

        voiceSignalingController.relaySignal(created.id(),
                new VoiceSignalRequest(VoiceSignalType.OFFER, target.getId(), "opaque-offer"),
                authenticationFor(sender));

        awaitUntil(() -> sessionIdsThatReceivedAMessage().contains("voice-target-session"));
        assertThat(sessionIdsThatReceivedAMessage()).containsExactly("voice-target-session");
    }
}
