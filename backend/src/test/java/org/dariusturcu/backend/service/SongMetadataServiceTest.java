package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.ai.AiResponse;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SongMetadataServiceTest {

    private static final Long CURRENT_USER_ID = 7L;
    private static final String YOUTUBE_URL = "https://youtube.com/watch?v=dQw4w9WgXcQ";
    private static final String RESOLVE_ENDPOINT = "/metadata/resolve";

    private MockRestServiceServer mockServer;
    private SongMetadataService songMetadataService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        songMetadataService = new SongMetadataService(builder.build());

        User currentUser = new User();
        currentUser.setId(CURRENT_USER_ID);
        currentUser.setUsername("current-user");
        currentUser.setRole(Role.USER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(currentUser), null, null)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void expectResolveCallReturning(String jsonBody) {
        mockServer.expect(requestTo(org.hamcrest.Matchers.endsWith(RESOLVE_ENDPOINT)))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(jsonBody, MediaType.APPLICATION_JSON));
    }

    @Test
    void successfulResolutionPassesThroughContent() {
        expectResolveCallReturning("""
                {"status":"SUCCESS","model":"gpt-5.1","content":{
                  "title":"Title","artist":"Artist","release_year":1999,
                  "color":"8B5CF6",
                  "confidence":"high","source":"MusicBrainz","reasoning":"Matched."}}
                """);

        AiResponse result = songMetadataService.fetchMetadata(YOUTUBE_URL);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.content()).isNotNull();
        assertThat(result.content().title()).isEqualTo("Title");
        assertThat(result.rejectionReason()).isNull();
        assertThat(songMetadataService.findCachedPreview("dQw4w9WgXcQ")).contains(result);
        mockServer.verify();
    }

    @Test
    void rejectedResolutionCarriesReasonAndDetailWithoutContent() {
        String detail = "The submission's YouTube text was flagged as a prompt-injection attempt.";
        expectResolveCallReturning("""
                {"status":"REJECTED","model":"gpt-5.1","content":null,
                 "rejection_reason":"PROMPT_INJECTION",
                 "rejection_detail":"%s"}
                """.formatted(detail));

        AiResponse result = songMetadataService.fetchMetadata(YOUTUBE_URL);

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.content()).isNull();
        assertThat(result.rejectionReason()).isEqualTo("PROMPT_INJECTION");
        assertThat(result.rejectionDetail()).isEqualTo(detail);
        mockServer.verify();
    }

    @Test
    void errorStatusFromAiServiceMapsToError() {
        expectResolveCallReturning("""
                {"status":"ERROR","model":"gpt-5.1","content":null}
                """);

        AiResponse result = songMetadataService.fetchMetadata(YOUTUBE_URL);

        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.content()).isNull();
        assertThat(result.rejectionReason()).isNull();
        assertThat(result.rejectionDetail()).isNull();
        mockServer.verify();
    }

    @Test
    void transportFailureMapsToError() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.endsWith(RESOLVE_ENDPOINT)))
                .andRespond(withServerError());

        AiResponse result = songMetadataService.fetchMetadata(YOUTUBE_URL);

        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.content()).isNull();
        mockServer.verify();
    }
}
