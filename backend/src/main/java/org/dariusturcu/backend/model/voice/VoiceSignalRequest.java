package org.dariusturcu.backend.model.voice;

import jakarta.validation.constraints.NotNull;

// What a member sends to /app/groups/{groupId}/voice/signal to relay a WebRTC handshake
// step to one other member of the same group. targetMemberUserId names the intended
// recipient; the server does not read payload, the opaque SDP or ICE-candidate body the
// two peers exchange. The sender is taken from the authenticated STOMP principal, never
// from the request, so a member cannot forge signaling as someone else.
public record VoiceSignalRequest(
        @NotNull VoiceSignalType type,
        @NotNull Long targetMemberUserId,
        @NotNull String payload) {
}
