package org.dariusturcu.backend.model.voice;

// The three kinds of WebRTC signaling message a mesh peer relays through the group's
// voice topic. The server never inspects the payload beyond routing it; the meaning of
// each type is a client-side WebRTC concern.
public enum VoiceSignalType {
    OFFER,
    ANSWER,
    CANDIDATE
}
