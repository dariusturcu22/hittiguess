package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.usage.AdminUsageDTO;
import org.dariusturcu.backend.model.usage.ApiUsageRecord;
import org.dariusturcu.backend.model.usage.TokenUsageDTO;
import org.dariusturcu.backend.model.usage.UsageProvider;
import org.dariusturcu.backend.model.usage.YoutubeQuotaUsageDTO;
import org.dariusturcu.backend.repository.ApiUsageRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiUsageServiceTest {

    private static final long DAILY_LIMIT = 10_000;
    private static final long UNITS_USED = 2_500;
    private static final long NO_QUOTA_UNITS = 0;
    private static final long ONE_MILLION_TOKENS = 1_000_000;

    private final ApiUsageRepository repository = mock(ApiUsageRepository.class);

    private ApiUsageService serviceAt(String instant) {
        return new ApiUsageService(repository, new ApiUsagePricing(), DAILY_LIMIT,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private static ApiUsageRecord deepInfraChat(long calls, long inputTokens, long outputTokens) {
        return new ApiUsageRecord(UsageProvider.DEEPINFRA, ApiUsagePricing.CHAT_COMPLETION_OPERATION, calls,
                NO_QUOTA_UNITS, inputTokens, outputTokens);
    }

    private static ApiUsageRecord openAiEmbeddings(long calls, long inputTokens) {
        return new ApiUsageRecord(UsageProvider.OPENAI, ApiUsagePricing.EMBEDDINGS_OPERATION, calls,
                NO_QUOTA_UNITS, inputTokens, 0);
    }

    @Test
    void reportsYoutubeQuotaForThePacificDayWithTheNextReset() {
        LocalDate pacificDay = LocalDate.of(2026, 10, 15);
        when(repository.quotaUnitsOn(UsageProvider.YOUTUBE, pacificDay)).thenReturn(UNITS_USED);

        YoutubeQuotaUsageDTO youtube = serviceAt("2026-10-16T05:00:00Z").usage().youtube();

        assertThat(youtube).isEqualTo(new YoutubeQuotaUsageDTO(UNITS_USED, DAILY_LIMIT, DAILY_LIMIT - UNITS_USED,
                pacificDay, Instant.parse("2026-10-16T07:00:00Z")));
    }

    @Test
    void remainingUnitsNeverGoBelowZero() {
        when(repository.quotaUnitsOn(UsageProvider.YOUTUBE, LocalDate.of(2026, 10, 16))).thenReturn(DAILY_LIMIT + 1);

        assertThat(serviceAt("2026-10-16T12:00:00Z").usage().youtube().unitsRemaining()).isZero();
    }

    @Test
    void sumsTodayAndTheUtcMonthPerProviderWithEstimatedCost() {
        LocalDate today = LocalDate.of(2026, 10, 16);
        LocalDate monthStart = LocalDate.of(2026, 10, 1);
        when(repository.totalsByOperation(today, today)).thenReturn(List.of(
                deepInfraChat(3, ONE_MILLION_TOKENS, ONE_MILLION_TOKENS)));
        when(repository.totalsByOperation(monthStart, today)).thenReturn(List.of(
                deepInfraChat(10, 2 * ONE_MILLION_TOKENS, ONE_MILLION_TOKENS),
                openAiEmbeddings(40, 5 * ONE_MILLION_TOKENS)));

        AdminUsageDTO usage = serviceAt("2026-10-16T12:00:00Z").usage();

        assertThat(usage.deepInfra().today()).isEqualTo(new TokenUsageDTO(3, ONE_MILLION_TOKENS, ONE_MILLION_TOKENS,
                new BigDecimal("0.270000")));
        assertThat(usage.deepInfra().month()).isEqualTo(new TokenUsageDTO(10, 2 * ONE_MILLION_TOKENS,
                ONE_MILLION_TOKENS, new BigDecimal("0.360000")));
        assertThat(usage.openAi().today()).isEqualTo(new TokenUsageDTO(0, 0, 0, new BigDecimal("0.000000")));
        assertThat(usage.openAi().month()).isEqualTo(new TokenUsageDTO(40, 5 * ONE_MILLION_TOKENS, 0,
                new BigDecimal("0.100000")));
        assertThat(usage.estimatedCostTodayUsd()).isEqualTo(new BigDecimal("0.270000"));
        assertThat(usage.estimatedCostMonthUsd()).isEqualTo(new BigDecimal("0.460000"));
        assertThat(usage.pricesVerifiedOn()).isEqualTo(ApiUsagePricing.PRICES_VERIFIED_ON);
    }

    @Test
    void theMonthStartsOverOnTheFirstUtcDayWhileYoutubeIsStillOnThePreviousPacificDay() {
        LocalDate firstOfNovember = LocalDate.of(2026, 11, 1);
        when(repository.totalsByOperation(firstOfNovember, firstOfNovember)).thenReturn(List.of(
                deepInfraChat(1, ONE_MILLION_TOKENS, 0)));
        when(repository.quotaUnitsOn(UsageProvider.YOUTUBE, LocalDate.of(2026, 10, 31))).thenReturn(UNITS_USED);

        AdminUsageDTO usage = serviceAt("2026-11-01T03:00:00Z").usage();

        assertThat(usage.deepInfra().month().calls()).isEqualTo(1);
        assertThat(usage.estimatedCostMonthUsd()).isEqualTo(new BigDecimal("0.090000"));
        assertThat(usage.youtube().quotaDay()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(usage.youtube().unitsUsedToday()).isEqualTo(UNITS_USED);
    }

    @Test
    void costsAreRoundedOnlyAfterSummingAcrossOperations() {
        LocalDate today = LocalDate.of(2026, 10, 16);
        when(repository.totalsByOperation(today, today)).thenReturn(List.of(
                new ApiUsageRecord(UsageProvider.OPENAI, ApiUsagePricing.CHAT_COMPLETION_OPERATION, 1, 0, 0, 1),
                new ApiUsageRecord(UsageProvider.OPENAI, ApiUsagePricing.EMBEDDINGS_OPERATION, 1, 0, 30, 0)));

        AdminUsageDTO usage = serviceAt("2026-10-16T12:00:00Z").usage();

        // 0.0000004 for one output token plus 0.0000006 for thirty embedding tokens.
        assertThat(usage.openAi().today().estimatedCostUsd()).isEqualTo(new BigDecimal("0.000001"));
    }
}
