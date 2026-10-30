import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

import GameSessionPage from "./page";

let roundEventHandler: ((event: { type: string; payload?: Record<string, unknown> }) => void) | undefined;
type MockGuessResult = { roundId?: number; artistCorrect?: boolean; titleCorrect?: boolean; tokenAwarded?: boolean; state?: Record<string, unknown> };
let guessResultHandler: ((result: MockGuessResult) => void) | undefined;
let sessionEndedHandler: ((ended: { groupId?: number }) => void) | undefined;
let mockGuessState: Record<string, unknown> | undefined;
const routerReplace = vi.fn();
const routerPush = vi.fn();
const leaveSessionMutate = vi.fn();
const leaveGroupMutate = vi.fn();
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

const shareSong = vi.fn(async () => true);
const resumePlayback = vi.fn();
const setSongVolume = vi.fn();
const useAudioLevelsSpy = vi.fn<(stream: MediaStream | null) => number[]>(() => []);
let mockSongVolume = 100;
let mockIsCapturing = false;
let mockShareErrorMessage: string | null = null;
let mockIsPlaybackBlocked = false;
let mockVisualizerStream: MediaStream | null = null;

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
let mockSessionIsLoading = false;
let mockMembership: { id: number } | undefined = { id: 2 };
let mockMembershipIsLoading = false;
let mockCurrentUserId = ACTIVE_USER_ID;

function withRound(round: Record<string, unknown>): MockGameSession {
  return { ...MID_GAME_SESSION, currentRound: { ...MID_GAME_SESSION.currentRound, ...round } };
}

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: routerReplace, push: routerPush }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ setQueryData: vi.fn(), getQueryData: () => mockGuessState, invalidateQueries: vi.fn() }),
}));

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetCurrentRoundLinkOut: (_sessionId: number, options: { query: { enabled: boolean } }) => ({
    data: options.query.enabled ? { watchUrl: WATCH_URL, artist: "Dua Lipa", title: "Levitating", releaseYear: 2020, color: "74c7ec" } : undefined,
    isLoading: false,
  }),
  useGetSession: () => ({
    data: mockSessionData,
    isLoading: mockSessionIsLoading,
    isError: !mockSessionData,
  }),
  useGetGuessState: () => ({ data: mockGuessState }),
  useLeaveSession: () => ({ mutate: leaveSessionMutate, isPending: false }),
  getGetGuessStateQueryKey: (sessionId: number) => [`/api/sessions/${sessionId}/guess-state`],
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetActiveMembership: () => ({ data: mockMembership, isLoading: mockMembershipIsLoading }),
  getGetActiveMembershipQueryKey: () => ["/api/groups/active"],
  useLeaveGroup: () => ({ mutate: leaveGroupMutate, isPending: false }),
  useGetGroup: () => ({ data: { playlists: [{ id: 5, name: "Midnight Radio", color: "cba6f7", songCount: 32, previewYoutubeIds: [] }] } }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({ data: { id: mockCurrentUserId } }),
}));

vi.mock("@/hooks/use-group-realtime", () => ({
  useGroupRealtime: () => ({ connectionState: "connected", sendChat: vi.fn(() => true) }),
}));

vi.mock("@/hooks/use-song-mesh", () => ({
  useSongMesh: () => ({
    shareSong,
    isCapturing: mockIsCapturing,
    shareErrorMessage: mockShareErrorMessage,
    isPlaybackBlocked: mockIsPlaybackBlocked,
    resumePlayback,
    visualizerStream: mockVisualizerStream,
    isSignalConnected: true,
  }),
}));

vi.mock("@/hooks/use-audio-levels", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/use-audio-levels")>();
  return {
    ...actual,
    useAudioLevels: (stream: MediaStream | null) => useAudioLevelsSpy(stream),
  };
});

vi.mock("@/hooks/use-audio-devices", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/hooks/use-audio-devices")>();
  return {
    ...actual,
    useAudioDevices: () => ({ songVolume: mockSongVolume, setSongVolume }),
  };
});

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

vi.mock("@/components/gameplay/game-pieces", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/components/gameplay/game-pieces")>();
  const { createElement } = await import("react");
  type MysteryCardProps = Parameters<typeof actual.MysteryCard>[0];
  return {
    ...actual,
    MysteryCard: ({ levels, ...rest }: MysteryCardProps) => createElement(
      "div",
      { "data-audio-levels": levels?.join(",") ?? "" },
      createElement(actual.MysteryCard, { ...rest, levels }),
    ),
  };
});

const TIMELINE_TRACK_BOUNDS = { left: 100, top: 200, width: 1000, height: 200, right: 1100, bottom: 400, x: 100, y: 200, toJSON: () => ({}) };

// jsdom reports no layout, so the card size sits at its minimum and the dragged card floats 0.7 card sizes above the pointer.
const MINIMUM_CARD_SIZE_PIXELS = 132;
const DRAGGED_CARD_LIFT_PIXELS = MINIMUM_CARD_SIZE_PIXELS * 0.7;
const POINTER_START = { clientX: 600, clientY: 350 };
const POINTER_MOVED = { clientX: 700, clientY: 400 };
const EXPECTED_START_Y = (POINTER_START.clientY - DRAGGED_CARD_LIFT_PIXELS - TIMELINE_TRACK_BOUNDS.top) / TIMELINE_TRACK_BOUNDS.height;
const EXPECTED_MOVED_X = (POINTER_MOVED.clientX - TIMELINE_TRACK_BOUNDS.left) / TIMELINE_TRACK_BOUNDS.width;
const EXPECTED_MOVED_Y = (POINTER_MOVED.clientY - DRAGGED_CARD_LIFT_PIXELS - TIMELINE_TRACK_BOUNDS.top) / TIMELINE_TRACK_BOUNDS.height;
const POSITION_PRECISION_DIGITS = 4;
const EMPTY_BOUNDS = { left: 0, top: 0, width: 0, height: 0, right: 0, bottom: 0, x: 0, y: 0, toJSON: () => ({}) };

function mockTimelineTrackBounds() {
  return vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
    if (this.matches("[data-timeline-track]")) return TIMELINE_TRACK_BOUNDS as DOMRect;
    return EMPTY_BOUNDS as DOMRect;
  });
}

async function renderPage() {
  await act(async () => {
    render(<GameSessionPage params={SESSION_PARAMS} />);
  });
}

describe("GameSessionPage", () => {
  beforeAll(() => {
    vi.spyOn(window.navigator, "userAgent", "get").mockReturnValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36");
  });

  beforeEach(() => {
    placeCard.mockClear();
    previewPlacement.mockClear();
    submitGuess.mockClear();
    skipBetting.mockClear();
    shareSong.mockClear();
    resumePlayback.mockClear();
    setSongVolume.mockClear();
    useAudioLevelsSpy.mockClear();
    roundEventHandler = undefined;
    guessResultHandler = undefined;
    sessionEndedHandler = undefined;
    mockGuessState = undefined;
    routerReplace.mockClear();
    mockSessionData = MID_GAME_SESSION;
    mockSessionIsLoading = false;
    mockMembership = { id: 2 };
    mockMembershipIsLoading = false;
    mockCurrentUserId = ACTIVE_USER_ID;
    mockSongVolume = 100;
    mockIsCapturing = false;
    mockShareErrorMessage = null;
    mockIsPlaybackBlocked = false;
    mockVisualizerStream = null;
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
    await waitFor(() => expect(previewPlacement).toHaveBeenLastCalledWith({ position: 1, phase: "DROPPED" }));

    fireEvent.click(screen.getByRole("button", { name: "Lock in answer" }));
    expect(placeCard).toHaveBeenCalledWith(1);
  });

  it("publishes the dragged card position as fractions of the timeline box", async () => {
    const rectSpy = mockTimelineTrackBounds();
    try {
      await renderPage();

      fireEvent.pointerDown(screen.getByRole("button", { name: "Your card. Choose a timeline position." }), POINTER_START);
      expect(previewPlacement).toHaveBeenCalledWith({ position: null, x: 0.5, y: expect.closeTo(EXPECTED_START_Y, POSITION_PRECISION_DIGITS), phase: "DRAGGING" });

      fireEvent.pointerMove(window, POINTER_MOVED);
      await waitFor(() => expect(previewPlacement).toHaveBeenLastCalledWith(expect.objectContaining({ x: expect.closeTo(EXPECTED_MOVED_X, POSITION_PRECISION_DIGITS), y: expect.closeTo(EXPECTED_MOVED_Y, POSITION_PRECISION_DIGITS), phase: "DRAGGING" })));
    } finally {
      rectSpy.mockRestore();
    }
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

  it("animates a new token award once without animating a partial correct guess", async () => {
    await renderPage();
    const view = { container: document.body };
    await screen.findByText("Your turn");
    const RESULT_ROUND_ID = 30;
    act(() => guessResultHandler?.({ roundId: RESULT_ROUND_ID, artistCorrect: true, state: { roundId: RESULT_ROUND_ID, tokenEarned: false } }));
    expect(view.container.querySelector(".token-earned-drop")).toBeNull();
    act(() => guessResultHandler?.({ roundId: RESULT_ROUND_ID, titleCorrect: true, tokenAwarded: true, state: { roundId: RESULT_ROUND_ID, tokenEarned: true } }));
    const earnedCoin = view.container.querySelector(".token-earned-drop");
    expect(earnedCoin).not.toBeNull();
    act(() => guessResultHandler?.({ roundId: RESULT_ROUND_ID, titleCorrect: true, tokenAwarded: true, state: { roundId: RESULT_ROUND_ID, tokenEarned: true } }));
    expect(view.container.querySelector(".token-earned-drop")).toBe(earnedCoin);
    act(() => guessResultHandler?.({ roundId: RESULT_ROUND_ID, artistCorrect: false, titleCorrect: false }));
    expect(view.container.querySelector(".incorrect-guess-feedback")).not.toBeNull();
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

  it("shows a loader, not the ended screen, while a purged session redirects to its group's results", async () => {
    mockSessionData = null;
    await renderPage();

    expect(routerReplace).toHaveBeenCalledWith("/sessions/1/results?group=2");
    expect(screen.getByLabelText("Loading game session")).toBeVisible();
    expect(screen.queryByText("This game has ended.")).toBeNull();
  });

  it("shows the ended screen when a purged session has no group to return to", async () => {
    mockSessionData = null;
    mockMembership = undefined;
    await renderPage();

    expect(screen.getByText("This game has ended.")).toBeVisible();
    expect(screen.getByRole("link", { name: "Back to playlists" })).toHaveAttribute("href", "/playlists");
    expect(routerReplace).not.toHaveBeenCalled();
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

  it("asks the DJ to share audio before the YouTube link appears", async () => {
    mockCurrentUserId = DJ_USER_ID;
    await renderPage();

    expect(screen.queryByRole("link", { name: "Open on YouTube to play" })).toBeNull();
    expect(screen.getByText("Levitating")).toBeVisible();
    expect(screen.getByText("You're the DJ")).toBeVisible();
    expect(screen.queryByPlaceholderText("Guess the artist")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Share your audio first" }));

    expect(shareSong).toHaveBeenCalledOnce();
  });

  it("shows a failed audio share with a retry that shares again", async () => {
    mockCurrentUserId = DJ_USER_ID;
    mockShareErrorMessage = "No audio was shared.";
    await renderPage();

    expect(screen.getByRole("alert")).toHaveTextContent("No audio was shared.");
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(shareSong).toHaveBeenCalledOnce();
  });

  it("shows the DJ a real YouTube link that opens in a new tab once the audio is shared", async () => {
    mockCurrentUserId = DJ_USER_ID;
    mockIsCapturing = true;
    await renderPage();

    const link = await screen.findByRole("link", { name: "Open on YouTube to play" });
    expect(link).toHaveAttribute("href", WATCH_URL);
    expect(link).toHaveAttribute("target", "_blank");
    expect(screen.getByText("Sharing your audio with the group")).toBeVisible();
  });

  it("traces the song hook's visualizer stream into the audio levels", async () => {
    const visualizerStream = { id: "song-stream" } as unknown as MediaStream;
    mockVisualizerStream = visualizerStream;
    await renderPage();

    expect(useAudioLevelsSpy).toHaveBeenCalledWith(visualizerStream);
  });

  it("offers a click to enable sound while the song playback is blocked", async () => {
    mockIsPlaybackBlocked = true;
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Click to enable sound" }));

    expect(resumePlayback).toHaveBeenCalledOnce();
  });

  it("changes the song volume from the page header", async () => {
    await renderPage();

    fireEvent.change(screen.getByRole("slider", { name: "Song volume" }), { target: { value: "40" } });

    expect(setSongVolume).toHaveBeenCalledWith(40);
  });

  it("tells a player on a browser that cannot capture audio that they are skipped as DJ", async () => {
    mockCurrentUserId = ACTIVE_USER_ID;
    const userAgent = vi.spyOn(window.navigator, "userAgent", "get").mockReturnValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
    await renderPage();

    expect(screen.getByText(/This browser can't share the song's audio, so you are skipped as DJ/)).toBeVisible();
    userAgent.mockReturnValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36");
  });

  it("leaves the session, then the group, and goes home after confirming Leave game", async () => {
    mockCurrentUserId = SPECTATOR_USER_ID;
    leaveSessionMutate.mockImplementation((_variables, options) => options.onSuccess());
    leaveGroupMutate.mockImplementation((_variables, options) => options.onSuccess());
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Leave game" }));
    expect(leaveSessionMutate).not.toHaveBeenCalled();
    fireEvent.click(within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Leave game" }));

    expect(leaveSessionMutate).toHaveBeenCalledWith({ sessionId: 1 }, expect.anything());
    expect(leaveGroupMutate).toHaveBeenCalledWith({ groupId: 2 }, expect.anything());
    expect(routerPush).toHaveBeenCalledWith("/playlists");
  });

  it("keeps the resting card preview out of the DJ instructions", async () => {
    mockCurrentUserId = DJ_USER_ID;
    await renderPage();

    act(() => {
      roundEventHandler?.({ type: "PLACEMENT_PREVIEW", payload: { roundId: 30, activePlayerId: 7, position: null, x: 0.4, y: -0.5, phase: "IDLE" } });
    });
    expect(document.querySelector("[data-spectator-card-preview]")).toBeNull();

    act(() => {
      roundEventHandler?.({ type: "PLACEMENT_PREVIEW", payload: { roundId: 30, activePlayerId: 7, position: 1, x: 0.6, y: 0.2, phase: "DRAGGING" } });
    });
    expect(document.querySelector("[data-spectator-card-preview]")).toHaveAttribute("data-phase", "DRAGGING");
  });

  it("shows spectators the live placement preview", async () => {
    mockCurrentUserId = SPECTATOR_USER_ID;
    useAudioLevelsSpy.mockReturnValue([0.1, 0.2, 0.3, 0.4, 0.5]);
    await renderPage();

    expect(screen.getByText("Watching live. It's not your turn to place a card.")).toBeVisible();
    expect(document.querySelector("[data-spectator-card-preview]")).toBeNull();

    act(() => {
      roundEventHandler?.({ type: "PLACEMENT_PREVIEW", payload: { roundId: 30, activePlayerId: 7, position: null, x: 0.4, y: -0.5, phase: "IDLE" } });
    });

    const idleCard = document.querySelector<HTMLElement>("[data-spectator-card-preview]");
    expect(idleCard).toHaveAttribute("data-phase", "IDLE");
    expect(idleCard).toHaveStyle({ left: "40%", top: "-50%" });
    expect(idleCard?.querySelector(".gameplay-card-float")).not.toBeNull();
    expect(idleCard?.querySelector(".gameplay-wobble")).toBeNull();
    expect(idleCard?.querySelector("[data-audio-levels]")).toHaveAttribute("data-audio-levels", "0.1,0.2,0.3,0.4,0.5");
    expect(screen.queryByRole("button", { name: "Your card. Choose a timeline position." })).toBeNull();

    act(() => {
      roundEventHandler?.({ type: "PLACEMENT_PREVIEW", payload: { roundId: 30, activePlayerId: 7, position: 1, x: 0.6, y: 0.2, phase: "DRAGGING" } });
    });

    const draggingCard = document.querySelector<HTMLElement>("[data-spectator-card-preview]");
    expect(draggingCard).toHaveAttribute("data-phase", "DRAGGING");
    expect(draggingCard).toHaveStyle({ left: "60%", top: "20%" });
    expect(draggingCard?.querySelector(".gameplay-wobble")).not.toBeNull();
    expect(screen.getByRole("region", { name: "Game stage" }).querySelector(".gameplay-gap-pulse")).not.toBeNull();

    act(() => {
      roundEventHandler?.({ type: "PLACEMENT_PREVIEW", payload: { roundId: 30, activePlayerId: 7, position: 1, phase: "DROPPED" } });
    });

    expect(document.querySelector("[data-spectator-card-preview]")).toBeNull();
    expect(screen.getByRole("region", { name: "Game stage" }).querySelector("[data-timeline-focus] [data-audio-levels]")).toHaveAttribute("data-audio-levels", "0.1,0.2,0.3,0.4,0.5");
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

describe("GameSessionPage viewport layout", () => {
  beforeAll(() => {
    vi.spyOn(window.navigator, "userAgent", "get").mockReturnValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36");
  });

  beforeEach(() => {
    mockSessionData = MID_GAME_SESSION;
    mockSessionIsLoading = false;
    mockCurrentUserId = ACTIVE_USER_ID;
    mockGuessState = undefined;
    window.sessionStorage.clear();
  });

  it("pins the game root to the viewport without page scrolling", async () => {
    await renderPage();
    const root = screen.getByRole("main");

    expect(root.className).toContain("h-full");
    expect(root.className).toContain("overflow-hidden");
    expect(root.className).not.toContain("min-h-full");
    expect(root.className).not.toContain("min-h-[680px]");
    expect(root.closest("[data-game-stage-host]")).toBeNull();
  });

  it("centres the loading state inside the viewport", async () => {
    mockSessionIsLoading = true;
    await renderPage();

    const root = screen.getByRole("main");
    expect(root.className).toContain("h-full");
    expect(root.className).toContain("overflow-hidden");
    expect(root.className).not.toContain("min-h-[720px]");
    expect(screen.getByLabelText("Loading game session")).toBeVisible();
  });

  it("centres the ended state inside the viewport", async () => {
    mockSessionData = null;
    await renderPage();

    const root = screen.getByRole("main");
    expect(root.className).toContain("h-full");
    expect(root.className).toContain("overflow-hidden");
    expect(root.className).not.toContain("min-h-[720px]");
  });

  it("flows the timeline between its own rows instead of offsetting it from the middle", async () => {
    const view = render(<GameSessionPage params={SESSION_PARAMS} />);
    await screen.findByText("Your turn");

    const stage = screen.getByRole("region", { name: "Game stage" });
    expect(stage.className).toContain("grid-rows-[1fr_auto_1fr]");
    expect(stage.className).toContain("overflow-y-auto");
    expect(view.container.querySelector('[class*="calc(50%"]')).toBeNull();
  });

  it("anchors the header and footer to the corners of the screen", async () => {
    await renderPage();

    const root = screen.getByRole("main");
    expect(root.className).toContain("env(safe-area-inset-left)");
    expect(root.className).toContain("env(safe-area-inset-top)");
    expect(root.className).toContain("env(safe-area-inset-right)");
    expect(root.className).toContain("env(safe-area-inset-bottom)");
    expect(root.querySelector("header")?.className).toContain("justify-between");
    expect(root.querySelector("footer")?.className).toContain("justify-between");
  });

  it("sizes the cards from the shared card size and keeps the guess pills and token pile at their design sizes", async () => {
    await renderPage();

    const card = screen.getByText("Torn").closest("article");
    expect(card?.className).toContain("size-[var(--card-size,168px)]");
    expect(card?.className).not.toContain("short:");
    expect(screen.getByRole("main").style.getPropertyValue("--card-size")).toBe(`${MINIMUM_CARD_SIZE_PIXELS}px`);

    const guessPill = screen.getByRole("textbox", { name: "Guess the artist" }).closest("label");
    expect(guessPill?.className).toContain("h-[52px]");
    expect(guessPill?.className).not.toContain("short:");

    const tokenPile = screen.getByLabelText("Your tokens: 0");
    expect(tokenPile.className).toContain("gap-2.5");
    expect(tokenPile.className).not.toContain("short:");
  });

  it("keeps the round intro at its design size", async () => {
    mockSessionData = { ...withRound({ roundNumber: 1, turnNumber: 1 }), currentRoundNumber: 1 };
    const view = render(<GameSessionPage params={SESSION_PARAMS} />);
    await screen.findByText("First DJ");

    const intro = view.container.querySelector('[class*="absolute inset-0 z-40"]');
    expect(intro?.className).toContain("overflow-hidden");
    expect(intro?.className).not.toContain("short:");

    const ring = view.container.querySelector('[class*="size-[140px]"]');
    expect(ring?.className).toContain("size-[140px]");
    expect(ring?.className).not.toContain("short:");
  });
});
