import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import AdminDashboardPage from "./page";
import {
  DASHBOARD_REFRESH_INTERVAL_MILLISECONDS,
  formatCostUsd,
  formatResetCountdown,
  formatTokenCount,
  quotaProgressPercent,
} from "./dashboard-presentation";

const dashboardHook = vi.hoisted(() => ({
  options: [] as unknown[],
  result: {} as Record<string, unknown>,
  refetch: vi.fn(),
}));

vi.mock("@/hooks/generated/admin-dashboard/admin-dashboard", () => ({
  useDashboard: (options: unknown) => {
    dashboardHook.options.push(options);
    return { ...dashboardHook.result, refetch: dashboardHook.refetch };
  },
}));

const NOW = "2026-10-16T12:00:00Z";
const RESET_IN_SIX_HOURS_TWELVE_MINUTES = "2026-10-16T18:12:30Z";
const HALF_QUOTA_UNITS = 3_418;
const DAILY_LIMIT = 10_000;
const EXPECTED_PERCENT = 34.18;
const OVER_QUOTA_UNITS = 12_000;
const FULL_PERCENT = 100;

function dashboardWith(overrides: { unitsUsedToday?: number; resetsAt?: string } = {}) {
  return {
    songs: { verified: 4812, needsReview: 326, inQueue: 3, processing: 1, error: 5, total: 5146 },
    queue: { waiting: 1835, inProgress: 7 },
    usage: {
      youtube: {
        unitsUsedToday: overrides.unitsUsedToday ?? HALF_QUOTA_UNITS,
        dailyLimit: DAILY_LIMIT,
        unitsRemaining: DAILY_LIMIT - (overrides.unitsUsedToday ?? HALF_QUOTA_UNITS),
        resetsAt: overrides.resetsAt ?? RESET_IN_SIX_HOURS_TWELVE_MINUTES,
      },
      deepInfra: {
        provider: "DEEPINFRA",
        today: { calls: 412, inputTokens: 1_500_000, outputTokens: 340_000, estimatedCostUsd: 0.41 },
        month: { calls: 4000, inputTokens: 0, outputTokens: 0, estimatedCostUsd: 6.92 },
      },
      openAi: {
        provider: "OPENAI",
        today: { calls: 388, inputTokens: 96_400, outputTokens: 0, estimatedCostUsd: 0 },
        month: { calls: 4000, inputTokens: 0, outputTokens: 0, estimatedCostUsd: 0 },
      },
      estimatedCostTodayUsd: 0.41,
      estimatedCostMonthUsd: 6.92,
    },
  };
}

describe("AdminDashboardPage", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW));
    dashboardHook.options = [];
    dashboardHook.refetch.mockReset();
    dashboardHook.result = { data: dashboardWith(), isLoading: false, isError: false };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows the catalog counts and the queue split", () => {
    render(<AdminDashboardPage />);

    expect(screen.getByText("4,812")).toBeInTheDocument();
    expect(screen.getByText("326")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("1,842")).toBeInTheDocument();
    expect(screen.getByText("7 in progress, 1,835 waiting")).toBeInTheDocument();
  });

  it("shows the YouTube quota with the reset countdown", () => {
    render(<AdminDashboardPage />);

    expect(screen.getByTestId("youtube-units-used")).toHaveTextContent("3,418");
    expect(screen.getByText("/ 10,000 units today")).toBeInTheDocument();
    expect(screen.getByText("resets in 6 h 12 min (midnight Pacific)")).toBeInTheDocument();
  });

  it("sizes the quota bar from the used share and clamps it at the full width", () => {
    const { unmount } = render(<AdminDashboardPage />);
    expect(screen.getByTestId("youtube-quota-bar")).toHaveStyle({ width: `${EXPECTED_PERCENT}%` });
    unmount();

    dashboardHook.result = { data: dashboardWith({ unitsUsedToday: OVER_QUOTA_UNITS }), isLoading: false, isError: false };
    render(<AdminDashboardPage />);
    expect(screen.getByTestId("youtube-quota-bar")).toHaveStyle({ width: `${FULL_PERCENT}%` });
  });

  it("shows AI calls, tokens, and the estimated cost for today and the month", () => {
    render(<AdminDashboardPage />);

    expect(screen.getByText("412 calls")).toBeInTheDocument();
    expect(screen.getByText("1.84 M tokens today")).toBeInTheDocument();
    expect(screen.getByText("388 calls")).toBeInTheDocument();
    expect(screen.getByText("96 K tokens today")).toBeInTheDocument();
    expect(screen.getByTestId("estimated-cost-today")).toHaveTextContent("$0.41");
    expect(screen.getByText("$6.92 this month")).toBeInTheDocument();
    expect(screen.getByText("no cap, tracked with estimated cost")).toBeInTheDocument();
  });

  it("does not render the pieces that wait for later pipeline work", () => {
    render(<AdminDashboardPage />);

    expect(screen.queryByText(/reserve for users/)).not.toBeInTheDocument();
    expect(screen.queryByText("Queues")).not.toBeInTheDocument();
  });

  it("refreshes every 10 seconds while visible, on focus, and keeps the previous data while refetching", () => {
    render(<AdminDashboardPage />);

    const [firstOptions] = dashboardHook.options;
    const options = firstOptions as {
      query: {
        refetchInterval: number;
        refetchIntervalInBackground: boolean;
        refetchOnWindowFocus: boolean;
        placeholderData: unknown;
      };
    };
    expect(options.query.refetchInterval).toBe(DASHBOARD_REFRESH_INTERVAL_MILLISECONDS);
    expect(DASHBOARD_REFRESH_INTERVAL_MILLISECONDS).toBe(10_000);
    expect(options.query.refetchIntervalInBackground).toBe(false);
    expect(options.query.refetchOnWindowFocus).toBe(true);
    expect(typeof options.query.placeholderData).toBe("function");
  });

  it("shows how long ago the numbers were updated", () => {
    const TEN_SECONDS_MILLISECONDS = 10_000;
    dashboardHook.result = {
      data: dashboardWith(),
      isLoading: false,
      isError: false,
      dataUpdatedAt: Date.parse(NOW) - TEN_SECONDS_MILLISECONDS,
    };
    render(<AdminDashboardPage />);

    expect(screen.getByTestId("updated-ago")).toHaveTextContent("Updated 10s ago");
  });

  it("shows a loading skeleton before the first response", () => {
    dashboardHook.result = { data: undefined, isLoading: true, isError: false };
    render(<AdminDashboardPage />);

    expect(screen.getByTestId("dashboard-skeleton")).toBeInTheDocument();
  });

  it("shows an error with a Retry button that refetches", () => {
    dashboardHook.result = { data: undefined, isLoading: false, isError: true };
    render(<AdminDashboardPage />);

    expect(screen.getByText("Couldn't load the dashboard.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(dashboardHook.refetch).toHaveBeenCalledTimes(1);
  });
});

describe("dashboard presentation helpers", () => {
  const FIVE_CENTS = 0.05;
  const NINE_NINETY_NINE = 9.994;
  const LARGE_COST = 1234.56;
  const SMALL_TOKENS = 840;
  const NOW_MILLISECONDS = Date.parse(NOW);

  it("formats cost with two decimals below ten dollars and whole dollars above", () => {
    expect(formatCostUsd(FIVE_CENTS)).toBe("$0.05");
    expect(formatCostUsd(NINE_NINETY_NINE)).toBe("$9.99");
    expect(formatCostUsd(LARGE_COST)).toBe("$1,235");
  });

  it("formats tokens as plain, K, or M", () => {
    expect(formatTokenCount(SMALL_TOKENS)).toBe("840");
    expect(formatTokenCount(96_400)).toBe("96 K");
    expect(formatTokenCount(1_840_000)).toBe("1.84 M");
  });

  it("counts down to the reset and never goes negative", () => {
    expect(formatResetCountdown(RESET_IN_SIX_HOURS_TWELVE_MINUTES, NOW_MILLISECONDS)).toBe(
      "resets in 6 h 12 min (midnight Pacific)",
    );
    expect(formatResetCountdown("2026-10-16T11:00:00Z", NOW_MILLISECONDS)).toBe(
      "resets in 0 h 0 min (midnight Pacific)",
    );
    expect(formatResetCountdown(undefined, NOW_MILLISECONDS)).toBeNull();
  });

  it("returns zero progress when the limit is zero", () => {
    expect(quotaProgressPercent(5, 0)).toBe(0);
  });
});
