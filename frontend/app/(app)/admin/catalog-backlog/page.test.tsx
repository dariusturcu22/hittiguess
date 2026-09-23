import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import CatalogBacklogPage from "./page";

let backlogQueryOptions: unknown;
let backlogStatusLoading = false;
const enqueueMutate = vi.fn();
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
      },
      isLoading: backlogStatusLoading,
      isError: false,
      refetch: vi.fn(),
    };
  },
  useEnqueue: () => ({ mutate: enqueueMutate, isPending: false }),
}));

describe("CatalogBacklogPage live status", () => {
  beforeEach(() => {
    backlogStatusLoading = false;
    enqueueMutate.mockReset();
    toastMocks.success.mockReset();
    toastMocks.error.mockReset();
  });

  it("polls the backlog status while the sweep drains", () => {
    render(<CatalogBacklogPage />);

    expect(backlogQueryOptions).toMatchObject({
      query: { refetchInterval: expect.any(Number) },
    });
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
});
