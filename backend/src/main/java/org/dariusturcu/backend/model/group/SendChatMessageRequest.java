package org.dariusturcu.backend.model.group;

// The client-to-server chat send payload. Length is validated in ChatService against the
// content column's own limit rather than as a bean-validation annotation, since a STOMP
// message payload does not pass through the REST @Valid path.
public record SendChatMessageRequest(String content) {
}
