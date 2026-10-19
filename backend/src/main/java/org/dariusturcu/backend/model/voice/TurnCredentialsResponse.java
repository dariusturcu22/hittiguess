package org.dariusturcu.backend.model.voice;

import java.util.List;

// The ICE configuration a member fetches before joining the mesh. iceServers is the list
// a client hands RTCPeerConnection directly. turnAvailable is false when no Cloudflare
// TURN key is configured, in which case iceServers holds STUN only and a peer behind a
// symmetric NAT has no relay to fall back to; the client can surface that state.
public record TurnCredentialsResponse(
        List<IceServer> iceServers,
        boolean turnAvailable) {
}
