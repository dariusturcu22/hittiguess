import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import GameSessionPage from "./page";

let roundEventHandler: ((event: { type: string }) => void) | undefined;
const placeCard = vi.fn(() => true);
const submitGuess = vi.fn(() => true);
const SESSION_PARAMS = Promise.resolve({ sessionId: "1" });

const MID_GAME_SESSION = {
  id: 1,
  groupId: 2,
  currentRoundNumber: 3,
  djMode: "ROTATING",
  players: [
    { id: 7, userId: 11, displayName: "Alex", tokenCount: 2, timeline: [] },
    { id: 8, userId: 12, displayName: "Sam", tokenCount: 2, timeline: [] },
  ],
  currentRound: {
    roundNumber: 3,
    status: "AWAITING_PLACEMENT",
    activePlayerId: 7,
    djPlayerId: 8,
  },
};

type MockGameSession = Omit<typeof MID_GAME_SESSION, "currentRound"> & {
  currentRound: (typeof MID_GAME_SESSION)["currentRound"] | null;
};

let mockSessionData: MockGameSession | null = MID_GAME_SESSION;
let mockCurrentUserId = 11;

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetCurrentRoundLinkOut: () => ({ data: undefined, isLoading: false }),
  useGetSession: () => ({
    data: mockSessionData,
    isLoading: false,
    isError: !mockSessionData,
  }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({ data: { id: mockCurrentUserId } }),
}));

vi.mock("@/hooks/use-group-realtime", () => ({
  useGroupRealtime: () => ({ connectionState: "connected", sendChat: vi.fn(() => true) }),
}));

vi.mock("@/hooks/use-game-session-realtime", () => ({
  useGameSessionRealtime: (_sessionId: number, handler: (event: { type: string }) => void) => {
    roundEventHandler = handler;
    return {
      connectionState: "connected",
      placeCard,
      placeBet: vi.fn(() => true),
      skipBetting: vi.fn(() => true),
      submitGuess,
    };
  },
}));

vi.mock("@/components/group-chat-overlay", () => ({
  GroupChatOverlay: () => <aside aria-label="Chat" />,
}));

describe("GameSessionPage gameplay interactions", () => {
  beforeEach(() => {
    placeCard.mockClear();
    submitGuess.mockClear();
    roundEventHandler = undefined;
    mockSessionData = MID_GAME_SESSION;
    mockCurrentUserId = 11;
  });

  it("activates the card with the keyboard and reports placement feedback", async () => {
    await act(async () => {
      render(<GameSessionPage params={SESSION_PARAMS} />);
    });

    fireEvent.keyDown(await screen.findByRole("button", { name: "Your card. Choose a timeline position." }), {
      key: "Enter",
    });

    const firstDropTarget = screen.getByRole("button", { name: "Place card at timeline position 1" });
    fireEvent.click(firstDropTarget);

    await waitFor(() => expect(placeCard).toHaveBeenCalledWith(0));
    expect(screen.getByText("Card placed. Waiting for the reveal.")).toBeVisible();
  });

  it("clears the placement feedback when the next round starts", async () => {
    await act(async () => {
      render(<GameSessionPage params={SESSION_PARAMS} />);
    });

    fireEvent.keyDown(await screen.findByRole("button", { name: "Your card. Choose a timeline position." }), {
      key: "Enter",
    });
    fireEvent.click(screen.getByRole("button", { name: "Place card at timeline position 1" }));
    await screen.findByText("Card placed. Waiting for the reveal.");

    act(() => {
      roundEventHandler?.({ type: "NEXT_ROUND" });
    });

    await waitFor(() => expect(screen.queryByText("Card placed. Waiting for the reveal.")).toBeNull());
  });

  it("shows the turn notification for a round-start realtime event", async () => {
    await act(async () => {
      render(<GameSessionPage params={SESSION_PARAMS} />);
    });

    await screen.findByRole("button", { name: "Your card. Choose a timeline position." });
    act(() => {
      roundEventHandler?.({ type: "ROUND_STARTED" });
    });

    expect(screen.getByRole("status")).toHaveTextContent("Alex's turn: get ready");
    expect(screen.getByRole("status")).toHaveTextContent("Round 3");
  });

  it("submits artist and title guesses with feedback", async () => {
    await act(async () => {
      render(<GameSessionPage params={SESSION_PARAMS} />);
    });

    fireEvent.change(screen.getByPlaceholderText("Guess the artist"), { target: { value: "Beatles" } });
    fireEvent.change(screen.getByPlaceholderText("Guess the title"), { target: { value: "Yesterday" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit guess the artist" }));

    await waitFor(() => expect(submitGuess).toHaveBeenCalledWith("Beatles", "Yesterday"));
    expect(screen.getByText("Guess sent. Keep listening for the result.")).toBeVisible();
  });

  it("shows the DJ link-out view without a placement card", async () => {
    mockCurrentUserId = 12;
    await act(async () => {
      render(<GameSessionPage params={SESSION_PARAMS} />);
    });

    expect(await screen.findByRole("button", { name: "Open on YouTube to play" })).toBeVisible();
    expect(screen.queryByRole("button", { name: "Your card. Choose a timeline position." })).toBeNull();
  });

  it("shows the waiting state before the first round", async () => {
    mockSessionData = { ...MID_GAME_SESSION, currentRound: null };
    await act(async () => {
      render(<GameSessionPage params={SESSION_PARAMS} />);
    });

    expect(await screen.findByText("Your timeline will appear when the round starts.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Your card. Choose a timeline position." })).toBeNull();
  });

  it("shows the spectator view for a non-active non-DJ player", async () => {
    mockSessionData = {
      ...MID_GAME_SESSION,
      players: [
        ...MID_GAME_SESSION.players,
        { id: 9, userId: 13, displayName: "Jo", tokenCount: 0, timeline: [] },
      ],
    };
    mockCurrentUserId = 13;
    await act(async () => {
      render(<GameSessionPage params={SESSION_PARAMS} />);
    });

    expect(await screen.findByPlaceholderText("Guess the artist")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Your card. Choose a timeline position." })).toBeNull();
    expect(screen.queryByRole("button", { name: "Open on YouTube to play" })).toBeNull();
  });
});
