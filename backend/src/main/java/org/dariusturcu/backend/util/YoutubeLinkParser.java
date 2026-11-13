package org.dariusturcu.backend.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a YouTube video ID or playlist ID from either a bare ID or a full YouTube link
 * (watch URL, youtu.be short link, embed URL, or playlist URL). Validates that URLs use
 * HTTP or HTTPS and point strictly to legitimate YouTube domains, blocking internal addresses
 * and loopback or private networks.
 */
public final class YoutubeLinkParser {

    private static final Pattern YOUTUBE_VIDEO_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{11}$");
    private static final Pattern YOUTUBE_PLAYLIST_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{2,64}$");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final String YOUTUBE_SHORT_LINK_HOST = "youtu.be";
    private static final String YOUTUBE_EMBED_PATH_PREFIX = "/embed/";
    private static final String YOUTUBE_WATCH_QUERY_PARAM = "v";
    private static final String YOUTUBE_PLAYLIST_QUERY_PARAM = "list";
    private static final String QUERY_PARAM_SEPARATOR = "&";
    private static final String QUERY_PARAM_ASSIGNMENT = "=";
    private static final String YOUTUBE_WATCH_URL_PREFIX = "https://www.youtube.com/watch?v=";
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";
    private static final String URL_PROTOCOL_DELIMITER = "://";
    private static final String PROTOCOL_RELATIVE_PREFIX = "//";
    private static final String PATH_SEPARATOR = "/";
    private static final String QUERY_DELIMITER = "?";
    private static final String LOCALHOST_SUFFIX = ".localhost";
    private static final String LOCAL_SUFFIX = ".local";
    private static final String INTERNAL_SUFFIX = ".internal";
    private static final String IPV6_LOOPBACK = "::1";
    private static final String IPV6_LINK_LOCAL_PREFIX = "fe80:";
    private static final String IPV6_UNIQUE_LOCAL_FC = "fc00:";
    private static final String IPV6_UNIQUE_LOCAL_FD = "fd00:";
    private static final int SUBSTRING_START_INDEX = 1;
    private static final int OCTET_FIRST_INDEX = 1;
    private static final int OCTET_SECOND_INDEX = 2;
    private static final int ANY_LOCAL_NETWORK_PREFIX = 0;
    private static final int LOOPBACK_NETWORK_PREFIX = 127;
    private static final int PRIVATE_10_NETWORK_PREFIX = 10;
    private static final int PRIVATE_172_NETWORK_PREFIX = 172;
    private static final int PRIVATE_172_SECOND_OCTET_MIN = 16;
    private static final int PRIVATE_172_SECOND_OCTET_MAX = 31;
    private static final int PRIVATE_192_NETWORK_PREFIX = 192;
    private static final int PRIVATE_192_SECOND_OCTET = 168;
    private static final int LINK_LOCAL_169_NETWORK_PREFIX = 169;
    private static final int LINK_LOCAL_169_SECOND_OCTET = 254;

    private static final Set<String> ALLOWED_YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be"
    );

    private static final Set<String> RESERVED_INTERNAL_HOSTS = Set.of(
            "localhost",
            "loopback",
            "local",
            "internal"
    );

    private YoutubeLinkParser() {
    }

    public static boolean isAllowedYoutubeHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_YOUTUBE_HOSTS.contains(normalizedHost);
    }

    public static boolean isInternalAddress(String hostOrAddress) {
        if (hostOrAddress == null || hostOrAddress.isBlank()) {
            return true;
        }
        String normalized = hostOrAddress.trim().toLowerCase(Locale.ROOT);
        if (RESERVED_INTERNAL_HOSTS.contains(normalized)
                || normalized.endsWith(LOCALHOST_SUFFIX)
                || normalized.endsWith(LOCAL_SUFFIX)
                || normalized.endsWith(INTERNAL_SUFFIX)) {
            return true;
        }
        if (normalized.equals(IPV6_LOOPBACK)
                || normalized.startsWith(IPV6_LINK_LOCAL_PREFIX)
                || normalized.startsWith(IPV6_UNIQUE_LOCAL_FC)
                || normalized.startsWith(IPV6_UNIQUE_LOCAL_FD)) {
            return true;
        }
        Matcher ipv4Matcher = IPV4_PATTERN.matcher(normalized);
        if (ipv4Matcher.matches()) {
            int firstOctet = Integer.parseInt(ipv4Matcher.group(OCTET_FIRST_INDEX));
            int secondOctet = Integer.parseInt(ipv4Matcher.group(OCTET_SECOND_INDEX));
            if (firstOctet == ANY_LOCAL_NETWORK_PREFIX
                    || firstOctet == LOOPBACK_NETWORK_PREFIX
                    || firstOctet == PRIVATE_10_NETWORK_PREFIX) {
                return true;
            }
            if (firstOctet == PRIVATE_172_NETWORK_PREFIX
                    && secondOctet >= PRIVATE_172_SECOND_OCTET_MIN
                    && secondOctet <= PRIVATE_172_SECOND_OCTET_MAX) {
                return true;
            }
            if (firstOctet == PRIVATE_192_NETWORK_PREFIX && secondOctet == PRIVATE_192_SECOND_OCTET) {
                return true;
            }
            if (firstOctet == LINK_LOCAL_169_NETWORK_PREFIX && secondOctet == LINK_LOCAL_169_SECOND_OCTET) {
                return true;
            }
        }
        return false;
    }

    // The canonical watch URL for a stored video id, resolving on the real YouTube page
    // or app rather than an embedded player.
    public static String buildWatchUrl(String videoId) {
        String trimmedVideoId = videoId.trim();
        if (!YOUTUBE_VIDEO_ID_PATTERN.matcher(trimmedVideoId).matches()) {
            throw new IllegalArgumentException("Not a valid YouTube video id: " + videoId);
        }
        return YOUTUBE_WATCH_URL_PREFIX + trimmedVideoId;
    }

    public static Optional<String> parseVideoId(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
            return Optional.empty();
        }

        if (trimmedInput.contains(URL_PROTOCOL_DELIMITER)
                || trimmedInput.startsWith(PROTOCOL_RELATIVE_PREFIX)
                || trimmedInput.contains(PATH_SEPARATOR)
                || trimmedInput.contains(QUERY_DELIMITER)) {
            String candidate = extractCandidateFromUrl(trimmedInput);
            return candidate != null && YOUTUBE_VIDEO_ID_PATTERN.matcher(candidate).matches()
                    ? Optional.of(candidate)
                    : Optional.empty();
        }

        if (isInternalAddress(trimmedInput)) {
            return Optional.empty();
        }

        return YOUTUBE_VIDEO_ID_PATTERN.matcher(trimmedInput).matches()
                ? Optional.of(trimmedInput)
                : Optional.empty();
    }

    public static Optional<String> parsePlaylistId(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String trimmedInput = input.trim();
        if (trimmedInput.isEmpty()) {
            return Optional.empty();
        }

        if (trimmedInput.contains(URL_PROTOCOL_DELIMITER)
                || trimmedInput.startsWith(PROTOCOL_RELATIVE_PREFIX)
                || trimmedInput.contains(PATH_SEPARATOR)
                || trimmedInput.contains(QUERY_DELIMITER)) {
            String candidate = extractPlaylistCandidateFromUrl(trimmedInput);
            return candidate != null && YOUTUBE_PLAYLIST_ID_PATTERN.matcher(candidate).matches()
                    ? Optional.of(candidate)
                    : Optional.empty();
        }

        if (isInternalAddress(trimmedInput)) {
            return Optional.empty();
        }

        return YOUTUBE_PLAYLIST_ID_PATTERN.matcher(trimmedInput).matches()
                ? Optional.of(trimmedInput)
                : Optional.empty();
    }

    // Parses each submitted video id or link, dropping any input that doesn't resolve to
    // a valid video id, rather than failing the whole submission over one bad entry.
    public static List<String> parseAllVideoIds(List<String> inputs) {
        if (inputs == null) {
            return List.of();
        }
        List<String> parsedIds = new ArrayList<>();
        for (String submittedInput : inputs) {
            parseVideoId(submittedInput).ifPresent(parsedIds::add);
        }
        return parsedIds;
    }

    private static String extractCandidateFromUrl(String trimmedInput) {
        URI parsedUri;
        try {
            parsedUri = new URI(trimmedInput);
        } catch (URISyntaxException malformedUri) {
            return null;
        }

        String scheme = parsedUri.getScheme();
        if (scheme == null || (!HTTP_SCHEME.equalsIgnoreCase(scheme) && !HTTPS_SCHEME.equalsIgnoreCase(scheme))) {
            return null;
        }

        String host = parsedUri.getHost();
        if (host == null || isInternalAddress(host) || !isAllowedYoutubeHost(host)) {
            return null;
        }

        String watchVideoId = extractQueryParam(parsedUri.getQuery(), YOUTUBE_WATCH_QUERY_PARAM);
        if (watchVideoId != null) {
            return watchVideoId;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (YOUTUBE_SHORT_LINK_HOST.equals(normalizedHost)) {
            String path = parsedUri.getPath();
            return path != null && path.startsWith(PATH_SEPARATOR) ? path.substring(SUBSTRING_START_INDEX) : path;
        }

        String path = parsedUri.getPath();
        if (path != null && path.startsWith(YOUTUBE_EMBED_PATH_PREFIX)) {
            return path.substring(YOUTUBE_EMBED_PATH_PREFIX.length());
        }

        return null;
    }

    private static String extractPlaylistCandidateFromUrl(String trimmedInput) {
        URI parsedUri;
        try {
            parsedUri = new URI(trimmedInput);
        } catch (URISyntaxException malformedUri) {
            return null;
        }

        String scheme = parsedUri.getScheme();
        if (scheme == null || (!HTTP_SCHEME.equalsIgnoreCase(scheme) && !HTTPS_SCHEME.equalsIgnoreCase(scheme))) {
            return null;
        }

        String host = parsedUri.getHost();
        if (host == null || isInternalAddress(host) || !isAllowedYoutubeHost(host)) {
            return null;
        }

        return extractQueryParam(parsedUri.getQuery(), YOUTUBE_PLAYLIST_QUERY_PARAM);
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
