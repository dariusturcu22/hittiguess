package org.dariusturcu.backend.service;

import org.dariusturcu.backend.exception.PlaylistImportException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PlaylistExpansionServiceTest {

    private static final String PLAYLIST_LINK = "https://youtube.com/playlist?list=PL123";
    private static final String PLAYLIST_VIDEO_IDS_ENDPOINT = "/metadata/playlist-video-ids";

    private MockRestServiceServer mockServer;
    private PlaylistExpansionService playlistExpansionService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        playlistExpansionService = new PlaylistExpansionService(builder.build());
    }

    private void expectPlaylistCallReturning(String jsonBody) {
        mockServer.expect(requestTo(org.hamcrest.Matchers.endsWith(PLAYLIST_VIDEO_IDS_ENDPOINT)))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(jsonBody, MediaType.APPLICATION_JSON));
    }

    @Test
    void expandPlaylistReturnsTheVideoIdsFromTheAiService() {
        expectPlaylistCallReturning("""
                {"video_ids":["video-one","video-two"]}
                """);

        List<String> videoIds = playlistExpansionService.expandPlaylist(PLAYLIST_LINK);

        assertThat(videoIds).containsExactly("video-one", "video-two");
        mockServer.verify();
    }

    @Test
    void expandPlaylistThrowsWhenTheAiServiceCallFails() {
        mockServer.expect(requestTo(org.hamcrest.Matchers.endsWith(PLAYLIST_VIDEO_IDS_ENDPOINT)))
                .andRespond(withServerError());

        assertThatThrownBy(() -> playlistExpansionService.expandPlaylist(PLAYLIST_LINK))
                .isInstanceOf(PlaylistImportException.class);
        mockServer.verify();
    }

    @Test
    void expandPlaylistThrowsWhenTheAiServiceReturnsNoVideoIds() {
        expectPlaylistCallReturning("""
                {"video_ids":null}
                """);

        assertThatThrownBy(() -> playlistExpansionService.expandPlaylist(PLAYLIST_LINK))
                .isInstanceOf(PlaylistImportException.class);
        mockServer.verify();
    }

    @Test
    void expandAndMergeReturnsTheParsedIdsUnchangedWhenNoPlaylistLinkIsSubmitted() {
        List<String> alreadyParsedIds = List.of("video-a", "video-b");

        List<String> merged = playlistExpansionService.expandAndMerge(null, alreadyParsedIds);

        assertThat(merged).isEqualTo(alreadyParsedIds);
    }

    @Test
    void expandAndMergeReturnsTheParsedIdsUnchangedWhenThePlaylistLinkIsBlank() {
        List<String> alreadyParsedIds = List.of("video-a");

        List<String> merged = playlistExpansionService.expandAndMerge("   ", alreadyParsedIds);

        assertThat(merged).isEqualTo(alreadyParsedIds);
    }

    @Test
    void expandAndMergeDedupesTheExpandedAndAlreadyParsedIds() {
        expectPlaylistCallReturning("""
                {"video_ids":["shared-id","expanded-only"]}
                """);

        List<String> merged = playlistExpansionService.expandAndMerge(PLAYLIST_LINK, List.of("shared-id", "extra-id"));

        assertThat(merged).containsExactlyInAnyOrder("shared-id", "expanded-only", "extra-id");
        assertThat(merged).doesNotHaveDuplicates();
        mockServer.verify();
    }
}
