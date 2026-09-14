package org.dariusturcu.backend.analytics;

public record ReportSubmittedEventPayload(long reportingUserId, long songId) {
}
