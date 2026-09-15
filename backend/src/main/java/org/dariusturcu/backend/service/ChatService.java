package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.exception.ResourceNotFoundException;
import org.dariusturcu.backend.exception.ResourceType;
import org.dariusturcu.backend.model.group.ChatMessage;
import org.dariusturcu.backend.model.group.ChatMessageDTO;
import org.dariusturcu.backend.model.group.Group;
import org.dariusturcu.backend.model.group.Member;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.ratelimit.RateLimiterRegistry;
import org.dariusturcu.backend.repository.ChatMessageRepository;
import org.dariusturcu.backend.repository.GroupRepository;
import org.dariusturcu.backend.websocket.GroupDestinations;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Group-scoped chat send and history. Every path is member-only: a non-member cannot send
// to or read a group's chat. Send enforces the message length and the per-user send rate
// limit, persists the message for the life of the group, and broadcasts it to the group's
// chat topic. History returns a bounded, most-recent-first slice.
//
// Broadcasts through SimpMessagingTemplate with the application's own ObjectMapper for the
// same reason GroupBroadcastListener does: the STOMP broker registers only string and
// byte-array converters by default, so a raw record payload would have nothing able to
// convert it.
@Service
@Transactional
public class ChatService {

    static final int MAX_MESSAGES_PER_WINDOW = 5;
    static final Duration RATE_LIMIT_WINDOW = Duration.ofSeconds(10);
    static final int HISTORY_PAGE_SIZE = 50;

    private final ChatMessageRepository chatMessageRepository;
    private final GroupRepository groupRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final AbuseVisibilityEvents abuseVisibilityEvents;
    private final RateLimiterRegistry rateLimiterRegistry =
            new RateLimiterRegistry(MAX_MESSAGES_PER_WINDOW, RATE_LIMIT_WINDOW);

    public ChatService(ChatMessageRepository chatMessageRepository, GroupRepository groupRepository,
                       SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper,
                       AbuseVisibilityEvents abuseVisibilityEvents) {
        this.chatMessageRepository = chatMessageRepository;
        this.groupRepository = groupRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.abuseVisibilityEvents = abuseVisibilityEvents;
    }

    public ChatMessageDTO sendMessage(Long groupId, Long senderUserId, String content) {
        Group group = findGroup(groupId);
        Member sender = requireMembership(group, senderUserId);

        validateContentLength(content);
        enforceRateLimit(groupId, senderUserId);

        ChatMessage message = new ChatMessage();
        message.setGroup(group);
        message.setSender(sender.getUser());
        message.setContent(content);
        message.setCreatedAt(Instant.now());
        ChatMessage saved = chatMessageRepository.save(message);

        ChatMessageDTO result = toDTO(saved, sender);
        broadcast(groupId, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getHistory(Long groupId, Long requestingUserId) {
        Group group = findGroup(groupId);
        requireMembership(group, requestingUserId);

        Pageable mostRecentPage = PageRequest.of(0, HISTORY_PAGE_SIZE);
        List<ChatMessage> messages =
                chatMessageRepository.findByGroupIdOrderByCreatedAtDesc(groupId, mostRecentPage);

        return messages.stream()
                .map(message -> toDTO(message, memberFor(group, message.getSender().getId())))
                .toList();
    }

    private void validateContentLength(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("A chat message cannot be empty");
        }
        if (content.length() > ChatMessage.MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                    "A chat message cannot exceed " + ChatMessage.MAX_CONTENT_LENGTH + " characters");
        }
    }

    private void enforceRateLimit(Long groupId, Long senderUserId) {
        String rateLimitKey = groupId + ":" + senderUserId;
        if (!rateLimiterRegistry.tryConsume(rateLimitKey)) {
            abuseVisibilityEvents.recordChatRateLimitExceeded(senderUserId, groupId);
            throw new RateLimitExceededException(
                    "Sending messages too quickly, wait a moment before sending another");
        }
    }

    private void broadcast(Long groupId, ChatMessageDTO message) {
        messagingTemplate.convertAndSend(
                GroupDestinations.chatTopic(groupId), objectMapper.writeValueAsString(message));
    }

    private Group findGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException(ResourceType.GROUP, groupId));
    }

    private Member requireMembership(Group group, Long userId) {
        return group.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this group"));
    }

    // A history message's sender is always a current group member, so this resolves the
    // sender's per-group display name from the same member list the group already holds.
    private Member memberFor(Group group, Long senderUserId) {
        return group.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(senderUserId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this group"));
    }

    private ChatMessageDTO toDTO(ChatMessage message, Member sender) {
        return new ChatMessageDTO(
                message.getId(),
                message.getGroup().getId(),
                sender.getUser().getId(),
                sender.getDisplayName(),
                message.getContent(),
                message.getCreatedAt());
    }

    // Test-only: the rate limiter's buckets otherwise live for the service's lifetime, so a
    // shared Spring context across test methods would carry rate-limit state between them.
    void resetRateLimiterForTesting() {
        rateLimiterRegistry.resetForTesting();
    }
}
