package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.group.ChatMessageDTO;
import org.dariusturcu.backend.model.group.CreateGroupRequest;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.model.mapper.PlaylistMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.ChatMessageRepository;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.CustomUserDetailsService;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.security.util.JwtUtil;
import org.dariusturcu.backend.service.AbuseVisibilityEvents;
import org.dariusturcu.backend.service.ChatService;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises group chat send, history, and group-deletion cleanup against the real STOMP
 * broker wiring and a real Postgres Testcontainer, the same harness and sandbox workaround
 * GroupWebSocketIntegrationTest documents: driven at the clientInboundChannel level rather
 * than through a real network socket, since this sandbox's JDK cannot open a loopback TCP
 * connection. Send is proven by the persisted row plus the broadcast pipeline running
 * against a real subscription without error, not by capturing a delivered frame.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = GroupChatIntegrationTest.ChatTestConfig.class)
class GroupChatIntegrationTest {

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
            JwtCookieHandshakeInterceptor.class,
            WebSocketConfig.class,
            GroupSessionEventListener.class,
            GroupBroadcastListener.class,
            AbuseVisibilityEvents.class
    })
    static class ChatTestConfig {
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

        @Bean
        ChatService chatService(ChatMessageRepository chatMessageRepository, GroupRepository groupRepository,
                                SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper,
                                AbuseVisibilityEvents abuseVisibilityEvents) {
            return new ChatService(chatMessageRepository, groupRepository, messagingTemplate, objectMapper, abuseVisibilityEvents);
        }
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
    private ChatService chatService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    @Qualifier("clientInboundChannel")
    private MessageChannel clientInboundChannel;
    @Autowired
    private SimpleBrokerMessageHandler simpleBrokerMessageHandler;

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

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    private void subscribe(String sessionId, String destination) throws InterruptedException {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId("sub-0");
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        clientInboundChannel.send(message);
        awaitUntil(() -> subscriptionExists(destination, sessionId));
    }

    private boolean subscriptionExists(String destination, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        Message<byte[]> probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return simpleBrokerMessageHandler.getSubscriptionRegistry()
                .findSubscriptions(probe).containsKey(sessionId);
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
    void aMemberSendsAMessageAndItPersistsAndTheBroadcastPipelineRunsAgainstARealSubscription() throws Exception {
        User admin = persistUser("chat-send-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));

        subscribe("chat-send-session", GroupDestinations.chatTopic(created.id()));

        ChatMessageDTO sent = chatService.sendMessage(created.id(), admin.getId(), "hello group");

        assertThat(sent.content()).isEqualTo("hello group");
        assertThat(sent.senderDisplayName()).isEqualTo("chat-send-admin");
        assertThat(chatMessageRepository.countByGroupId(created.id())).isEqualTo(1);
    }

    @Test
    void aNonMemberCannotSendToAGroupsChat() {
        User admin = persistUser("chat-send-denied-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));

        User outsider = persistUser("chat-send-outsider");

        assertThatThrownBy(() -> chatService.sendMessage(created.id(), outsider.getId(), "let me in"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(chatMessageRepository.countByGroupId(created.id())).isZero();
    }

    @Test
    void aMemberReadsHistoryMostRecentFirst() {
        User admin = persistUser("chat-history-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));

        chatService.sendMessage(created.id(), admin.getId(), "first");
        chatService.sendMessage(created.id(), admin.getId(), "second");

        List<ChatMessageDTO> history = chatService.getHistory(created.id(), admin.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).content()).isEqualTo("second");
        assertThat(history.get(1).content()).isEqualTo("first");
    }

    @Test
    void aNonMemberCannotReadHistory() {
        User admin = persistUser("chat-history-denied-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        chatService.sendMessage(created.id(), admin.getId(), "members only");

        User outsider = persistUser("chat-history-outsider");

        assertThatThrownBy(() -> chatService.getHistory(created.id(), outsider.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deletingAGroupThroughTheExpirySweepRemovesItsChatHistory() {
        User admin = persistUser("chat-cleanup-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        chatService.sendMessage(created.id(), admin.getId(), "goes away with the group");
        assertThat(chatMessageRepository.countByGroupId(created.id())).isEqualTo(1);

        var group = groupRepository.findById(created.id()).orElseThrow();
        group.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        groupRepository.save(group);

        groupService.deleteExpiredGroups();

        assertThat(groupRepository.findById(created.id())).isEmpty();
        assertThat(chatMessageRepository.countByGroupId(created.id())).isZero();
    }
}
