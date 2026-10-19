package org.dariusturcu.backend.analytics;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

// Writes usage/event data to the separate analytics store (story 33). Nothing calls
// recordEvent yet; story 34 instruments the actual event-producing call sites.
@Service
public class AnalyticsEventRecorder {

    private static final String INSERT_EVENT_SQL =
            "INSERT INTO analytics_events (event_type, payload) VALUES (?, CAST(? AS jsonb))";

    private final JdbcTemplate analyticsJdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalyticsEventRecorder(
            @Qualifier("analyticsJdbcTemplate") JdbcTemplate analyticsJdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.analyticsJdbcTemplate = analyticsJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordEvent(AnalyticsEventType eventType, Object payload) {
        String payloadJson = objectMapper.writeValueAsString(payload);
        analyticsJdbcTemplate.update(INSERT_EVENT_SQL, eventType.name(), payloadJson);
    }
}
