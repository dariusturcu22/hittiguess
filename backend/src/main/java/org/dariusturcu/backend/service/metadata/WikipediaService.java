package org.dariusturcu.backend.service.metadata;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.dariusturcu.backend.util.HttpUtils;
import org.dariusturcu.backend.util.MetadataParser;
import org.dariusturcu.backend.util.UrlBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WikipediaService {
    public Map<String, String> search(String title, String artist) {
        try {
            String[] searchTerms = {
                    String.format("%s %s song", title, artist),
                    String.format("%s (song)", title),
                    title
            };

            for (String searchTerm : searchTerms) {
                String url = UrlBuilder.buildWikipediaApiLink(searchTerm);

                HttpURLConnection connection = HttpUtils.createGetConnection(url);
                connection.setRequestProperty("User-Agent", "MusicMetadataApp/2.0");


                if (connection.getResponseCode() == HttpStatus.OK.value()) {
                    JsonNode root = MetadataParser.getJsonRoot(connection);
                    JsonNode searchResults = root.path("query").path("search");

                    if (searchResults.isArray() && !searchResults.isEmpty()) {
                        String pageTitle = searchResults.get(0).path("title").asText();
                        Map<String, String> result = fetchPageContent(pageTitle);
                        if (result != null) return result;
                    }
                }

                Thread.sleep(500);
            }
        } catch (Exception e) {
            log.warn("Wikipedia error: {}", e.getMessage());
        }
        return null;
    }

    private Map<String, String> fetchPageContent(String pageTitle) {
        try {
            String contentUrl = UrlBuilder.buildWikipediaPageLink(pageTitle);

            HttpURLConnection connection = HttpUtils.createGetConnection(contentUrl);
            connection.setRequestProperty("User-Agent", "MusicMetadataApp/2.0");

            JsonNode contentRoot = MetadataParser.getJsonRoot(connection);
            JsonNode pages = contentRoot.path("query").path("pages");

            if (!pages.isEmpty()) {
                JsonNode page = pages.iterator().next();
                String extract = page.path("extract").asText("");
                String releaseInfo = MetadataParser.extractReleaseDateFromText(extract);

                if (!releaseInfo.equals("No release date found")) {
                    Map<String, String> result = new HashMap<>();
                    result.put("title", pageTitle);
                    result.put("release_info", releaseInfo);
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Wikipedia page fetch error: {}", e.getMessage());
        }
        return null;
    }
}
