import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import CatalogBacklogPage from "./page";

let backlogQueryOptions: unknown;

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
      isLoading: false,
      isError: false,
    };
  },
  useEnqueue: () => ({ mutate: vi.fn(), isPending: false }),
}));

describe("CatalogBacklogPage live status", () => {
  it("polls the backlog status while the sweep drains", () => {
    render(<CatalogBacklogPage />);

    expect(backlogQueryOptions).toMatchObject({
      query: { refetchInterval: expect.any(Number) },
    });
    expect(screen.getByText("12")).toBeVisible();
  });
});
