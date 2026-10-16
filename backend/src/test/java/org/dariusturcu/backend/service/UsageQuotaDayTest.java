package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.usage.UsageProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UsageQuotaDayTest {

    @Test
    void youtubeUsesThePacificDateWhileOtherProvidersUseTheUtcDate() {
        Instant earlyUtcMorning = Instant.parse("2026-10-16T05:00:00Z");

        assertThat(UsageQuotaDay.usageDate(UsageProvider.YOUTUBE, earlyUtcMorning)).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(UsageQuotaDay.usageDate(UsageProvider.DEEPINFRA, earlyUtcMorning)).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(UsageQuotaDay.usageDate(UsageProvider.OPENAI, earlyUtcMorning)).isEqualTo(LocalDate.of(2026, 10, 16));
    }

    @Test
    void theResetIsTheNextPacificMidnightInDaylightTime() {
        assertThat(UsageQuotaDay.nextYoutubeQuotaReset(Instant.parse("2026-10-16T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-10-17T07:00:00Z"));
    }

    @Test
    void theResetFollowsTheSpringDaylightSavingTransition() {
        // 2026-03-08 starts daylight time at 02:00, after that day's midnight.
        assertThat(UsageQuotaDay.nextYoutubeQuotaReset(Instant.parse("2026-03-07T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-03-08T08:00:00Z"));
        assertThat(UsageQuotaDay.nextYoutubeQuotaReset(Instant.parse("2026-03-08T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-03-09T07:00:00Z"));
    }

    @Test
    void theResetFollowsTheAutumnDaylightSavingTransition() {
        // 2026-11-01 returns to standard time at 02:00, after that day's midnight.
        assertThat(UsageQuotaDay.nextYoutubeQuotaReset(Instant.parse("2026-10-31T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-01T07:00:00Z"));
        assertThat(UsageQuotaDay.nextYoutubeQuotaReset(Instant.parse("2026-11-01T12:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-02T08:00:00Z"));
    }

    @Test
    void anInstantExactlyAtPacificMidnightBelongsToTheNewQuotaDay() {
        Instant pacificMidnight = Instant.parse("2026-10-17T07:00:00Z");

        assertThat(UsageQuotaDay.usageDate(UsageProvider.YOUTUBE, pacificMidnight)).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(UsageQuotaDay.nextYoutubeQuotaReset(pacificMidnight)).isEqualTo(Instant.parse("2026-10-18T07:00:00Z"));
    }
}
