package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.mapper.GroupMapper;
import org.dariusturcu.backend.repository.GameSessionRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.repository.MemberRepository;
import org.dariusturcu.backend.repository.PlaylistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GroupServiceAdmissionTest {

    private static final Long TEST_GROUP_ID = 99L;

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private GameSessionRepository gameSessionRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private GroupMapper groupMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PlaylistAccessService playlistAccessService;
    @Mock
    private GameSessionAdmissionGuard admissionGuard;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupRepository,
                memberRepository,
                gameSessionRepository,
                playlistRepository,
                groupMapper,
                eventPublisher,
                playlistAccessService,
                admissionGuard
        );
    }

    @Test
    void beginAudioCheckFailsFastWhenAdmissionLimitIsExceeded() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE))
                .when(admissionGuard).requireAdmissionCapacity();

        assertThatThrownBy(() -> groupService.beginAudioCheck(TEST_GROUP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrownException -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) thrownException;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(responseStatusException.getReason()).isEqualTo(GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE);
                });

        verifyNoInteractions(groupRepository);
    }

    @Test
    void startGameSessionFailsFastWhenAdmissionLimitIsExceeded() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE))
                .when(admissionGuard).requireAdmissionCapacity();

        assertThatThrownBy(() -> groupService.startGameSession(TEST_GROUP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrownException -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) thrownException;
                    assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(responseStatusException.getReason()).isEqualTo(GameSessionAdmissionGuard.CAPACITY_EXCEEDED_MESSAGE);
                });

        verifyNoInteractions(groupRepository);
    }
}
