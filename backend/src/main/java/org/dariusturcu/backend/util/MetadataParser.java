package org.dariusturcu.backend.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetadataParser {

    private static final Pattern YOUTUBE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$");

    public static String extractYouTubeVideoId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        url = url.trim();
        String candidate = null;

        if (url.contains("youtu.be/")) {
            candidate = url.substring(url.lastIndexOf("/") + 1).split("[?&#]")[0];
        } else if (url.contains("youtube.com/watch")) {
            int vIndex = url.indexOf("v=");
            if (vIndex != -1) {
                candidate = url.substring(vIndex + 2).split("&")[0];
            }
        } else if (url.contains("youtube.com/embed/")) {
            candidate = url.substring(url.indexOf("/embed/") + 7).split("[?&#]")[0];
        } else if (url.contains("youtube.com/v/")) {
            candidate = url.substring(url.indexOf("/v/") + 3).split("[?&#]")[0];
        } else if (url.contains("youtube.com/shorts/")) {
            candidate = url.substring(url.indexOf("/shorts/") + 8).split("[?&#]")[0];
        } else {
            candidate = url;
        }

        return candidate != null && YOUTUBE_ID_PATTERN.matcher(candidate).matches() ? candidate : null;
    }

    public static String cleanYouTubeText(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)\\(.*official.*?\\)", "")
                .replaceAll("(?i)\\[.*official.*?]", "")
                .replaceAll("(?i)(official|video|audio|lyric|lyrics|hd|4k|hq)", "")
                .replaceAll("(?i)(feat\\.|ft\\.|featuring).*", "")
                .replaceAll("[–—]", "-")
                .replaceAll("[()\\[\\]!?]", " ")
                .trim()
                .replaceAll("\\s{2,}", " ");
    }

    public static String extractReleaseDateFromText(String text) {
        if (text == null || text.isEmpty()) return "No release date found";

        Pattern[] patterns = {
                Pattern.compile("released on ([A-Za-z]+ \\d{1,2}, \\d{4})"),
                Pattern.compile("released in ([A-Za-z]+ \\d{4})"),
                Pattern.compile("released (\\d{4})"),
                Pattern.compile("(\\d{4}) single"),
                Pattern.compile("(\\d{4}) song")
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return "Released: " + matcher.group(1);
            }
        }

        return "No release date found";
    }

    public static String readInputStream(InputStream inputStream) {
        Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }

    public static JsonNode getJsonRoot(HttpURLConnection connection) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String response = MetadataParser.readInputStream(connection.getInputStream());
        return mapper.readTree(response);
    }
}
