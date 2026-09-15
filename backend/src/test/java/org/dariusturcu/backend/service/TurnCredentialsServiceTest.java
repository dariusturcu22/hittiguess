package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.voice.IceServer;
import org.dariusturcu.backend.model.voice.TurnCredentialsResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TurnCredentialsServiceTest {

    private static final String STUN_URL = "stun:stun.cloudflare.com:3478";

    @Test
    void withNoCloudflareKeyItReturnsStunOnlyAndReportsTurnUnavailable() {
        TurnCredentialsService service = new TurnCredentialsService(STUN_URL, "", "");

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
        TurnCredentialsService service = new TurnCredentialsService(STUN_URL, "some-key-id", "");

        TurnCredentialsResponse response = service.issueCredentials();

        assertThat(response.turnAvailable()).isFalse();
        assertThat(response.iceServers()).hasSize(1);
    }
}
