package org.dariusturcu.backend.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extracts a YouTube video ID from either a bare ID or a full YouTube link
 * (watch URL, youtu.be short link, or embed URL). Mirrors extractYoutubeId in
 * frontend/app/(dashboard)/playlists/[playlistId]/songs/add/AddSongForm.tsx,
 * so the backend accepts exactly the range of inputs the frontend already
 * extracts a video ID from.
 */
public final class YoutubeLinkParser {

    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$");
    private static final String YOUTUBE_SHORT_LINK_HOST = "youtu.be";
    private static final String YOUTUBE_EMBED_PATH_PREFIX = "/embed/";
    private static final String YOUTUBE_WATCH_QUERY_PARAM = "v";
    private static final String QUERY_PARAM_SEPARATOR = "&";
    private static final String QUERY_PARAM_ASSIGNMENT = "=";

    private YoutubeLinkParser() {
    }

    public static Optional<String> parseVideoId(String input) {
        String trimmedInput = input.trim();

        if (YOUTUBE_VIDEO_ID_PATTERN.matcher(trimmedInput).matches()) {
            return Optional.of(trimmedInput);
        }

        String candidate = extractCandidateFromUrl(trimmedInput);
        return candidate != null && YOUTUBE_VIDEO_ID_PATTERN.matcher(candidate).matches()
                ? Optional.of(candidate)
                : Optional.empty();
    }

    private static String extractCandidateFromUrl(String trimmedInput) {
        URI parsedUri;
        try {
            parsedUri = new URI(trimmedInput);
        } catch (URISyntaxException malformedUri) {
            return null;
        }

        String host = parsedUri.getHost();
        if (host == null) {
            return null;
        }

        String watchVideoId = extractQueryParam(parsedUri.getQuery(), YOUTUBE_WATCH_QUERY_PARAM);
        if (watchVideoId != null) {
            return watchVideoId;
        }

        if (YOUTUBE_SHORT_LINK_HOST.equals(host)) {
            String path = parsedUri.getPath();
            return path != null && path.startsWith("/") ? path.substring(1) : path;
        }

        String path = parsedUri.getPath();
        if (path != null && path.startsWith(YOUTUBE_EMBED_PATH_PREFIX)) {
            return path.substring(YOUTUBE_EMBED_PATH_PREFIX.length());
        }

        return null;
    }

    private static String extractQueryParam(String query, String paramName) {
        if (query == null) {
            return null;
        }

        for (String parameterPair : query.split(QUERY_PARAM_SEPARATOR)) {
            int assignmentIndex = parameterPair.indexOf(QUERY_PARAM_ASSIGNMENT);
            String key = assignmentIndex >= 0 ? parameterPair.substring(0, assignmentIndex) : parameterPair;
            if (paramName.equals(key)) {
                return assignmentIndex >= 0 ? parameterPair.substring(assignmentIndex + 1) : "";
            }
        }

        return null;
    }
}
