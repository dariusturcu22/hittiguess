import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import GameSessionPage from "./page";

let roundEventHandler: ((event: { type: string; payload?: Record<string, unknown> }) => void) | undefined;
let guessResultHandler: ((result: { roundId?: number; artistCorrect?: boolean; titleCorrect?: boolean }) => void) | undefined;
const placeCard = vi.fn(() => true);
const previewPlacement = vi.fn(() => true);
const submitGuess = vi.fn(() => true);
const skipBetting = vi.fn(() => true);
const SESSION_PARAMS = Promise.resolve({ sessionId: "1" });
const WATCH_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
const ACTIVE_USER_ID = 11;
const DJ_USER_ID = 12;
const SPECTATOR_USER_ID = 13;
const BETTING_WINDOW_REMAINING_MILLISECONDS = 9_000;
const REVEAL_HOLD_REMAINING_MILLISECONDS = 4_000;

const TIMELINE = [
  { songId: 1, artist: "Natalie Imbruglia", title: "Torn", releaseYear: 1998, color: "89b4fa", position: 0 },
  { songId: 2, artist: "OutKast", title: "Hey Ya!", releaseYear: 2003, color: "a6e3a1", position: 1 },
];

const MID_GAME_SESSION = {
  id: 1,
  groupId: 2,
  currentRoundNumber: 3,
  djMode: "ROTATING",
  winConditionCardCount: 8,
  players: [
    { id: 7, userId: ACTIVE_USER_ID, displayName: "Alex", tokenCount: 0, turnOrder: 0, status: "ACTIVE", timeline: TIMELINE },
    { id: 8, userId: DJ_USER_ID, displayName: "Sam", tokenCount: 0, turnOrder: 1, status: "ACTIVE", timeline: [] },
    { id: 9, userId: SPECTATOR_USER_ID, displayName: "Jo", tokenCount: 2, turnOrder: 2, status: "ACTIVE", timeline: [] },
  ],
  currentRound: {
    id: 30,
    roundNumber: 3,
    status: "AWAITING_PLACEMENT",
    activePlayerId: 7,
    djPlayerId: 8,
    bets: [],
  } as Record<string, unknown>,
};

type MockGameSession = Omit<typeof MID_GAME_SESSION, "currentRound"> & { currentRound: Record<string, unknown> | null };

let mockSessionData: MockGameSession | null = MID_GAME_SESSION;
let mockCurrentUserId = ACTIVE_USER_ID;

function withRound(round: Record<string, unknown>): MockGameSession {
  return { ...MID_GAME_SESSION, currentRound: { ...MID_GAME_SESSION.currentRound, ...round } };
}

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetCurrentRoundLinkOut: (_sessionId: number, options: { query: { enabled: boolean } }) => ({
    data: options.query.enabled ? { watchUrl: WATCH_URL, artist: "Dua Lipa", title: "Levitating", releaseYear: 2020, color: "74c7ec" } : undefined,
    isLoading: false,
  }),
  useGetSession: () => ({
    data: mockSessionData,
    isLoading: false,
    isError: !mockSessionData,
  }),
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetGroup: () => ({ data: { playlists: [{ id: 5, name: "Midnight Radio", color: "cba6f7", songCount: 32, previewYoutubeIds: [] }] } }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({ data: { id: mockCurrentUserId } }),
}));

vi.mock("@/hooks/use-group-realtime", () => ({
  useGroupRealtime: () => ({ connectionState: "connected", sendChat: vi.fn(() => true) }),
}));

vi.mock("@/lib/lock-in-sound", () => ({
  playLockInSound: vi.fn(),
}));

vi.mock("@/hooks/use-game-session-realtime", () => ({
  PLACEMENT_PREVIEW_EVENT: "PLACEMENT_PREVIEW",
  GUESS_CORRECT_EVENT: "GUESS_CORRECT",
  useGameSessionRealtime: (
    _sessionId: number,
    handler: (event: { type: string }) => void,
    onGuessResult: (result: { roundId?: number; artistCorrect?: boolean; titleCorrect?: boolean }) => void,
  ) => {
    roundEventHandler = handler;
    guessResultHandler = onGuessResult;
    return {
      connectionState: "connected",
      placeCard,
      previewPlacement,
      placeBet: vi.fn(() => true),
      skipBetting,
      submitGuess,
    };
  },
}));

vi.mock("@/components/group-chat-overlay", () => ({
  GroupChatOverlay: () => <aside aria-label="Chat" />,
}));

async function renderPage() {
  await act(async () => {
    render(<GameSessionPage params={SESSION_PARAMS} />);
  });
}

describe("GameSessionPage", () => {
  beforeEach(() => {
    placeCard.mockClear();
    previewPlacement.mockClear();
    submitGuess.mockClear();
    skipBetting.mockClear();
    roundEventHandler = undefined;
    guessResultHandler = undefined;
    mockSessionData = MID_GAME_SESSION;
    mockCurrentUserId = ACTIVE_USER_ID;
    window.sessionStorage.clear();
  });

  it("shows the header with the round, the turn status, and the playlist chip", async () => {
    await renderPage();

    expect(screen.getByRole("heading", { name: "Round 3" })).toBeVisible();
    expect(screen.getByText("Your turn")).toBeVisible();
    expect(screen.getByText("Midnight Radio")).toBeVisible();
    expect(screen.getByText("32 songs")).toBeVisible();
  });

  it("renders timeline cards with their artist, year, and title", async () => {
    await renderPage();

    expect(screen.getByText("Natalie Imbruglia")).toBeVisible();
    expect(screen.getByText("1998")).toBeVisible();
    expect(screen.getByText("Torn")).toBeVisible();
  });

  it("places the card with the keyboard, previews the gap, and locks it in", async () => {
    await renderPage();

    fireEvent.keyDown(screen.getByRole("button", { name: "Your card. Choose a timeline position." }), { key: "Enter" });
    expect(await screen.findByRole("button", { name: "Lock in answer" })).toBeVisible();
    expect(screen.getByText("Confirm your placement")).toBeVisible();

    fireEvent.keyDown(screen.getByRole("button", { name: "Your card. Choose a timeline position." }), { key: "ArrowLeft" });
    await waitFor(() => expect(previewPlacement).toHaveBeenLastCalledWith(1));

    fireEvent.click(screen.getByRole("button", { name: "Lock in answer" }));
    expect(placeCard).toHaveBeenCalledWith(1);
  });

  it("shows the turn banner for the active player when a round starts", async () => {
    await renderPage();

    act(() => {
      roundEventHandler?.({ type: "ROUND_STARTED" });
    });

    expect(screen.getByRole("status")).toHaveTextContent("Your turn: get ready");
    expect(screen.getByRole("status")).toHaveTextContent("Round 3");
  });

  it("submits the artist and title together with feedback", async () => {
    await renderPage();

    fireEvent.change(screen.getByPlaceholderText("Guess the artist"), { target: { value: "Beatles" } });
    fireEvent.change(screen.getByPlaceholderText("Guess the title"), { target: { value: "Yesterday" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit guess the title" }));

    expect(submitGuess).toHaveBeenCalledWith("Beatles", "Yesterday");
    expect(screen.getByText("Checking your guess…")).toBeVisible();
  });

  it("tells the guesser which part of their guess was right", async () => {
    await renderPage();

    act(() => {
      guessResultHandler?.({ roundId: 30, artistCorrect: true, titleCorrect: false });
    });
    expect(screen.getByText("You got the artist.")).toBeVisible();

    act(() => {
      guessResultHandler?.({ roundId: 30, artistCorrect: false, titleCorrect: false });
    });
    expect(screen.getByText("Not quite. Try again.")).toBeVisible();
  });

  it("announces another player's correct guess without the answer", async () => {
    await renderPage();

    act(() => {
      roundEventHandler?.({ type: "GUESS_CORRECT", payload: { roundId: 30, playerId: 9, displayName: "Jo", artistGuessed: true, titleGuessed: false } });
    });

    expect(screen.getByRole("status")).toHaveTextContent("Jo guessed the artist");
  });

  it("doesn't announce the viewer's own correct guess as a toast", async () => {
    await renderPage();

    act(() => {
      roundEventHandler?.({ type: "GUESS_CORRECT", payload: { roundId: 30, playerId: 7, displayName: "Alex", artistGuessed: true, titleGuessed: true } });
    });

    expect(screen.queryByText("Alex guessed the artist and the title")).toBeNull();
  });

  it("shows the DJ the song card and a real YouTube link", async () => {
    mockCurrentUserId = DJ_USER_ID;
    await renderPage();

    const link = await screen.findByRole("link", { name: "Open on YouTube to play" });
    expect(link).toHaveAttribute("href", WATCH_URL);
    expect(link).toHaveAttribute("target", "_blank");
    expect(screen.getByText("Levitating")).toBeVisible();
    expect(screen.getByText("You're the DJ")).toBeVisible();
    expect(screen.queryByPlaceholderText("Guess the artist")).toBeNull();
  });

  it("starts the audio share and opens YouTube in its own window from one click", async () => {
    mockCurrentUserId = DJ_USER_ID;
    const shareRequests = vi.fn();
    const youtubeWindow = { opener: window };
    const openWindow = vi.spyOn(window, "open").mockReturnValue(youtubeWindow as unknown as Window);
    window.addEventListener("session-start-audio-share", shareRequests);
    await renderPage();

    fireEvent.click(await screen.findByRole("link", { name: "Open on YouTube to play" }));

    expect(shareRequests).toHaveBeenCalledOnce();
    expect(openWindow).toHaveBeenCalledWith(WATCH_URL, "hittiguess-youtube", expect.stringContaining("popup"));
    expect(youtubeWindow.opener).toBeNull();
    expect(screen.getByRole("button", { name: "Pick the YouTube window to share its audio" })).toBeVisible();
    window.removeEventListener("session-start-audio-share", shareRequests);
    openWindow.mockRestore();
  });

  it("shows spectators the live placement preview", async () => {
    mockCurrentUserId = SPECTATOR_USER_ID;
    await renderPage();

    expect(screen.getByText("Watching live. It's not your turn to place a card.")).toBeVisible();
    act(() => {
      roundEventHandler?.({ type: "PLACEMENT_PREVIEW", payload: { roundId: 30, activePlayerId: 7, position: 1 } });
    });

    expect(screen.getByRole("region", { name: "Game stage" }).querySelector(".gameplay-gap-pulse")).not.toBeNull();
    expect(screen.queryByRole("button", { name: "Your card. Choose a timeline position." })).toBeNull();
  });

  it("closes guessing and stops the active player's audio once the card is locked in", async () => {
    mockSessionData = withRound({ status: "COUNTDOWN", placedPosition: 1 });
    await renderPage();

    expect(screen.getByText("Card locked in")).toBeVisible();
    expect(screen.getByText("Audio stopped")).toBeVisible();
    expect(screen.getByText("Guessing is closed for this card")).toBeVisible();
    expect(screen.getByLabelText("Locked card")).toBeVisible();
    expect(screen.getByText("Sitting out")).toBeVisible();
  });

  it("gives a token holder the betting timer and the skip action", async () => {
    mockSessionData = withRound({ status: "BETTING", placedPosition: 1, bettingWindowEndsAt: new Date(Date.now() + BETTING_WINDOW_REMAINING_MILLISECONDS).toISOString() });
    mockCurrentUserId = SPECTATOR_USER_ID;
    await renderPage();

    expect(screen.getByText("Betting closes")).toBeVisible();
    expect(screen.getByText("Drag a token into a gap you think is right")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Skip betting" }));
    expect(skipBetting).toHaveBeenCalledOnce();
  });

  it("replaces the skip action with a waiting note once the viewer has skipped", async () => {
    mockSessionData = withRound({ status: "BETTING", placedPosition: 1, bettingSkippedPlayerIds: [9], bettingWindowEndsAt: new Date(Date.now() + BETTING_WINDOW_REMAINING_MILLISECONDS).toISOString() });
    mockCurrentUserId = SPECTATOR_USER_ID;
    await renderPage();

    expect(screen.getByText("Skipped. Waiting for the others.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Skip betting" })).toBeNull();
  });

  it("doesn't offer the active player a skip action", async () => {
    mockSessionData = withRound({ status: "BETTING", placedPosition: 1, bettingWindowEndsAt: new Date(Date.now() + BETTING_WINDOW_REMAINING_MILLISECONDS).toISOString() });
    await renderPage();

    expect(screen.queryByRole("button", { name: "Skip betting" })).toBeNull();
  });

  it("shows placed bets as coins on the timeline", async () => {
    mockSessionData = withRound({ status: "BETTING", placedPosition: 1, bets: [{ playerId: 9, position: 0 }] });
    await renderPage();

    expect(screen.getByLabelText("Jo's bet")).toBeVisible();
    expect(screen.getByText("One token is staked on this card.")).toBeVisible();
  });

  it("reveals the card, who took it, and who plays next", async () => {
    mockSessionData = withRound({
      status: "SCORED",
      placedPosition: 2,
      placementCorrect: true,
      revealedArtist: "Dua Lipa",
      revealedTitle: "Levitating",
      revealedYear: 2020,
      revealedColor: "74c7ec",
      nextRoundStartsAt: new Date(Date.now() + REVEAL_HOLD_REMAINING_MILLISECONDS).toISOString(),
    });
    await renderPage();

    expect(screen.getByText("Correct! Card locked in")).toBeVisible();
    expect(screen.getByText("Levitating")).toBeVisible();
    expect(screen.getByText("Next turn starts in")).toBeVisible();
    expect(screen.getByText(/takes the card/)).toBeVisible();
  });

  it("opens the first round with the round intro", async () => {
    mockSessionData = { ...withRound({ roundNumber: 1 }), currentRoundNumber: 1 };
    await renderPage();

    expect(screen.getByText("First DJ")).toBeVisible();
    expect(screen.getByText("First turn")).toBeVisible();
  });

  it("shows the waiting state before the first round", async () => {
    mockSessionData = { ...MID_GAME_SESSION, currentRound: null };
    await renderPage();

    expect(await screen.findByText("Your timeline will appear when the round starts.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Your card. Choose a timeline position." })).toBeNull();
  });
});
