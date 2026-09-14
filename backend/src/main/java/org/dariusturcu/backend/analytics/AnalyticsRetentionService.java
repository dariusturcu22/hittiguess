package org.dariusturcu.backend.analytics;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AnalyticsRetentionService {

    private static final String DELETE_EVENTS_OLDER_THAN_CUTOFF_SQL =
            "DELETE FROM analytics_events WHERE occurred_at < ?";

    private final JdbcTemplate analyticsJdbcTemplate;
    private final int retentionDays;

    public AnalyticsRetentionService(
            @Qualifier("analyticsJdbcTemplate") JdbcTemplate analyticsJdbcTemplate,
            @Value("${analytics.retention.days}") int retentionDays
    ) {
        this.analyticsJdbcTemplate = analyticsJdbcTemplate;
        this.retentionDays = retentionDays;
    }

    public int purgeExpiredEvents() {
        Instant retentionCutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        return analyticsJdbcTemplate.update(DELETE_EVENTS_OLDER_THAN_CUTOFF_SQL, Timestamp.from(retentionCutoff));
    }
}
