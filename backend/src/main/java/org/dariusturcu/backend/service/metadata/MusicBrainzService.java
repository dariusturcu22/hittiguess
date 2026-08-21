package org.dariusturcu.backend.service.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.util.HttpUtils;
import org.dariusturcu.backend.util.MetadataParser;
import org.dariusturcu.backend.util.UrlBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MusicBrainzService {
    public List<Map<String, String>> search(String title, String artist) {
        List<Map<String, String>> results = new ArrayList<>();

        try {
            List<String> queries = buildQueries(title, artist);

            for (String query : queries) {
                String apiUrl = UrlBuilder.buildMusicBrainzApiLink(query);

                HttpURLConnection connection = HttpUtils.createGetConnection(apiUrl);
                connection.setRequestProperty("User-Agent", "MusicMetadataApp/2.0 (example@example.com)");

                if (connection.getResponseCode() == HttpStatus.OK.value()) {
                    JsonNode root = MetadataParser.getJsonRoot(connection);
                    JsonNode recordings = root.path("recordings");

                    if (recordings.isArray() && !recordings.isEmpty()) {
                        for (int i = 0; i < Math.min(10, recordings.size()); i++) {
                            results.add(parseRecording(recordings.get(i)));
                        }
                        if (!results.isEmpty()) break;
                    }
                }

                // MusicBrainz requires 1 request per second
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.warn("MusicBrainz error: {}", e.getMessage());
        }
        return results;
    }

    private List<String> buildQueries(String title, String artist) {
        String safeTitle = escapeLucene(title);
        String safeArtist = escapeLucene(artist);

        List<String> queries = new ArrayList<>();
        queries.add(String.format("recording:\"%s\" AND artist:\"%s\"", safeTitle, safeArtist));
        queries.add(String.format("recording:\"%s\"", safeTitle));
        queries.add(String.format("\"%s\" \"%s\"", safeTitle, safeArtist));
        queries.add(safeTitle + " " + safeArtist);
        return queries;
    }

    private String escapeLucene(String value) {
        if (value == null) return "";
        return value.replaceAll("([+\\-!(){}\\[\\]^\"~*?:\\\\&|/])", "\\\\$1");
    }

    private Map<String, String> parseRecording(JsonNode rec) {
        Map<String, String> item = new HashMap<>();

        JsonNode artistCredit = rec.path("artist-credit");
        item.put("artist", artistCredit.isArray() && !artistCredit.isEmpty()
                ? artistCredit.get(0).path("name").asText("unknown")
                : "unknown");

        item.put("title", rec.path("title").asText("unknown"));
        item.put("release_date", rec.path("first-release-date").asText("unknown"));
        item.put("score", String.valueOf(rec.path("score").asInt(0)));

        JsonNode tags = rec.path("tags");
        if (tags.isArray() && !tags.isEmpty()) {
            StringBuilder tagStr = new StringBuilder();
            for (int j = 0; j < Math.min(3, tags.size()); j++) {
                tagStr.append(tags.get(j).path("name").asText("")).append(", ");
            }
            item.put("tags", tagStr.toString());
        }

        return item;
    }
}
