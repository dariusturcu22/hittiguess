package org.dariusturcu.backend.model.usage;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The admin usage view: YouTube quota for the current Pacific quota day, DeepInfra and OpenAI
 * usage with estimated cost, the combined estimated cost, and the date the prices were read.
 */
public record AdminUsageDTO(
        YoutubeQuotaUsageDTO youtube,
        ProviderUsageDTO deepInfra,
        ProviderUsageDTO openAi,
        BigDecimal estimatedCostTodayUsd,
        BigDecimal estimatedCostMonthUsd,
        LocalDate pricesVerifiedOn) {
}
