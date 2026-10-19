package org.dariusturcu.backend.model.usage;

/**
 * Usage of one provider operation summed over a range of days in api_usage_daily.
 */
public record ApiUsageRecord(
        UsageProvider provider,
        String operation,
        long callCount,
        long quotaUnits,
        long inputTokens,
        long outputTokens) {
}
