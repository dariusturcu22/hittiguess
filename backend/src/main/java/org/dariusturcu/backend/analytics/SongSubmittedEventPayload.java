package org.dariusturcu.backend.analytics;

public record SongSubmittedEventPayload(long userId, long songId) {
}
