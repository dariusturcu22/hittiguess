package org.dariusturcu.backend.model.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The client-to-server chat send payload. A STOMP payload does not pass through the REST
// @Valid path, so ChatService enforces the same limits; the annotations keep the contract
// visible on the record and in the generated API description.
public record SendChatMessageRequest(
        @NotBlank(message = "Chat message content cannot be blank")
        @Size(max = ChatMessage.MAX_CONTENT_LENGTH, message = "Chat message must be at most 500 characters")
        String content) {
}
