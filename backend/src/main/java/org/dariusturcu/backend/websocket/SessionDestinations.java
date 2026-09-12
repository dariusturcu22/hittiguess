package org.dariusturcu.backend.websocket;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The session-scoped parallel to GroupDestinations, following the convention that
// story 11 reserved: broadcast topics under "/topic/sessions/{sessionId}/..." and a
// client-to-server action channel under "/app/sessions/{sessionId}/...". A client
// subscribes to a session's round topic right after the group's GAME_SESSION_STARTED
// event fires, the same way it subscribes to the group's membership topic on
// create/join; that subscription is what registers presence in SessionPresenceRegistry.
public final class SessionDestinations {

    private static final String SESSION_TOPIC_PREFIX = "/topic/sessions/";
    private static final String SESSION_APP_PREFIX = "/app/sessions/";

    private static final String ROUND_SEGMENT = "/round";
    private static final String ENDED_SEGMENT = "/ended";
    private static final String PLACE_SEGMENT = "/place";
    private static final String GUESS_SEGMENT = "/guess";
    private static final String BET_SEGMENT = "/bet";
    private static final String SKIP_BETTING_SEGMENT = "/skip-betting";

    private static final Pattern ROUND_TOPIC_PATTERN = Pattern.compile(
            "^" + Pattern.quote(SESSION_TOPIC_PREFIX) + "(?<sessionId>\\d+)" + Pattern.quote(ROUND_SEGMENT) + "$");

    private SessionDestinations() {
    }

    // Round started, guess locked, bet placed, reveal triggered, round scored, and next
    // round all broadcast here; SessionEventType distinguishes them within the payload.
    public static String roundTopic(Long sessionId) {
        return SESSION_TOPIC_PREFIX + sessionId + ROUND_SEGMENT;
    }

    // The session's final results (or a plain abandonment notice) broadcasts here,
    // separately from the round topic, mirroring GroupDestinations' settings/membership
    // split: a client that only cares about the outcome doesn't need to have followed
    // every round event to receive it.
    public static String endedTopic(Long sessionId) {
        return SESSION_TOPIC_PREFIX + sessionId + ENDED_SEGMENT;
    }

    public static String placeDestination(Long sessionId) {
        return SESSION_APP_PREFIX + sessionId + PLACE_SEGMENT;
    }

    public static String guessDestination(Long sessionId) {
        return SESSION_APP_PREFIX + sessionId + GUESS_SEGMENT;
    }

    public static String betDestination(Long sessionId) {
        return SESSION_APP_PREFIX + sessionId + BET_SEGMENT;
    }

    public static String skipBettingDestination(Long sessionId) {
        return SESSION_APP_PREFIX + sessionId + SKIP_BETTING_SEGMENT;
    }

    public static Optional<Long> sessionIdFromRoundTopic(String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Matcher matcher = ROUND_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(matcher.group("sessionId")));
    }
}
