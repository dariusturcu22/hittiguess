package org.dariusturcu.backend.service;

import org.dariusturcu.backend.model.usage.UsageProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The calendar day a usage row belongs to. YouTube's daily quota resets at midnight Pacific
 * time, so YouTube usage is counted by the Pacific date; every other provider uses the UTC date.
 * The AI service applies the same rule when it writes the rows.
 */
public final class UsageQuotaDay {

    public static final ZoneId YOUTUBE_QUOTA_ZONE = ZoneId.of("America/Los_Angeles");

    private UsageQuotaDay() {
    }

    public static ZoneId zoneFor(UsageProvider provider) {
        return provider == UsageProvider.YOUTUBE ? YOUTUBE_QUOTA_ZONE : ZoneOffset.UTC;
    }

    public static LocalDate usageDate(UsageProvider provider, Instant moment) {
        return LocalDate.ofInstant(moment, zoneFor(provider));
    }

    public static Instant nextYoutubeQuotaReset(Instant moment) {
        return usageDate(UsageProvider.YOUTUBE, moment).plusDays(1).atStartOfDay(YOUTUBE_QUOTA_ZONE).toInstant();
    }
}
