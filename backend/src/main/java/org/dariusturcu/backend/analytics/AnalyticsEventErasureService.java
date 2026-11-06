package org.dariusturcu.backend.analytics;

import org.dariusturcu.backend.history.AccountHistoryDeleted;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

// Erases every analytics event that names a deleted account, so a user id does not outlive
// the account for the whole raw-event retention window. Game events list several players,
// and are erased as a whole when any one of them deletes their account.
@Service
public class AnalyticsEventErasureService {

    private static final String DELETE_EVENTS_NAMING_USER_SQL = """
            DELETE FROM analytics_events
            WHERE payload ->> 'userId' = ?
               OR payload ->> 'reportingUserId' = ?
               OR payload ->> 'winningPlayerUserId' = ?
               OR payload -> 'playerUserIds' @> to_jsonb(CAST(? AS bigint))
               OR jsonb_exists(payload -> 'cardsWonByPlayerUserId', ?)
               OR jsonb_exists(payload -> 'finalScoreByPlayerUserId', ?)
            """;

    private final JdbcTemplate analyticsJdbcTemplate;

    public AnalyticsEventErasureService(@Qualifier("analyticsJdbcTemplate") JdbcTemplate analyticsJdbcTemplate) {
        this.analyticsJdbcTemplate = analyticsJdbcTemplate;
    }

    @EventListener
    public int deleted(AccountHistoryDeleted deletion) {
        String userIdText = Long.toString(deletion.userId());
        return analyticsJdbcTemplate.update(DELETE_EVENTS_NAMING_USER_SQL,
                userIdText, userIdText, userIdText, userIdText, userIdText, userIdText);
    }
}
