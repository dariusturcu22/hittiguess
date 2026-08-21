package org.dariusturcu.backend.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UrlBuilder {
    public static String buildYoutubeApiLink(String videoId, String apiKey) {
        String encodedId = URLEncoder.encode(videoId, StandardCharsets.UTF_8);
        return "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id=" +
                encodedId + "&key=" + apiKey;
    }

    public static String buildMusicBrainzApiLink(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "https://musicbrainz.org/ws/2/recording/?query=" + encoded + "&fmt=json&limit=10";
    }

    public static String buildWikipediaApiLink(String searchTerm) {
        String encoded = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        return "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" +
                encoded + "&format=json&srlimit=3";
    }

    public static String buildWikipediaPageLink(String pageTitle) {
        return "https://en.wikipedia.org/w/api.php?action=query&titles=" +
                URLEncoder.encode(pageTitle, StandardCharsets.UTF_8) +
                "&prop=extracts&exintro=true&format=json";
    }

    public static String buildGeniusApiLink(String artist, String title) {
        String query = String.format("%s %s", artist, title);
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "https://genius.com/api/search/multi?q=" + encoded;
    }
}
