package org.dariusturcu.backend.model.voice;

import java.util.List;

// One entry in the ICE server list a client hands RTCPeerConnection. A STUN entry
// carries only urls; a TURN entry also carries the short-lived username and credential
// the client authenticates to the relay with. Shape mirrors the browser's own
// RTCIceServer dictionary so the client passes it straight through.
public record IceServer(
        List<String> urls,
        String username,
        String credential) {

    public static IceServer stun(String url) {
        return new IceServer(List.of(url), null, null);
    }
}
