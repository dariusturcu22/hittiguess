package org.dariusturcu.backend.group;

import org.dariusturcu.backend.exception.ConflictException;
import org.dariusturcu.backend.model.group.CreateGroupRequest;
import org.dariusturcu.backend.model.group.DjMode;
import org.dariusturcu.backend.model.group.GroupDetailDTO;
import org.dariusturcu.backend.model.group.GroupStatus;
import org.dariusturcu.backend.model.group.JoinGroupRequest;
import org.dariusturcu.backend.model.group.MemberDTO;
import org.dariusturcu.backend.model.group.UpdateGroupSettingsRequest;
import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.model.mapper.SongMapper;
import org.dariusturcu.backend.model.playlist.Playlist;
import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlaylistMembershipRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.service.PlaylistAccessService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises GroupService against a real Postgres instance rather than mocks:
 * the full create/join/settings/start-session lifecycle, both expiry timers
 * through the sweep mechanism, and explicit leave versus disconnect.
 *
 * Uses a minimal JPA-only context, the same reason SongApiCompatibilityAfterMigrationTest
 * does: this project's OAuth2 client and AI-service beans build a java.net.http.HttpClient,
 * which this sandbox's JDK can't construct, and neither has anything to do with groups.
 */
@Testcontainers
@SpringBootTest(classes = GroupLifecycleIntegrationTest.JpaTestConfig.class)
@Transactional
class GroupLifecycleIntegrationTest {

    @Configuration
    @EnableAutoConfiguration(exclude = OAuth2ClientAutoConfiguration.class)
    @EntityScan("org.dariusturcu.backend.model")
    @EnableJpaRepositories(basePackageClasses = GroupRepository.class)
    @ComponentScan(basePackages = "org.dariusturcu.backend.model.mapper")
    static class JpaTestConfig {
        @Bean
        SongMapper songMapper() {
            return new SongMapper();
        }

        @Bean
        PlaylistAccessService playlistAccessService(PlaylistMembershipRepository playlistMembershipRepository) {
            return new PlaylistAccessService(playlistMembershipRepository);
        }

        @Bean
        GroupService groupService(GroupRepository groupRepository, MemberRepository memberRepository,
                                   PlaylistRepository playlistRepository, GroupMapper groupMapper,
                                   ApplicationEventPublisher eventPublisher, PlaylistAccessService playlistAccessService) {
            return new GroupService(groupRepository, memberRepository, playlistRepository, groupMapper, eventPublisher, playlistAccessService);
        }
    }

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg18");

    // Flyway runs by hand before the Spring context exists, same reasoning as
    // SongApiCompatibilityAfterMigrationTest: the autoconfigured Flyway bean's timing
    // relative to @BeforeAll isn't guaranteed, running it up front removes the question.
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> false);
    }

    @BeforeAll
    static void migrate() throws SQLException {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
    }

    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlaylistRepository playlistRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User persistUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setRole(Role.USER);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setImageUrl(username + "-avatar.png");
        return userRepository.save(user);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, null));
    }

    @Test
    void fullLifecycleCreateJoinByBothCodesUpdateSettingsAndStartASession() {
        User admin = persistUser("lifecycle-admin");
        User inviteLinkJoiner = persistUser("lifecycle-invite-joiner");
        User joinCodeJoiner = persistUser("lifecycle-code-joiner");

        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        assertThat(created.status()).isEqualTo(GroupStatus.OPEN);
        assertThat(created.joinCode()).matches("^[A-Z]{4}$");

        authenticateAs(inviteLinkJoiner);
        GroupDetailDTO afterInviteJoin = groupService.joinGroup(
                new JoinGroupRequest(created.inviteCode(), null, "Invite Joiner Name", null));
        assertThat(afterInviteJoin.members()).hasSize(2);

        authenticateAs(joinCodeJoiner);
        GroupDetailDTO afterCodeJoin = groupService.joinGroup(
                new JoinGroupRequest(null, created.joinCode(), null, null));
        assertThat(afterCodeJoin.members()).hasSize(3);
        MemberDTO codeJoinerMember = afterCodeJoin.members().stream()
                .filter(member -> member.displayName().equals("lifecycle-code-joiner"))
                .findFirst()
                .orElseThrow();
        assertThat(codeJoinerMember.isAdmin()).isFalse();

        Playlist playlist = new Playlist();
        playlist.setName("Lifecycle Playlist");
        playlist.setColor("112233");
        playlist.setInviteCode("lifecycle-playlist-invite");
        playlist.setOwner(admin);
        Playlist savedPlaylist = playlistRepository.save(playlist);

        authenticateAs(admin);
        GroupDetailDTO afterSettingsUpdate = groupService.updateGroupSettings(
                created.id(),
                new UpdateGroupSettingsRequest(Set.of(savedPlaylist.getId()), DjMode.ROTATING, 10));
        assertThat(afterSettingsUpdate.djMode()).isEqualTo(DjMode.ROTATING);
        assertThat(afterSettingsUpdate.winConditionCardCount()).isEqualTo(10);
        assertThat(afterSettingsUpdate.playlists()).extracting("id").containsExactly(savedPlaylist.getId());

        authenticateAs(inviteLinkJoiner);
        assertThatThrownBy(() -> groupService.updateGroupSettings(
                created.id(), new UpdateGroupSettingsRequest(null, DjMode.FIXED, null)))
                .isInstanceOf(AccessDeniedException.class);

        authenticateAs(admin);
        GroupDetailDTO afterStart = groupService.startGameSession(created.id());
        assertThat(afterStart.status()).isEqualTo(GroupStatus.LOCKED);
        assertThat(afterStart.expiresAt()).isNull();

        User lateJoiner = persistUser("lifecycle-late-joiner");
        authenticateAs(lateJoiner);
        assertThatThrownBy(() -> groupService.joinGroup(new JoinGroupRequest(null, created.joinCode(), null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void thePreSessionTimerDeletesAGroupOnceTheSweepRunsPastItsExpiry() {
        User admin = persistUser("pre-session-timer-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));

        var group = groupRepository.findById(created.id()).orElseThrow();
        group.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        groupRepository.save(group);

        groupService.deleteExpiredGroups();

        assertThat(groupRepository.findById(created.id())).isEmpty();
    }

    @Test
    void theBetweenSessionTimerDeletesAGroupAndItsMembersOnceTheSweepRunsPastItsExpiry() {
        User admin = persistUser("between-session-timer-admin");
        User otherMember = persistUser("between-session-timer-member");

        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        authenticateAs(otherMember);
        groupService.joinGroup(new JoinGroupRequest(created.inviteCode(), null, null, null));

        authenticateAs(admin);
        groupService.startGameSession(created.id());
        groupService.recordGameSessionEnded(created.id());

        var group = groupRepository.findById(created.id()).orElseThrow();
        group.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        groupRepository.save(group);

        groupService.deleteExpiredGroups();

        assertThat(groupRepository.findById(created.id())).isEmpty();
    }

    @Test
    void aGroupNotYetPastItsExpiryIsUntouchedBySweep() {
        User admin = persistUser("not-expired-admin");
        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));

        groupService.deleteExpiredGroups();

        assertThat(groupRepository.findById(created.id())).isPresent();
    }

    @Test
    void explicitLeaveRemovesMembershipWhileDisconnectOnlyFlipsTheConnectionFlag() {
        User admin = persistUser("leave-vs-disconnect-admin");
        User otherMember = persistUser("leave-vs-disconnect-member");

        authenticateAs(admin);
        GroupDetailDTO created = groupService.createGroup(new CreateGroupRequest(null, null));
        authenticateAs(otherMember);
        groupService.joinGroup(new JoinGroupRequest(created.inviteCode(), null, null, null));

        groupService.disconnect(created.id());
        GroupDetailDTO afterDisconnect = groupService.getGroup(created.id());
        MemberDTO disconnectedMember = afterDisconnect.members().stream()
                .filter(member -> !member.isAdmin())
                .findFirst()
                .orElseThrow();
        assertThat(disconnectedMember.isConnected()).isFalse();
        assertThat(afterDisconnect.members()).hasSize(2);

        groupService.leaveGroup(created.id());

        authenticateAs(admin);
        GroupDetailDTO afterLeave = groupService.getGroup(created.id());
        assertThat(afterLeave.members()).hasSize(1);
    }
}
