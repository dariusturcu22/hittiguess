package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.voice.IceServer;
import org.dariusturcu.backend.model.voice.TurnCredentialsResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

// Builds the ICE server list a client feeds RTCPeerConnection. STUN is always present so
// most peers connect directly. Cloudflare's pay-as-you-go TURN relay is added only as a
// fallback for connections that cannot be established directly, and only when a real
// Cloudflare key is configured.
//
// The Cloudflare key does not exist in this environment. Until it is provisioned, the
// key-absent branch returns STUN-only credentials and reports turnAvailable=false rather
// than failing; minting real short-lived TURN credentials against Cloudflare's API is the
// deferred piece. When the key is present the credential lifetime is capped so a leaked
// credential expires on its own.
@Service
@Slf4j
public class TurnCredentialsService {

    private static final int CREDENTIAL_TTL_SECONDS = 3600;

    private final String stunUrl;
    private final String cloudflareKeyId;
    private final String cloudflareApiToken;

    public TurnCredentialsService(
            @Value("${voice.turn.stun-url}") String stunUrl,
            @Value("${voice.turn.cloudflare.key-id:}") String cloudflareKeyId,
            @Value("${voice.turn.cloudflare.api-token:}") String cloudflareApiToken) {
        this.stunUrl = stunUrl;
        this.cloudflareKeyId = cloudflareKeyId;
        this.cloudflareApiToken = cloudflareApiToken;
    }

    public TurnCredentialsResponse issueCredentials() {
        IceServer stunServer = IceServer.stun(stunUrl);
        if (!cloudflareConfigured()) {
            return new TurnCredentialsResponse(List.of(stunServer), false);
        }

        IceServer turnServer = mintCloudflareTurnServer();
        return new TurnCredentialsResponse(List.of(stunServer, turnServer), true);
    }

    private boolean cloudflareConfigured() {
        return !cloudflareKeyId.isBlank() && !cloudflareApiToken.isBlank();
    }

    // Placeholder for the real Cloudflare TURN credential mint (a short-lived username and
    // credential from the /v1/turn/keys/{keyId}/credentials/generate endpoint, valid for
    // CREDENTIAL_TTL_SECONDS). Reached only once a key is configured, which has not
    // happened in any environment yet, so the network call itself is deferred.
    private IceServer mintCloudflareTurnServer() {
        throw new UnsupportedOperationException(
                "Cloudflare TURN credential minting is not wired until a Cloudflare key is provisioned");
    }
}
