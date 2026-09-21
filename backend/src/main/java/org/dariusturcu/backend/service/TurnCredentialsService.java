package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.voice.CloudflareIceServersResponse;
import org.dariusturcu.backend.model.voice.CloudflareTurnCredentialRequest;
import org.dariusturcu.backend.model.voice.IceServer;
import org.dariusturcu.backend.model.voice.TurnCredentialsResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

// Builds the ICE server list a client feeds RTCPeerConnection. STUN is always present so
// most peers connect directly. Cloudflare's pay-as-you-go TURN relay is added only as a
// fallback for connections that cannot be established directly, and only when a real
// Cloudflare key is configured; if minting a credential fails (Cloudflare outage, revoked
// key), a member still gets STUN-only credentials rather than being unable to join voice
// at all. The credential lifetime is capped so a leaked short-lived credential expires on
// its own.
@Service
@Slf4j
public class TurnCredentialsService {

    private static final int CREDENTIAL_TTL_SECONDS = 3600;
    private static final String GENERATE_ICE_SERVERS_PATH = "/v1/turn/keys/{keyId}/credentials/generate-ice-servers";

    private final String stunUrl;
    private final String cloudflareKeyId;
    private final String cloudflareApiToken;
    private final RestClient cloudflareTurnRestClient;

    public TurnCredentialsService(
            @Value("${voice.turn.stun-url}") String stunUrl,
            @Value("${voice.turn.cloudflare.key-id:}") String cloudflareKeyId,
            @Value("${voice.turn.cloudflare.api-token:}") String cloudflareApiToken,
            RestClient cloudflareTurnRestClient) {
        this.stunUrl = stunUrl;
        this.cloudflareKeyId = cloudflareKeyId;
        this.cloudflareApiToken = cloudflareApiToken;
        this.cloudflareTurnRestClient = cloudflareTurnRestClient;
    }

    public TurnCredentialsResponse issueCredentials() {
        IceServer stunServer = IceServer.stun(stunUrl);
        if (!cloudflareConfigured()) {
            return new TurnCredentialsResponse(List.of(stunServer), false);
        }

        return mintCloudflareTurnServer()
                .map(turnServer -> new TurnCredentialsResponse(List.of(stunServer, turnServer), true))
                .orElseGet(() -> new TurnCredentialsResponse(List.of(stunServer), false));
    }

    private boolean cloudflareConfigured() {
        return !cloudflareKeyId.isBlank() && !cloudflareApiToken.isBlank();
    }

    private Optional<IceServer> mintCloudflareTurnServer() {
        try {
            CloudflareIceServersResponse response = cloudflareTurnRestClient.post()
                    .uri(GENERATE_ICE_SERVERS_PATH, cloudflareKeyId)
                    .body(new CloudflareTurnCredentialRequest(CREDENTIAL_TTL_SECONDS))
                    .retrieve()
                    .body(CloudflareIceServersResponse.class);

            if (response == null || response.iceServers() == null) {
                log.warn("Cloudflare returned no ICE servers for a TURN credential mint");
                return Optional.empty();
            }
            // Cloudflare's response bundles a bare STUN echo alongside the actual TURN
            // entry; the TURN entry is the one carrying a username, so that is what
            // distinguishes it rather than list position.
            Optional<IceServer> turnServer = response.iceServers().stream()
                    .filter(server -> server.username() != null && !server.username().isBlank())
                    .findFirst();
            if (turnServer.isEmpty()) {
                log.warn("Cloudflare's response had no TURN entry with credentials");
            }
            return turnServer;
        } catch (Exception cloudflareCallFailure) {
            log.warn("Cloudflare TURN credential mint failed: {}", cloudflareCallFailure.getMessage());
            return Optional.empty();
        }
    }
}
