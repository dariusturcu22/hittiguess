import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { GameSessionDTO } from "@/hooks/models/gameSessionDTO";
import { ActiveSessionWidget, phaseDeadline } from "./active-session-widget";

const CURRENT_USER_ID = 11;
const SESSION_ID = 9;
const OWN_PLAYER_ID = 6;
const ROUND_ID = 30;
const NOW = new Date("2026-10-09T12:00:00Z");
const PHASE_END = "2026-10-09T12:00:10Z";
const playSound = vi.fn();
let pathname = "/playlists";
let session: GameSessionDTO;

vi.mock("next/navigation", () => ({ usePathname: () => pathname }));
vi.mock("@/hooks/generated/group-management/group-management", () => ({ useGetActiveMembership: () => ({ data: { id: 4, status: "LOCKED" } }) }));
vi.mock("@/hooks/generated/user-management/user-management", () => ({ useGetCurrentUser: () => ({ data: { id: CURRENT_USER_ID } }) }));
vi.mock("@/hooks/use-game-session-realtime", () => ({ useGameSessionRealtime: vi.fn() }));
vi.mock("@/hooks/use-turn-sound", () => ({ useTurnSound: () => playSound }));
vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetActiveSessionForGroup: () => ({ data: { id: SESSION_ID } }),
  useGetSession: () => ({ data: session }),
}));

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
  playSound.mockClear();
  pathname = "/playlists";
  session = { currentRound: { id: ROUND_ID, roundNumber: 2, activePlayerId: OWN_PLAYER_ID, status: "AWAITING_PLACEMENT" }, players: [{ id: OWN_PLAYER_ID, userId: CURRENT_USER_ID, displayName: "Sam", tokenCount: 3 }] };
});
afterEach(() => vi.useRealTimers());

describe("ActiveSessionWidget", () => {
  it("shows the active player, round, own tokens and accessible return link", () => {
    render(<ActiveSessionWidget />);
    const returnLink = screen.getByRole("link", { name: "Return to game in progress" });
    expect(returnLink).toHaveAttribute("href", `/sessions/${SESSION_ID}`);
    expect(returnLink).toHaveTextContent("Sam is placing a card");
    expect(returnLink).toHaveTextContent("Round 2");
    expect(returnLink).toHaveTextContent("Your tokens: 3");
    expect(screen.queryByText(/Phase ends/)).not.toBeInTheDocument();
  });

  it("alerts once per own turn and retains that identity across refreshed data and navigation", () => {
    const view = render(<ActiveSessionWidget />);
    expect(screen.getByRole("link", { name: "Your turn. Return to game" })).toHaveAttribute("href", `/sessions/${SESSION_ID}`);
    expect(playSound).toHaveBeenCalledTimes(1);
    session = { ...session, currentRound: { ...session.currentRound } };
    view.rerender(<ActiveSessionWidget />);
    pathname = `/sessions/${SESSION_ID}`;
    view.rerender(<ActiveSessionWidget />);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    pathname = "/explore";
    view.rerender(<ActiveSessionWidget />);
    expect(playSound).toHaveBeenCalledTimes(1);
    session.currentRound = { ...session.currentRound, id: ROUND_ID + 1 };
    view.rerender(<ActiveSessionWidget />);
    expect(playSound).toHaveBeenCalledTimes(2);
  });

  it("hides the turn banner once placement locks and counts the current server deadline", () => {
    session.currentRound = { ...session.currentRound, status: "COUNTDOWN", countdownEndsAt: PHASE_END };
    render(<ActiveSessionWidget />);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(playSound).not.toHaveBeenCalled();
    expect(screen.getByText("Phase ends in 10s")).toBeVisible();
    act(() => vi.advanceTimersByTime(3_000));
    expect(screen.getByText("Phase ends in 7s")).toBeVisible();
  });

  it("does not alert spectators or show the widget on the session route", () => {
    session.currentRound = { ...session.currentRound, activePlayerId: OWN_PLAYER_ID + 1 };
    const view = render(<ActiveSessionWidget />);
    expect(playSound).not.toHaveBeenCalled();
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    pathname = `/sessions/${SESSION_ID}`;
    view.rerender(<ActiveSessionWidget />);
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("uses only the current phase deadline", () => {
    const round = { placementEndsAt: PHASE_END, countdownEndsAt: "countdown", bettingWindowEndsAt: "betting", nextRoundStartsAt: "next" };
    expect(phaseDeadline({ ...round, status: "AWAITING_PLACEMENT" })).toBe(PHASE_END);
    expect(phaseDeadline({ ...round, status: "COUNTDOWN" })).toBe("countdown");
    expect(phaseDeadline({ ...round, status: "BETTING" })).toBe("betting");
    expect(phaseDeadline({ ...round, status: "REVEALED" })).toBe("next");
    expect(phaseDeadline({ ...round, status: "SCORED" })).toBe("next");
    expect(phaseDeadline(undefined)).toBeUndefined();
  });
});
