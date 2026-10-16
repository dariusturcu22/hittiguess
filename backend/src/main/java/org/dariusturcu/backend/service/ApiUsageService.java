package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.usage.AdminUsageDTO;
import org.dariusturcu.backend.model.usage.ApiUsageRecord;
import org.dariusturcu.backend.model.usage.ProviderUsageDTO;
import org.dariusturcu.backend.model.usage.TokenUsageDTO;
import org.dariusturcu.backend.model.usage.UsageProvider;
import org.dariusturcu.backend.model.usage.YoutubeQuotaUsageDTO;
import org.dariusturcu.backend.repository.ApiUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Builds the admin usage view from the daily usage rows: YouTube quota for the current Pacific
 * quota day, and token usage with estimated cost for the current UTC day and UTC calendar month.
 */
@Service
public class ApiUsageService {

    public static final long YOUTUBE_DEFAULT_DAILY_QUOTA_UNITS = 10_000;

    private final ApiUsageRepository apiUsageRepository;
    private final ApiUsagePricing apiUsagePricing;
    private final long youtubeDailyQuotaUnits;
    private final Clock clock;

    @Autowired
    public ApiUsageService(ApiUsageRepository apiUsageRepository, ApiUsagePricing apiUsagePricing,
                           @Value("${usage.youtube.daily-quota-units:" + YOUTUBE_DEFAULT_DAILY_QUOTA_UNITS + "}")
                           long youtubeDailyQuotaUnits) {
        this(apiUsageRepository, apiUsagePricing, youtubeDailyQuotaUnits, Clock.systemUTC());
    }

    ApiUsageService(ApiUsageRepository apiUsageRepository, ApiUsagePricing apiUsagePricing,
                    long youtubeDailyQuotaUnits, Clock clock) {
        this.apiUsageRepository = apiUsageRepository;
        this.apiUsagePricing = apiUsagePricing;
        this.youtubeDailyQuotaUnits = youtubeDailyQuotaUnits;
        this.clock = clock;
    }

    public AdminUsageDTO usage() {
        Instant now = clock.instant();
        LocalDate utcToday = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate utcMonthStart = YearMonth.from(utcToday).atDay(1);

        List<ApiUsageRecord> todayTotals = apiUsageRepository.totalsByOperation(utcToday, utcToday);
        List<ApiUsageRecord> monthTotals = apiUsageRepository.totalsByOperation(utcMonthStart, utcToday);

        ProviderUsageDTO deepInfra = providerUsage(UsageProvider.DEEPINFRA, todayTotals, monthTotals);
        ProviderUsageDTO openAi = providerUsage(UsageProvider.OPENAI, todayTotals, monthTotals);

        return new AdminUsageDTO(
                youtubeQuota(now),
                deepInfra,
                openAi,
                ApiUsagePricing.roundCost(exactCost(todayTotals, UsageProvider.DEEPINFRA)
                        .add(exactCost(todayTotals, UsageProvider.OPENAI))),
                ApiUsagePricing.roundCost(exactCost(monthTotals, UsageProvider.DEEPINFRA)
                        .add(exactCost(monthTotals, UsageProvider.OPENAI))),
                ApiUsagePricing.PRICES_VERIFIED_ON);
    }

    public long youtubeUnitsRemaining() {
        return youtubeQuota(clock.instant()).unitsRemaining();
    }

    private YoutubeQuotaUsageDTO youtubeQuota(Instant now) {
        LocalDate quotaDay = UsageQuotaDay.usageDate(UsageProvider.YOUTUBE, now);
        long unitsUsedToday = apiUsageRepository.quotaUnitsOn(UsageProvider.YOUTUBE, quotaDay);
        long unitsRemaining = Math.max(0, youtubeDailyQuotaUnits - unitsUsedToday);
        return new YoutubeQuotaUsageDTO(unitsUsedToday, youtubeDailyQuotaUnits, unitsRemaining, quotaDay,
                UsageQuotaDay.nextYoutubeQuotaReset(now));
    }

    private ProviderUsageDTO providerUsage(UsageProvider provider, List<ApiUsageRecord> todayTotals,
                                           List<ApiUsageRecord> monthTotals) {
        return new ProviderUsageDTO(provider, tokenUsage(provider, todayTotals), tokenUsage(provider, monthTotals));
    }

    private TokenUsageDTO tokenUsage(UsageProvider provider, List<ApiUsageRecord> totals) {
        List<ApiUsageRecord> providerTotals = totals.stream().filter(usage -> usage.provider() == provider).toList();
        return new TokenUsageDTO(
                providerTotals.stream().mapToLong(ApiUsageRecord::callCount).sum(),
                providerTotals.stream().mapToLong(ApiUsageRecord::inputTokens).sum(),
                providerTotals.stream().mapToLong(ApiUsageRecord::outputTokens).sum(),
                ApiUsagePricing.roundCost(exactCost(totals, provider)));
    }

    private BigDecimal exactCost(List<ApiUsageRecord> totals, UsageProvider provider) {
        return totals.stream()
                .filter(usage -> usage.provider() == provider)
                .map(apiUsagePricing::estimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
