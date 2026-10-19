package org.dariusturcu.backend.analytics;

public record RateLimitExceededEventPayload(long userId, String endpoint) {
}
