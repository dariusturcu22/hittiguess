package org.dariusturcu.backend.service.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.util.HttpUtils;
import org.dariusturcu.backend.util.MetadataParser;
import org.dariusturcu.backend.util.UrlBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class GeniusService {
    public Map<String, String> search(String title, String artist) {
        try {
            String url = UrlBuilder.buildGeniusApiLink(artist, title);

            HttpURLConnection connection = HttpUtils.createGetConnection(url);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            if (connection.getResponseCode() == HttpStatus.OK.value()) {
                JsonNode root = MetadataParser.getJsonRoot(connection);
                JsonNode sections = root.path("response").path("sections");

                for (JsonNode section : sections) {
                    JsonNode hits = section.path("hits");
                    if (hits.isArray() && !hits.isEmpty()) {
                        JsonNode firstHit = hits.get(0).path("result");

                        Map<String, String> result = new HashMap<>();
                        result.put("title", firstHit.path("title").asText("unknown"));
                        result.put("artist", firstHit.path("primary_artist").path("name").asText("unknown"));
                        result.put("release_date", firstHit.path("release_date_for_display").asText("unknown"));
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Genius error: {}", e.getMessage());
        }
        return null;
    }
}
