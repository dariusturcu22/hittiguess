package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.model.group.ChatMessage;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.ChatMessageRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long GROUP_ID = 10L;
    private static final Long MEMBER_USER_ID = 1L;
    private static final Long NON_MEMBER_USER_ID = 2L;

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private AbuseVisibilityEvents abuseVisibilityEvents;

    private ChatService chatService;

    private Group group;
    private User memberUser;

    @BeforeEach
    void setUp() {
        memberUser = new User();
        memberUser.setId(MEMBER_USER_ID);
        memberUser.setUsername("member-user");

        group = new Group();
        group.setId(GROUP_ID);
        Member member = new Member();
        member.setId(100L);
        member.setUser(memberUser);
        member.setDisplayName("member-user");
        member.setJoinedAt(Instant.now());
        group.addMember(member);

        chatService = new ChatService(chatMessageRepository, groupRepository, messagingTemplate,
                new ObjectMapper(), abuseVisibilityEvents);

        lenient().when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        lenient().when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            saved.setId(500L);
            return saved;
        });
    }

    @Test
    void sendMessagePersistsAndBroadcastsAMessageAtTheLengthLimit() {
        String maxLengthContent = "x".repeat(ChatMessage.MAX_CONTENT_LENGTH);

        chatService.sendMessage(GROUP_ID, MEMBER_USER_ID, maxLengthContent);

        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void sendMessageRejectsContentOverTheLengthLimit() {
        String tooLongContent = "x".repeat(ChatMessage.MAX_CONTENT_LENGTH + 1);

        assertThatThrownBy(() -> chatService.sendMessage(GROUP_ID, MEMBER_USER_ID, tooLongContent))
                .isInstanceOf(IllegalArgumentException.class);

        verify(chatMessageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void sendMessageRejectsBlankContent() {
        assertThatThrownBy(() -> chatService.sendMessage(GROUP_ID, MEMBER_USER_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessageRejectsANonMember() {
        assertThatThrownBy(() -> chatService.sendMessage(GROUP_ID, NON_MEMBER_USER_ID, "hello"))
                .isInstanceOf(AccessDeniedException.class);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessageUnderTheRateLimitPasses() {
        for (int messageNumber = 0; messageNumber < ChatService.MAX_MESSAGES_PER_WINDOW; messageNumber++) {
            String content = "message " + messageNumber;
            assertThatCode(() -> chatService.sendMessage(GROUP_ID, MEMBER_USER_ID, content))
                    .doesNotThrowAnyException();
        }

        verify(chatMessageRepository, times(ChatService.MAX_MESSAGES_PER_WINDOW)).save(any(ChatMessage.class));
        verify(abuseVisibilityEvents, never()).recordChatRateLimitExceeded(anyLong(), anyLong());
    }

    @Test
    void sendMessageOverTheRateLimitRejectsAndFiresTheStubbedEvent() {
        for (int messageNumber = 0; messageNumber < ChatService.MAX_MESSAGES_PER_WINDOW; messageNumber++) {
            chatService.sendMessage(GROUP_ID, MEMBER_USER_ID, "message " + messageNumber);
        }

        assertThatThrownBy(() -> chatService.sendMessage(GROUP_ID, MEMBER_USER_ID, "one too many"))
                .isInstanceOf(RateLimitExceededException.class);

        verify(chatMessageRepository, times(ChatService.MAX_MESSAGES_PER_WINDOW)).save(any(ChatMessage.class));
        verify(abuseVisibilityEvents).recordChatRateLimitExceeded(MEMBER_USER_ID, GROUP_ID);
    }

    @Test
    void theRateLimitIsPerUserSoASecondSendersFirstMessagesPass() {
        User secondUser = new User();
        secondUser.setId(3L);
        secondUser.setUsername("second-user");
        Member secondMember = new Member();
        secondMember.setId(101L);
        secondMember.setUser(secondUser);
        secondMember.setDisplayName("second-user");
        secondMember.setJoinedAt(Instant.now());
        group.addMember(secondMember);

        for (int messageNumber = 0; messageNumber < ChatService.MAX_MESSAGES_PER_WINDOW; messageNumber++) {
            chatService.sendMessage(GROUP_ID, MEMBER_USER_ID, "first sender " + messageNumber);
        }

        assertThatCode(() -> chatService.sendMessage(GROUP_ID, secondUser.getId(), "second sender first message"))
                .doesNotThrowAnyException();
    }
}
