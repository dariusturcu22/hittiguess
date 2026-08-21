package org.dariusturcu.backend.service.metadata;

import tools.jackson.databind.JsonNode;
import org.dariusturcu.backend.util.HttpUtils;
import org.dariusturcu.backend.util.MetadataParser;
import org.dariusturcu.backend.util.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

@Component
public class YouTubeMetadataService {
    @Value("${youtube.api.key}")
    private String youtubeApiKey;

    public Map<String, String> fetchMetadata(String url) {
        Map<String, String> result = new HashMap<>();
        try {
            String videoId = MetadataParser.extractYouTubeVideoId(url);
            String apiUrl = UrlBuilder.buildYoutubeApiLink(videoId, youtubeApiKey);

            HttpURLConnection connection = HttpUtils.createGetConnection(apiUrl);

            JsonNode jsonRoot = MetadataParser.getJsonRoot(connection);
            JsonNode items = jsonRoot.path("items");

            if (items.isArray() && !items.isEmpty()) {
                JsonNode snippet = items.get(0).path("snippet");

                result.put("channel_title", snippet.path("channelTitle").asText("unknown"));
                result.put("video_title", snippet.path("title").asText("unknown"));
                result.put("description", snippet.path("description").asText(""));
                result.put("tags", snippet.path("tags").toString());

                String publishedAt = snippet.path("publishedAt").asText("unknown");
                result.put("upload_date", publishedAt.length() >= 10 ? publishedAt.substring(0, 10) : "unknown");
                result.put("upload_year", publishedAt.length() >= 4 ? publishedAt.substring(0, 4) : "unknown");

            } else {
                setUnknownDefaults(result);
            }
        } catch (Exception e) {
            setUnknownDefaults(result);
        }
        return result;
    }

    private void setUnknownDefaults(Map<String, String> result) {
        result.put("channel_title", "unknown");
        result.put("video_title", "unknown");
        result.put("description", "");
        result.put("upload_date", "unknown");
        result.put("upload_year", "unknown");
        result.put("tags", "[]");
    }
}
