import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import CatalogBacklogPage from "./page";

let backlogQueryOptions: unknown;
let backlogStatusLoading = false;
let backlogExtras: Record<string, unknown> = {};
const enqueueMutate = vi.fn();
const drainMutate = vi.fn();
const toastMocks = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

vi.mock("sonner", () => ({
  toast: toastMocks,
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={typeof href === "string" ? href : "#"}>{children}</a>
  ),
}));

vi.mock("@/hooks/generated/admin-catalog-seeding/admin-catalog-seeding", () => ({
  useBacklogStatus: (options?: unknown) => {
    backlogQueryOptions = options;
    return {
      data: {
        pendingCount: 12,
        processedTodayCount: 3,
        dailyDrainQuota: 200,
        quotaRemainingToday: 197,
        queueItems: [],
        recentRechecks: [],
        draining: false,
        ...backlogExtras,
      },
      isLoading: backlogStatusLoading,
      isError: false,
      refetch: vi.fn(),
    };
  },
  useEnqueue: () => ({ mutate: enqueueMutate, isPending: false }),
  useDrainNow: () => ({ mutate: drainMutate, isPending: false }),
}));

describe("CatalogBacklogPage live status", () => {
  beforeEach(() => {
    backlogStatusLoading = false;
    backlogExtras = {};
    enqueueMutate.mockReset();
    drainMutate.mockReset();
    toastMocks.success.mockReset();
    toastMocks.error.mockReset();
  });

  it("polls the backlog status while the sweep drains", () => {
    render(<CatalogBacklogPage />);

    const refetchInterval = (backlogQueryOptions as { query: { refetchInterval: (query: unknown) => number } }).query.refetchInterval;
    expect(refetchInterval({ state: { data: { draining: false } } })).toBeGreaterThan(
      refetchInterval({ state: { data: { draining: true } } }),
    );
    expect(screen.getByText("12")).toBeVisible();
  });

  it("shows a skeleton in the stat tiles while the status is loading", () => {
    backlogStatusLoading = true;
    render(<CatalogBacklogPage />);

    expect(screen.queryByText("12")).toBeNull();
    expect(document.querySelectorAll('[data-slot="skeleton"]').length).toBeGreaterThan(0);
  });

  it("toasts on a successful enqueue", async () => {
    enqueueMutate.mockImplementation((_args, options) => options?.onSuccess?.());
    render(<CatalogBacklogPage />);

    fireEvent.change(screen.getByLabelText("YouTube playlist link, or one video ID per line"), {
      target: { value: "dQw4w9WgXcQ" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add to backlog" }));

    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Added to backlog"));
  });

  it("toasts an error when enqueue fails", async () => {
    enqueueMutate.mockImplementation((_args, options) => options?.onError?.());
    render(<CatalogBacklogPage />);

    fireEvent.change(screen.getByLabelText("YouTube playlist link, or one video ID per line"), {
      target: { value: "dQw4w9WgXcQ" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add to backlog" }));

    await waitFor(() =>
      expect(toastMocks.error).toHaveBeenCalledWith("Enqueue failed. Check the link or IDs and try again."),
    );
  });

  it("lists provisional answers with their provisional and patient years, flagging a corrected year", () => {
    backlogExtras = {
      recentRechecks: [
        { youtubeId: "correctedId", origin: "FAST_TIER_RECHECK", status: "DONE", title: "Corrected Song", artists: "Band", provisionalYear: 1998, patientYear: 1999, enqueuedAt: "2026-09-24T10:00:00Z" },
        { youtubeId: "confirmedId", origin: "FAST_TIER_RECHECK", status: "DONE", title: "Confirmed Song", artists: "Band", provisionalYear: 2004, patientYear: 2004, enqueuedAt: "2026-09-24T10:01:00Z" },
        { youtubeId: "waitingId01", origin: "USER_ADD_RECHECK", status: "PENDING", title: "Waiting Song", artists: "Band", provisionalYear: 2010, enqueuedAt: "2026-09-24T10:02:00Z" },
      ],
    };
    render(<CatalogBacklogPage />);

    const rechecks = screen.getByRole("region", { name: "Provisional answers being rechecked" });
    expect(rechecks).toHaveTextContent("Corrected Song");
    expect(rechecks).toHaveTextContent("1 year corrected");
    expect(rechecks.querySelectorAll("[data-year-changed]")).toHaveLength(1);
    expect(rechecks).toHaveTextContent("Queued");
    expect(rechecks).toHaveTextContent("Added by hand");
  });

  it("starts a drain on demand", async () => {
    drainMutate.mockImplementation((_variables, options) => options?.onSuccess?.());
    render(<CatalogBacklogPage />);

    fireEvent.click(screen.getByRole("button", { name: "Drain now" }));

    expect(drainMutate).toHaveBeenCalledOnce();
    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Draining the backlog now"));
  });

  it("disables the drain button while a drain is running", () => {
    backlogExtras = { draining: true };
    render(<CatalogBacklogPage />);

    expect(screen.getByRole("button", { name: "Draining..." })).toBeDisabled();
  });
});
