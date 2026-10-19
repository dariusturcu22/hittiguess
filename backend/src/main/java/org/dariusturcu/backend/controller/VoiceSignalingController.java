package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.model.voice.VoiceSignalMessage;
import org.dariusturcu.backend.model.voice.VoiceSignalRequest;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.service.GroupService;
import org.dariusturcu.backend.websocket.GroupDestinations;

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

// Relays one WebRTC signaling step (an SDP offer, an SDP answer, or an ICE candidate)
// from one group member to its target member only, on that member's own per-user voice
// signal queue. Signals carry network addresses, so no other member receives them. The
// counterpart to GameActionController for the voice room: the same STOMP action
// pattern, the same principal-to-user-id resolution, gated on both ends being members of
// the group rather than open to any authenticated socket.
//
// Serializes to JSON with the application's own ObjectMapper before handing the string
// to SimpMessagingTemplate, matching GroupBroadcastListener: the simple broker registers
// only string and byte-array converters, so a raw record payload would have nothing able
// to convert it. The mesh setup, RTCPeerConnection lifecycle, and media handling this
// signaling drives are a browser concern owned by story 28.
@Controller
@RequiredArgsConstructor
@Validated
public class VoiceSignalingController {

    private final GroupService groupService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @MessageMapping("/groups/{groupId}/voice/signal")
    public void relaySignal(@DestinationVariable Long groupId,
                            @Valid @Payload VoiceSignalRequest request,
                            Principal principal) {
        Long senderUserId = resolveUserId(principal);
        if (!groupService.isGroupMember(groupId, senderUserId)) {
            throw new AccessDeniedException("You are not a member of this group");
        }
        if (!groupService.isGroupMember(groupId, request.targetMemberUserId())) {
            throw new AccessDeniedException("The signal's target is not a member of this group");
        }
        String targetUsername = userRepository.findById(request.targetMemberUserId())
                .map(User::getUsername)
                .orElseThrow(() -> new AccessDeniedException("The signal's target does not exist"));

        VoiceSignalMessage relayed = new VoiceSignalMessage(
                request.type(), senderUserId, request.targetMemberUserId(), request.payload());
        messagingTemplate.convertAndSendToUser(
                targetUsername, GroupDestinations.voiceSignalQueue(groupId), objectMapper.writeValueAsString(relayed));
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUser().getId();
        }
        throw new AccessDeniedException("No authenticated user on this STOMP session");
    }
}
