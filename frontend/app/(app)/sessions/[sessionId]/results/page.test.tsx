import { act, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import SessionResultsPage from "./page";

const RESULTS_PARAMS = Promise.resolve({ sessionId: "9" });

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={typeof href === "string" ? href : "#"}>{children}</a>
  ),
}));

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetSession: () => ({ data: { id: 9 }, isLoading: false, isError: false }),
  useGetResults: () => ({
    data: {
      groupId: 4,
      cardCountRanking: [
        { playerId: 1, displayName: "Alex", cardCount: 5, rank: 1 },
        { playerId: 2, displayName: "Sam", cardCount: 3, rank: 2 },
      ],
      mostArtistsGuessed: [{ playerId: 1, displayName: "Alex", value: 7 }],
      mostTitlesGuessed: [{ playerId: 2, displayName: "Sam", value: 4 }],
    },
    isLoading: false,
    isError: false,
  }),
}));

function renderPage() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionResultsPage params={RESULTS_PARAMS} />
    </QueryClientProvider>,
  );
}

describe("SessionResultsPage download options", () => {
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
      expect.stringContaining("Alex reached 5 cards first"),
    );
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining("1. Alex - 5 cards"));
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining("Alex: 7"));
  });
});
