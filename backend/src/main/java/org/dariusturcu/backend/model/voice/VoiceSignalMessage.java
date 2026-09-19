package org.dariusturcu.backend.model.voice;

// What the server relays onto the group's voice topic for one signaling step. Carries
// the original type and payload plus both ends of the exchange: senderUserId is filled
// from the authenticated STOMP principal, targetMemberUserId is echoed from the request.
// Every member of the group receives it over the shared topic and ignores anything not
// addressed to them, matching the group's broadcast-the-whole-thing convention rather
// than a per-user destination the broker is not configured for.
public record VoiceSignalMessage(
        VoiceSignalType type,
        Long senderUserId,
        Long targetMemberUserId,
        String payload) {
}
