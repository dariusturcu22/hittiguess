package org.dariusturcu.backend.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class YoutubeLinkParserTest {

    private static final String EXPECTED_VIDEO_ID = "dQw4w9WgXcQ";

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
            "https://www.youtube.com/watch?list=PL123",
            "short",
            "waytoolongtobeavalidvideoid",
    })
    void returnsEmptyForAnythingThatIsNotAYoutubeLinkOrId(String nonYoutubeInput) {
        Optional<String> videoId = YoutubeLinkParser.parseVideoId(nonYoutubeInput);

        assertThat(videoId).isEmpty();
    }
}
