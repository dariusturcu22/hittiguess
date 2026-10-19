import { describe, expect, it } from "vitest";

import { formatUpdatedAgo, isAdminQueryKey, livePollingQueryOptions } from "./live-admin-data";

const NOW_MILLISECONDS = Date.parse("2026-10-16T12:00:00Z");
const SECOND_MILLISECONDS = 1_000;
const MINUTE_MILLISECONDS = 60 * SECOND_MILLISECONDS;
const HOUR_MILLISECONDS = 60 * MINUTE_MILLISECONDS;
const FIVE_SECONDS_MILLISECONDS = 5 * SECOND_MILLISECONDS;

describe("formatUpdatedAgo", () => {
  it("shows nothing before the first response", () => {
    expect(formatUpdatedAgo(undefined, NOW_MILLISECONDS)).toBeNull();
    expect(formatUpdatedAgo(0, NOW_MILLISECONDS)).toBeNull();
  });

  it("says just now right after a refresh, also for a clock slightly behind", () => {
    expect(formatUpdatedAgo(NOW_MILLISECONDS, NOW_MILLISECONDS)).toBe("Updated just now");
    expect(formatUpdatedAgo(NOW_MILLISECONDS + SECOND_MILLISECONDS, NOW_MILLISECONDS)).toBe("Updated just now");
  });

  it("counts seconds, then minutes, then hours", () => {
    expect(formatUpdatedAgo(NOW_MILLISECONDS - 12 * SECOND_MILLISECONDS, NOW_MILLISECONDS)).toBe("Updated 12s ago");
    expect(formatUpdatedAgo(NOW_MILLISECONDS - 3 * MINUTE_MILLISECONDS, NOW_MILLISECONDS)).toBe("Updated 3 min ago");
    expect(formatUpdatedAgo(NOW_MILLISECONDS - 2 * HOUR_MILLISECONDS, NOW_MILLISECONDS)).toBe("Updated 2 h ago");
  });
});

describe("livePollingQueryOptions", () => {
  it("polls only while visible, refetches on focus, and keeps the previous data", () => {
    const options = livePollingQueryOptions(FIVE_SECONDS_MILLISECONDS);

    expect(options.refetchInterval).toBe(FIVE_SECONDS_MILLISECONDS);
    expect(options.refetchIntervalInBackground).toBe(false);
    expect(options.refetchOnWindowFocus).toBe(true);
    expect(options.staleTime).toBe(0);
    expect(typeof options.placeholderData).toBe("function");
  });
});

describe("isAdminQueryKey", () => {
  it("matches every admin endpoint and nothing else", () => {
    expect(isAdminQueryKey(["/api/admin/queues"])).toBe(true);
    expect(isAdminQueryKey(["/api/admin/runs", { page: 0 }])).toBe(true);
    expect(isAdminQueryKey(["/api/admin/batches"])).toBe(true);
    expect(isAdminQueryKey(["/api/admin/dashboard"])).toBe(true);
    expect(isAdminQueryKey(["/api/admin/songs/12"])).toBe(true);
    expect(isAdminQueryKey(["/api/playlists"])).toBe(false);
    expect(isAdminQueryKey([42])).toBe(false);
  });
});
