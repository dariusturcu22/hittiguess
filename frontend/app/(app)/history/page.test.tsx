import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import GameHistoryPage from "./page";

const mocks = vi.hoisted(() => ({
  history: vi.fn(), statistics: vi.fn(), retry: vi.fn(),
}));
vi.mock("@/hooks/game-history", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/hooks/game-history")>(),
  useGameHistory: mocks.history, useHistoryStatistics: mocks.statistics,
}));
vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({ data: { id: 11 } }),
}));
const SUMMARY_ID = 7;
const summary = {
  id: SUMMARY_ID, groupName: "Group ABCD", startedAt: "2026-10-09T10:00:00Z", endedAt: "2026-10-09T10:18:00Z",
  mode: "DIFFICULTY", difficultyTier: "MEDIUM", endingReason: "TARGET_REACHED",
  participants: [{ userId: 11, isWinner: true, finalCardCount: 8 }, { userId: 12, isWinner: true, finalCardCount: 8 }],
};
describe("Game history", () => {
  beforeEach(() => {
    mocks.history.mockReset(); mocks.statistics.mockReset(); mocks.retry.mockReset();
    mocks.history.mockReturnValue({ data: { items: [summary], total: 21 }, refetch: mocks.retry });
    mocks.statistics.mockReturnValue({ data: { gamesPlayed: 12, wins: 5 } });
  });
  it("shows real totals, tied wins, and detail navigation", () => {
    render(<GameHistoryPage />);
    expect(screen.getByText("42%")).toBeVisible();
    expect(screen.getByText("Joint winner")).toBeVisible();
    expect(screen.getByRole("link", { name: "View results for Group ABCD" })).toHaveAttribute("href", `/history/${SUMMARY_ID}`);
  });
  it("requests the next page", () => {
    render(<GameHistoryPage />);
    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(mocks.history).toHaveBeenLastCalledWith(1);
    expect(screen.getByText("Page 2")).toBeVisible();
  });
  it("has an explicit empty state", () => {
    mocks.history.mockReturnValue({ data: { items: [], total: 0 } });
    render(<GameHistoryPage />);
    expect(screen.getByText(/No games yet/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });
  it("offers retry on failure without displaying fake statistics", () => {
    mocks.history.mockReturnValue({ isError: true, refetch: mocks.retry });
    mocks.statistics.mockReturnValue({ isError: true, refetch: mocks.retry });
    render(<GameHistoryPage />);
    expect(screen.getAllByText("Unavailable")).toHaveLength(3);
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    expect(mocks.retry).toHaveBeenCalled();
  });
  it("shows a loading state", () => {
    mocks.history.mockReturnValue({ isLoading: true });
    mocks.statistics.mockReturnValue({ isLoading: true });
    render(<GameHistoryPage />);
    expect(screen.getByText("Loading game history")).toBeInTheDocument();
  });
  it("marks interruptions independently of winner flags", () => {
    mocks.history.mockReturnValue({ data: { items: [{ ...summary, endingReason: "INTERRUPTED" }], total: 1 } });
    render(<GameHistoryPage />);
    expect(screen.getByText("Interrupted")).toBeVisible();
  });
});
