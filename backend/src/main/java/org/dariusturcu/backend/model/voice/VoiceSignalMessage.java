package org.dariusturcu.backend.model.voice;

// What the server relays to the target member's voice signal queue for one signaling
// step. Carries the original type and payload plus both ends of the exchange:
// senderUserId is filled from the authenticated STOMP principal, targetMemberUserId is
// echoed from the request.
public record VoiceSignalMessage(
        VoiceSignalType type,
        Long senderUserId,
        Long targetMemberUserId,
        String payload) {
}
