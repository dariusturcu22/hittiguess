package org.dariusturcu.backend.websocket;

// The kinds of group-side state change GroupService publishes as a GroupBroadcastEvent.
// SETTINGS_CHANGED routes to the settings topic, every other type routes to the
// membership topic, see GroupBroadcastListener.
public enum GroupEventType {
    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_CONNECTION_CHANGED,
    ADMIN_CHANGED,
    SETTINGS_CHANGED,
    GAME_SESSION_STARTED
}
