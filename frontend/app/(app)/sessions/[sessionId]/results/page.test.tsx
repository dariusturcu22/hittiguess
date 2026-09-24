import { act, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import SessionResultsPage, { csvCell } from "./page";

const RESULTS_PARAMS = Promise.resolve({ sessionId: "9" });
const RESULTS_SEARCH_PARAMS = Promise.resolve({ group: "4" });
const { useGetSessionMock, useGetResultsMock, resultsData } = vi.hoisted(() => ({
  useGetSessionMock: vi.fn(),
  useGetResultsMock: vi.fn(),
  resultsData: {
    current: {
      groupId: 4,
      cardCountRanking: [
        { playerId: 1, displayName: "Alex", cardCount: 5, rank: 1 },
        { playerId: 2, displayName: "Sam", cardCount: 3, rank: 2 },
      ],
      mostArtistsGuessed: [{ playerId: 1, displayName: "Alex", value: 7, rank: 1 }],
      mostTitlesGuessed: [{ playerId: 2, displayName: "Sam", value: 4, rank: 1 }],
    } as Record<string, unknown>,
  },
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={typeof href === "string" ? href : "#"}>{children}</a>
  ),
}));

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetSession: (...args: unknown[]) => {
    useGetSessionMock(...args);
    return { data: undefined, isLoading: false, isError: true };
  },
  useGetResults: (...args: unknown[]) => {
    useGetResultsMock(...args);
    return { data: resultsData.current, isLoading: false, isError: false };
  },
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetActiveMembership: () => ({ data: { id: 4 } }),
}));

function renderPage() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionResultsPage params={RESULTS_PARAMS} searchParams={RESULTS_SEARCH_PARAMS} />
    </QueryClientProvider>,
  );
}

describe("SessionResultsPage download options", () => {
  it("neutralizes spreadsheet formula prefixes in CSV cells", () => {
    expect(csvCell("=SUM(A1:A2)")).toBe("\"'=SUM(A1:A2)\"");
    expect(csvCell("+1")).toBe("\"'+1\"");
    expect(csvCell("-1")).toBe("\"'-1\"");
    expect(csvCell("@value")).toBe("\"'@value\"");
    expect(csvCell("Alex")).toBe("\"Alex\"");
  });

  it("offers PDF, text, and CSV actions", async () => {
    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole("button", { name: "Download results" }));

    expect(screen.getByRole("button", { name: "Download PDF" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Copy as text" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Download CSV" })).toBeVisible();
  });

  it("copies the results summary as text", async () => {
    const writeText = vi.fn(() => Promise.resolve());
    Object.defineProperty(window.navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
    await act(async () => {
      renderPage();
    });

    fireEvent.click(screen.getByRole("button", { name: "Download results" }));
    fireEvent.click(screen.getByRole("button", { name: "Copy as text" }));

    expect(writeText).toHaveBeenCalledWith(
      expect.stringContaining("Alex wins with 5 cards"),
    );
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining("1. Alex - 5 cards"));
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining("1. Alex: 7"));
  });

  it("reads the group from the URL even though the ended session is gone", async () => {
    await act(async () => {
      renderPage();
    });

    expect(useGetResultsMock).toHaveBeenCalledWith(4, expect.anything());
    expect(useGetSessionMock).toHaveBeenCalledWith(9, { query: { enabled: false, retry: false } });
  });

  it("shares first place between tied players", async () => {
    resultsData.current = {
      groupId: 4,
      cardCountRanking: [
        { playerId: 1, displayName: "Alex", cardCount: 5, rank: 1 },
        { playerId: 2, displayName: "Sam", cardCount: 5, rank: 1 },
        { playerId: 3, displayName: "Kim", cardCount: 2, rank: 3 },
      ],
      mostArtistsGuessed: [],
      mostTitlesGuessed: [],
    };
    await act(async () => {
      renderPage();
    });

    expect(screen.getByText("Alex and Sam tie for first with 5 cards")).toBeVisible();
    expect(screen.getAllByText("3").length).toBeGreaterThan(0);
  });
});
