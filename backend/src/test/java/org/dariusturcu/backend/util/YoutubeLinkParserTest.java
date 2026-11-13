package org.dariusturcu.backend.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YoutubeLinkParserTest {

    private static final String EXPECTED_VIDEO_ID = "dQw4w9WgXcQ";
    private static final String EXPECTED_PLAYLIST_ID = "PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4";

    @Test
    void parsesABareVideoId() {
        Optional<String> videoId = YoutubeLinkParser.parseVideoId(EXPECTED_VIDEO_ID);

        assertThat(videoId).contains(EXPECTED_VIDEO_ID);
    }

    @Test
    void parsesABareVideoIdWithSurroundingWhitespace() {
        Optional<String> videoId = YoutubeLinkParser.parseVideoId("  " + EXPECTED_VIDEO_ID + "  ");

        assertThat(videoId).contains(EXPECTED_VIDEO_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ&list=PL123",
            "http://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?t=30",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
    })
    void parsesTheVideoIdOutOfEveryLinkShapeTheFrontendAccepts(String youtubeLink) {
        Optional<String> videoId = YoutubeLinkParser.parseVideoId(youtubeLink);

        assertThat(videoId).contains(EXPECTED_VIDEO_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "bohemian rhapsody queen",
            "not a url",
            "https://example.com/dQw4w9WgXcQ",
            "https://example.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?list=PL123",
            "short",
            "waytoolongtobeavalidvideoid",
            "http://localhost:8080/watch?v=dQw4w9WgXcQ",
            "http://127.0.0.1:8080/watch?v=dQw4w9WgXcQ",
            "http://127.0.0.1/embed/dQw4w9WgXcQ",
            "http://169.254.169.254/watch?v=dQw4w9WgXcQ",
            "http://10.0.0.1/watch?v=dQw4w9WgXcQ",
            "http://192.168.1.1/watch?v=dQw4w9WgXcQ",
            "file:///etc/passwd?v=dQw4w9WgXcQ",
            "gopher://localhost/watch?v=dQw4w9WgXcQ",
            "http://internal.service.local/watch?v=dQw4w9WgXcQ",
    })
    void returnsEmptyForAnythingThatIsNotAYoutubeLinkOrId(String nonYoutubeInput) {
        Optional<String> videoId = YoutubeLinkParser.parseVideoId(nonYoutubeInput);

        assertThat(videoId).isEmpty();
    }

    @Test
    void parsesABarePlaylistId() {
        Optional<String> playlistId = YoutubeLinkParser.parsePlaylistId(EXPECTED_PLAYLIST_ID);

        assertThat(playlistId).contains(EXPECTED_PLAYLIST_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "https://youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "http://m.youtube.com/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "https://music.youtube.com/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
    })
    void parsesValidPlaylistUrls(String playlistLink) {
        Optional<String> playlistId = YoutubeLinkParser.parsePlaylistId(playlistLink);

        assertThat(playlistId).contains(EXPECTED_PLAYLIST_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "http://127.0.0.1:8080/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "http://169.254.169.254/?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "http://10.0.0.1/?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "http://192.168.0.1/?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "http://service.internal/?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "file:///etc/hosts?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
            "localhost",
            "internal",
            "http://attacker.com/playlist?list=PLrAl5_J2KA_E7b6P_jJ1n4S7A1E1oP8Q4",
    })
    void rejectsPlaylistUrlsPointedAtInternalAddressesOrNonYoutubeHosts(String invalidInput) {
        Optional<String> playlistId = YoutubeLinkParser.parsePlaylistId(invalidInput);

        assertThat(playlistId).isEmpty();
    }

    @Test
    void buildsTheCanonicalWatchUrlForAVideoId() {
        String watchUrl = YoutubeLinkParser.buildWatchUrl(EXPECTED_VIDEO_ID);

        assertThat(watchUrl).isEqualTo("https://www.youtube.com/watch?v=" + EXPECTED_VIDEO_ID);
    }

    @Test
    void buildsTheWatchUrlAfterTrimmingSurroundingWhitespace() {
        String watchUrl = YoutubeLinkParser.buildWatchUrl("  " + EXPECTED_VIDEO_ID + "  ");

        assertThat(watchUrl).isEqualTo("https://www.youtube.com/watch?v=" + EXPECTED_VIDEO_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "waytoolongtobeavalidvideoid", "not a url"})
    void rejectsAWatchUrlRequestForAnythingThatIsNotAValidVideoId(String invalidVideoId) {
        assertThatThrownBy(() -> YoutubeLinkParser.buildWatchUrl(invalidVideoId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
