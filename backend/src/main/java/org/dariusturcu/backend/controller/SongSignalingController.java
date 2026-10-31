package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.exception.RateLimitExceededException;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.model.voice.VoiceSignalMessage;
import org.dariusturcu.backend.model.voice.VoiceSignalRequest;
import org.dariusturcu.backend.ratelimit.RateLimiterRegistry;
import org.dariusturcu.backend.repository.PlayerRepository;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.websocket.SessionDestinations;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

import java.security.Principal;
import java.time.Duration;

// The session-scoped parallel to VoiceSignalingController: relays one WebRTC signaling
// step for the DJ's shared song audio from one session player to its target player only,
// on that player's own per-user song signal queue. Song audio is a session concern, so
// both ends are gated on being a player in the session rather than a member of a group.
// The signaling payloads reuse the voice signal models, whose wire shape is identical.
//
// Serializes to JSON with the application's own ObjectMapper before handing the string
// to SimpMessagingTemplate: the simple broker registers only string and byte-array
// converters, so a raw record payload would have nothing able to convert it. The mesh
// setup, RTCPeerConnection lifecycle, and media handling this signaling drives are a
// browser concern.
@Controller
@RequiredArgsConstructor
@Validated
public class SongSignalingController {

    static final int MAXIMUM_SIGNALS_PER_WINDOW = 30;
    static final Duration SIGNAL_RATE_LIMIT_WINDOW = Duration.ofSeconds(10);

    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private final RateLimiterRegistry signalRateLimiter =
            new RateLimiterRegistry(MAXIMUM_SIGNALS_PER_WINDOW, SIGNAL_RATE_LIMIT_WINDOW);

    @MessageMapping("/sessions/{sessionId}/song/signal")
    public void relaySignal(@DestinationVariable Long sessionId,
                            @Valid @Payload VoiceSignalRequest request,
                            Principal principal) {
        Long senderUserId = resolveUserId(principal);
        if (!playerRepository.existsBySessionIdAndUserId(sessionId, senderUserId)) {
            throw new AccessDeniedException("You are not a player in this session");
        }
        if (!playerRepository.existsBySessionIdAndUserId(sessionId, request.targetMemberUserId())) {
            throw new AccessDeniedException("The signal's target is not a player in this session");
        }
        if (!signalRateLimiter.tryConsume(sessionId + ":" + senderUserId)) {
            throw new RateLimitExceededException("Signaling rate limit exceeded. Please wait a moment.");
        }
        String targetUsername = userRepository.findById(request.targetMemberUserId())
                .map(User::getUsername)
                .orElseThrow(() -> new AccessDeniedException("The signal's target does not exist"));

        VoiceSignalMessage relayed = new VoiceSignalMessage(
                request.type(), senderUserId, request.targetMemberUserId(), request.payload());
        messagingTemplate.convertAndSendToUser(
                targetUsername, SessionDestinations.songSignalQueue(sessionId), objectMapper.writeValueAsString(relayed));
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUser().getId();
        }
        throw new AccessDeniedException("No authenticated user on this STOMP session");
    }

    void resetRateLimiterForTesting() {
        signalRateLimiter.resetForTesting();
    }
}
