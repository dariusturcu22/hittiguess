import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import GameSessionPage from "./page";

let roundEventHandler: ((event: { type: string; payload?: Record<string, unknown> }) => void) | undefined;
type MockGuessResult = { roundId?: number; artistCorrect?: boolean; titleCorrect?: boolean; state?: Record<string, unknown> };
let guessResultHandler: ((result: MockGuessResult) => void) | undefined;
let sessionEndedHandler: ((ended: { groupId?: number }) => void) | undefined;
let mockGuessState: Record<string, unknown> | undefined;
const routerReplace = vi.fn();
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
    turnNumber: 7,
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
  useRouter: () => ({ replace: routerReplace }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ setQueryData: vi.fn() }),
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
  useGetGuessState: () => ({ data: mockGuessState }),
  getGetGuessStateQueryKey: (sessionId: number) => [`/api/sessions/${sessionId}/guess-state`],
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetActiveMembership: () => ({ data: { id: 2 } }),
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
    onGuessResult: (result: MockGuessResult) => void,
    onSessionEnded: (ended: { groupId?: number }) => void,
  ) => {
    roundEventHandler = handler;
    guessResultHandler = onGuessResult;
    sessionEndedHandler = onSessionEnded;
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
    sessionEndedHandler = undefined;
    mockGuessState = undefined;
    routerReplace.mockClear();
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

  it("keeps the guess fields next to the lock-in button once the card is dropped", async () => {
    await renderPage();

    fireEvent.keyDown(screen.getByRole("button", { name: "Your card. Choose a timeline position." }), { key: "Enter" });

    expect(await screen.findByRole("button", { name: "Lock in answer" })).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Guess the artist" })).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Guess the title" })).toBeVisible();
  });

  it("shows the turn banner for the active player when a round starts", async () => {
    await renderPage();

    act(() => {
      roundEventHandler?.({ type: "ROUND_STARTED" });
    });

    expect(screen.getByRole("status")).toHaveTextContent("Your turn: get ready");
    expect(screen.getByRole("status")).toHaveTextContent("Round 3");
  });

  it("submits the artist and the title separately with feedback", async () => {
    await renderPage();

    fireEvent.change(screen.getByRole("textbox", { name: "Guess the artist" }), { target: { value: "Beatles" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit guess the artist" }));
    expect(submitGuess).toHaveBeenLastCalledWith("Beatles", "");

    fireEvent.change(screen.getByRole("textbox", { name: "Guess the title" }), { target: { value: "Yesterday" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit guess the title" }));
    expect(submitGuess).toHaveBeenLastCalledWith("", "Yesterday");
    expect(screen.getByText("Checking your guess…")).toBeVisible();
  });

  it("tells the guesser how their artist guess went", async () => {
    await renderPage();

    fireEvent.change(screen.getByRole("textbox", { name: "Guess the artist" }), { target: { value: "Queen" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit guess the artist" }));
    act(() => {
      guessResultHandler?.({ roundId: 30, artistCorrect: true, titleCorrect: false, state: { roundId: 30, artistCount: 2, correctArtistCount: 1 } });
    });
    expect(screen.getByText("You got an artist. 1 more credited.")).toBeVisible();

    fireEvent.change(screen.getByRole("textbox", { name: "Guess the artist" }), { target: { value: "Freddie" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit guess the artist" }));
    act(() => {
      guessResultHandler?.({ roundId: 30, artistCorrect: false, titleCorrect: false, state: { roundId: 30, artistCount: 2, correctArtistCount: 1, artistGuessingClosed: true } });
    });
    expect(screen.getByText("Wrong artist. No more artist guesses this round.")).toBeVisible();
  });

  it("disables the guess fields whose guessing is closed for the round", async () => {
    mockGuessState = { roundId: 30, artistCount: 1, correctArtistCount: 0, artistGuessingClosed: true, titleGuessed: true, titleCorrect: true };
    await renderPage();

    expect(screen.getByRole("textbox", { name: "Guess the artist" })).toBeDisabled();
    expect(screen.getByRole("textbox", { name: "Guess the title" })).toBeDisabled();
    expect(screen.getByPlaceholderText("Title guessed")).toBeVisible();
  });

  it("moves to the results screen with the group id when the game ends", async () => {
    await renderPage();

    act(() => {
      sessionEndedHandler?.({ groupId: 2 });
    });

    expect(routerReplace).toHaveBeenCalledWith("/sessions/1/results?group=2");
  });

  it("goes back to the lobby when an ended session was abandoned", async () => {
    await renderPage();

    act(() => {
      sessionEndedHandler?.({});
    });

    expect(routerReplace).toHaveBeenCalledWith("/groups/2");
  });

  it("sends a player whose session is already gone to the group's results", async () => {
    mockSessionData = null;
    await renderPage();

    expect(routerReplace).toHaveBeenCalledWith("/sessions/1/results?group=2");
    expect(screen.getByRole("link", { name: "Back to lobby" })).toHaveAttribute("href", "/groups/2");
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

  it("asks the voice sidebar to share tab audio from the DJ's own click", async () => {
    mockCurrentUserId = DJ_USER_ID;
    const shareRequests = vi.fn();
    window.addEventListener("session-start-audio-share", shareRequests);
    await renderPage();

    fireEvent.click(await screen.findByRole("link", { name: "Open on YouTube to play" }));
    fireEvent.click(screen.getByRole("button", { name: "Share YouTube audio with the group" }));

    expect(shareRequests).toHaveBeenCalledOnce();
    window.removeEventListener("session-start-audio-share", shareRequests);
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
    mockSessionData = { ...withRound({ roundNumber: 1, turnNumber: 1 }), currentRoundNumber: 1 };
    await renderPage();

    expect(screen.getByText("First DJ")).toBeVisible();
    expect(screen.getByText("First turn")).toBeVisible();
  });

  it("doesn't replay the intro on later turns of the first round", async () => {
    mockSessionData = { ...withRound({ roundNumber: 1, turnNumber: 2 }), currentRoundNumber: 1 };
    await renderPage();

    expect(screen.queryByText("First DJ")).toBeNull();
    expect(screen.getByRole("heading", { name: "Round 1" })).toBeVisible();
  });

  it("shows the waiting state before the first round", async () => {
    mockSessionData = { ...MID_GAME_SESSION, currentRound: null };
    await renderPage();

    expect(await screen.findByText("Your timeline will appear when the round starts.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Your card. Choose a timeline position." })).toBeNull();
  });
});
