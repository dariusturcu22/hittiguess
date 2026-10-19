import { act, render, screen } from "@testing-library/react";
import { Suspense } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import GameHistoryDetailPage from "./page";

const mocks = vi.hoisted(() => ({ detail: vi.fn() }));
vi.mock("@/hooks/game-history", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/hooks/game-history")>(), useGameHistoryDetail: mocks.detail,
}));
const SUMMARY_ID = "7";
const summary = {
  id: 7, groupName: "Group ABCD", startedAt: "2026-10-09T10:00:00Z", endedAt: "2026-10-09T10:18:00Z",
  mode: "DIFFICULTY", difficultyTier: "MEDIUM", endingReason: "TARGET_REACHED", winTargetCards: 8,
  participants: [{ id: 1, userId: null, displayName: "Deleted player", participationStatus: "LEFT", finalCardCount: 8,
    cardRank: 1, artistRank: 2, titleRank: 1, isWinner: true, correctPlacements: 7, placementAttempts: 9,
    correctTitles: 4, titleAttempts: 7, correctArtists: 5, artistAttempts: 8, betsWon: 1, betsPlaced: 2 }],
};
async function show(summaryId = SUMMARY_ID) {
  await act(async () => { render(<Suspense><GameHistoryDetailPage params={Promise.resolve({ summaryId })} /></Suspense>); });
}
describe("Game history detail", () => {
  beforeEach(() => { mocks.detail.mockReset(); mocks.detail.mockReturnValue({ data: summary }); });
  it("shows deleted and departed participants with their results", async () => {
    await show();
    expect(screen.getByText("Deleted player · Winner · Left")).toBeVisible();
    expect(screen.getByText("7 / 9")).toBeVisible();
    expect(screen.getByRole("link", { name: "Back to history" })).toHaveAttribute("href", "/history");
  });
  it("explains that interruptions do not count as wins", async () => {
    mocks.detail.mockReturnValue({ data: { ...summary, endingReason: "INTERRUPTED" } });
    await show();
    expect(screen.getByText(/does not count as a competitive win/)).toBeVisible();
  });
  it("does not expose results when access fails", async () => {
    mocks.detail.mockReturnValue({ isError: true });
    await show();
    expect(screen.getByRole("alert")).toHaveTextContent("unavailable or was not played by your account");
    expect(screen.queryByText("Deleted player")).toBeNull();
  });
  it("handles malformed history links", async () => {
    mocks.detail.mockReturnValue({});
    await show("invalid");
    expect(screen.getByRole("alert")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Retry" })).toBeNull();
  });
});
