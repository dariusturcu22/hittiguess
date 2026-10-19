package org.dariusturcu.backend.websocket;

// Published when an admin removes a member, so the removed user's sockets can be closed
// once the removal commits. The principal name is the one their STOMP sessions carry.
public record GroupMemberRemovedEvent(Long groupId, String removedPrincipalName) {
}
