package org.dariusturcu.backend.websocket;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Naming convention for every per-group STOMP destination this story defines. A client
// subscribes to the broadcast topics below after creating or joining a group over REST;
// subscribing to the membership topic is what registers the session in
// GroupPresenceRegistry (see GroupSessionEventListener).
//
// Story 10 (game session) extends this same convention with a parallel, session-scoped
// set: broadcast topics under "/topic/sessions/{sessionId}/..." (round started, guess
// locked, bet placed, reveal triggered, round scored, next round) and a client-to-server
// action channel under "/app/sessions/{sessionId}/..." (place, guess, bet, skip-betting).
// See SessionDestinations.
public final class GroupDestinations {

    private static final String GROUP_TOPIC_PREFIX = "/topic/groups/";
    private static final String GROUP_APP_PREFIX = "/app/groups/";

    private static final String MEMBERSHIP_SEGMENT = "/membership";
    private static final String SETTINGS_SEGMENT = "/settings";
    // Reserved for story 13, not implemented here: only the naming convention is fixed.
    private static final String CHAT_SEGMENT = "/chat";
    // Reserved for story 12, not implemented here: only the naming convention is fixed.
    private static final String VOICE_SEGMENT = "/voice";
    // Reserved client-to-server channel for admin actions sent over the socket instead of
    // REST. Nothing publishes to it yet: every admin action this batch broadcasts
    // (settings, start session) is still triggered over REST and broadcast from
    // GroupService's own methods, see DECISIONS.md.
    private static final String ADMIN_ACTIONS_SEGMENT = "/admin";

    private static final Pattern MEMBERSHIP_TOPIC_PATTERN =
            Pattern.compile("^" + Pattern.quote(GROUP_TOPIC_PREFIX) + "(?<groupId>\\d+)"
                    + Pattern.quote(MEMBERSHIP_SEGMENT) + "$");

    private GroupDestinations() {
    }

    public static String membershipTopic(Long groupId) {
        return GROUP_TOPIC_PREFIX + groupId + MEMBERSHIP_SEGMENT;
    }

    public static String settingsTopic(Long groupId) {
        return GROUP_TOPIC_PREFIX + groupId + SETTINGS_SEGMENT;
    }

    public static String chatTopic(Long groupId) {
        return GROUP_TOPIC_PREFIX + groupId + CHAT_SEGMENT;
    }

    public static String voiceTopic(Long groupId) {
        return GROUP_TOPIC_PREFIX + groupId + VOICE_SEGMENT;
    }

    public static String adminActionsDestination(Long groupId) {
        return GROUP_APP_PREFIX + groupId + ADMIN_ACTIONS_SEGMENT;
    }

    // Used by GroupSessionEventListener to recognize a client's subscription to its
    // group's membership topic, the signal that registers the session in
    // GroupPresenceRegistry for later disconnect handling.
    public static Optional<Long> groupIdFromMembershipTopic(String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        Matcher matcher = MEMBERSHIP_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(matcher.group("groupId")));
    }
}
