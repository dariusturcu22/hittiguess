package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.voice.IceServer;
import org.dariusturcu.backend.model.voice.TurnCredentialsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TurnCredentialsServiceTest {

    private static final String STUN_URL = "stun:stun.cloudflare.com:3478";
    private static final String KEY_ID = "some-key-id";
    private static final String API_TOKEN = "some-api-token";
    private static final String MINT_ENDPOINT = "/v1/turn/keys/" + KEY_ID + "/credentials/generate-ice-servers";

    private TurnCredentialsService serviceWithMockedCloudflare(MockRestServiceServer[] mockServerOut) {
        RestClient.Builder builder = RestClient.builder();
        mockServerOut[0] = MockRestServiceServer.bindTo(builder).build();
        return new TurnCredentialsService(STUN_URL, KEY_ID, API_TOKEN, builder.build());
    }

    @Test
    void withNoCloudflareKeyItReturnsStunOnlyAndReportsTurnUnavailable() {
        TurnCredentialsService service =
                new TurnCredentialsService(STUN_URL, "", "", RestClient.create());

        TurnCredentialsResponse response = service.issueCredentials();

        assertThat(response.turnAvailable()).isFalse();
        assertThat(response.iceServers()).hasSize(1);
        IceServer only = response.iceServers().getFirst();
        assertThat(only.urls()).containsExactly(STUN_URL);
        assertThat(only.username()).isNull();
        assertThat(only.credential()).isNull();
    }

    @Test
    void aBlankApiTokenAloneStillLeavesTurnUnavailable() {
        TurnCredentialsService service =
                new TurnCredentialsService(STUN_URL, "some-key-id", "", RestClient.create());

        TurnCredentialsResponse response = service.issueCredentials();

        assertThat(response.turnAvailable()).isFalse();
        assertThat(response.iceServers()).hasSize(1);
    }

    @Test
    void withACloudflareKeyItPicksTheTurnEntryOutOfCloudflaresBundledStunAndTurnResponse() {
        MockRestServiceServer[] mockServerOut = new MockRestServiceServer[1];
        TurnCredentialsService service = serviceWithMockedCloudflare(mockServerOut);
        // Cloudflare's real response bundles a bare STUN echo (no username) ahead of the
        // actual TURN entry; picking iceServers[0] would silently hand out STUN twice.
        mockServerOut[0].expect(requestTo(org.hamcrest.Matchers.endsWith(MINT_ENDPOINT)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"iceServers":[{"urls":["stun:stun.cloudflare.com:3478"]},{"urls":["turn:turn.cloudflare.com:3478?transport=udp"],"username":"user123","credential":"secret456"}]}
                        """, MediaType.APPLICATION_JSON));

        TurnCredentialsResponse response = service.issueCredentials();

        assertThat(response.turnAvailable()).isTrue();
        assertThat(response.iceServers()).hasSize(2);
        IceServer turnServer = response.iceServers().get(1);
        assertThat(turnServer.urls()).containsExactly("turn:turn.cloudflare.com:3478?transport=udp");
        assertThat(turnServer.username()).isEqualTo("user123");
        assertThat(turnServer.credential()).isEqualTo("secret456");
    }

    @Test
    void aFailedCloudflareMintFallsBackToStunOnlyInsteadOfBlockingVoice() {
        MockRestServiceServer[] mockServerOut = new MockRestServiceServer[1];
        TurnCredentialsService service = serviceWithMockedCloudflare(mockServerOut);
        mockServerOut[0].expect(requestTo(org.hamcrest.Matchers.endsWith(MINT_ENDPOINT)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        TurnCredentialsResponse response = service.issueCredentials();

        assertThat(response.turnAvailable()).isFalse();
        assertThat(response.iceServers()).hasSize(1);
        assertThat(response.iceServers().getFirst().urls()).containsExactly(STUN_URL);
    }
}
