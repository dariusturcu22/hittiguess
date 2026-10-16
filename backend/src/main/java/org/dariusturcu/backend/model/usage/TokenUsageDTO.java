package org.dariusturcu.backend.model.usage;

import java.math.BigDecimal;

/**
 * Calls and tokens for one provider over a period, with the cost estimated from the current
 * price constants.
 */
public record TokenUsageDTO(
        long calls,
        long inputTokens,
        long outputTokens,
        BigDecimal estimatedCostUsd) {
}
