package org.dariusturcu.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Abuse-visibility signals whose real event store (story 34) does not exist yet.
 * Each write is a structured log line marked as a story-34 stub, not a persisted
 * event; story 34 replaces these with writes into its analytics event pipeline.
 */
@Component
public class AbuseVisibilityEvents {

    private static final Logger logger = LoggerFactory.getLogger(AbuseVisibilityEvents.class);

    private static final String REPORT_SUBMITTED_EVENT = "report_submitted";

    // TODO: story 34, replace this log line with a real abuse-visibility event write.
    public void recordReportSubmitted(long reportingUserId, long songId) {
        logger.info("abuse_event={} reporting_user_id={} song_id={}",
                REPORT_SUBMITTED_EVENT, reportingUserId, songId);
    }
}
