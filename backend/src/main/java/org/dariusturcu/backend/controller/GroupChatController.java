package org.dariusturcu.backend.controller;

import org.dariusturcu.backend.model.group.ChatMessageDTO;
import org.dariusturcu.backend.model.group.SendChatMessageRequest;
import org.dariusturcu.backend.security.UserPrincipal;
import org.dariusturcu.backend.security.util.SecurityUtils;
import org.dariusturcu.backend.service.ChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

// The chat surface for a group: a client-to-server STOMP channel to send a message,
// mirroring GameActionController's /app/{...} destinations, and a REST endpoint to load a
// group's history on join or reconnect. Both resolve the caller's own identity, the send
// path from the socket's authenticated principal, the history path from the request-scoped
// security context, and delegate the member-only check to ChatService.
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Group chat", description = "Group-scoped text chat send and history")
public class GroupChatController {

    private final ChatService chatService;

    @MessageMapping("/groups/{groupId}/chat")
    public void sendMessage(@DestinationVariable Long groupId,
                            @Payload SendChatMessageRequest request, Principal principal) {
        chatService.sendMessage(groupId, resolveUserId(principal), request.content());
    }

    @Operation(summary = "Load a group's recent chat history, members only")
    @GetMapping("/{groupId}/chat/messages")
    public ResponseEntity<List<ChatMessageDTO>> getHistory(@PathVariable Long groupId) {
        List<ChatMessageDTO> history =
                chatService.getHistory(groupId, SecurityUtils.getCurrentUser().getId());
        return ResponseEntity.ok(history);
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getUser().getId();
        }
        throw new AccessDeniedException("No authenticated user on this STOMP session");
    }
}
