package org.dariusturcu.backend.research;

import java.util.UUID;

public record ResearchQueueEntry(UUID eventId, String eventType, String payload) {}
