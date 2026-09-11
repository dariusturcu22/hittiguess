package org.dariusturcu.backend.websocket;

import org.dariusturcu.backend.model.group.GroupDetailDTO;

// Published by GroupService (a plain Spring application event, no ApplicationEvent
// subclassing needed) whenever a group-side mutation happens that other members need to
// see live. GroupBroadcastListener picks it up and forwards the full, freshly-mapped
// group snapshot to the right STOMP topic: broadcasting the whole state on every change,
// rather than a bespoke delta payload per event type, keeps the client side of this
// simple, a client just replaces its local copy of the group.
public record GroupBroadcastEvent(GroupEventType type, GroupDetailDTO group) {
}
