import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

const toastMocks = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

vi.mock("sonner", () => ({
  toast: toastMocks,
}));

import GroupLobbyPage from "./page";

const GROUP_PARAMS = Promise.resolve({ groupId: "1" });

const generateMutate = vi.fn();
const startWithSongsMutate = vi.fn();
const startSessionMutate = vi.fn();
let lobbySearchParams = new URLSearchParams();

const updateSettingsMutate = vi.fn();
const leaveMutate = vi.fn();
let lobbyMembers = [
  { id: 1, userId: 11, displayName: "Admin", isAdmin: true, isConnected: true },
  { id: 2, userId: 12, displayName: "Sam", isAdmin: false, isConnected: true },
];

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => "/groups/1",
  useSearchParams: () => lobbySearchParams,
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetGroupQueryKey: (groupId: number) => [`/api/groups/${groupId}`],
  getGetActiveMembershipQueryKey: () => ["/api/users/me/active-group"],
  useGetGroup: () => ({
    data: {
      id: 1,
      status: "OPEN",
      djMode: "ROTATING",
      winConditionCardCount: 5,
      joinCode: "ABCD",
      inviteCode: "invite-code",
      members: lobbyMembers,
      playlists: [],
    },
    isLoading: false,
    isError: false,
  }),
  useLeaveGroup: () => ({ mutate: leaveMutate, isPending: false }),
  useStartGameSession: () => ({ mutate: startSessionMutate, isPending: false }),
  useUpdateGroupSettings: () => ({ mutate: updateSettingsMutate, isPending: false }),
  useGenerateDifficultySet: () => ({ mutate: generateMutate, isPending: false }),
  useStartSessionWithSongs: () => ({ mutate: startWithSongsMutate, isPending: false }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({ data: { id: 11 } }),
  useGetUserPlaylists: () => ({
    data: [{ id: 21, name: "Party mix", color: "cba6f7", songCount: 9, previewYoutubeIds: [] }],
  }),
}));

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetActiveSessionForGroup: () => ({ data: undefined }),
}));

vi.mock("@/hooks/use-group-realtime", () => ({
  useGroupRealtime: () => ({ connectionState: "connected", sendChat: vi.fn(() => true) }),
}));

vi.mock("@/components/group-chat-overlay", () => ({
  GroupChatOverlay: () => <aside aria-label="Chat" />,
}));

const previews = [
  { id: 31, title: "Song One", artists: ["Artist One"], releaseYear: 1999 },
  { id: 32, title: "Song Two", artists: ["Artist Two"], releaseYear: 2001 },
];

async function renderPage() {
  const queryClient = new QueryClient();
  await act(async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <GroupLobbyPage params={GROUP_PARAMS} />
      </QueryClientProvider>,
    );
  });
}

describe("GroupLobbyPage start options", () => {
  beforeEach(() => {
    generateMutate.mockReset();
    startWithSongsMutate.mockReset();
    startSessionMutate.mockReset();
    updateSettingsMutate.mockReset();
    leaveMutate.mockReset();
    lobbySearchParams = new URLSearchParams();
    lobbyMembers = [
      { id: 1, userId: 11, displayName: "Admin", isAdmin: true, isConnected: true },
      { id: 2, userId: 12, displayName: "Sam", isAdmin: false, isConnected: true },
    ];
    generateMutate.mockImplementation((_args, options) => options?.onSuccess?.(previews));
    startWithSongsMutate.mockImplementation((_args, options) => options?.onSuccess?.({}));
    updateSettingsMutate.mockImplementation((_args, options) => options?.onSuccess?.({}));
    toastMocks.success.mockReset();
    toastMocks.error.mockReset();
  });

  it("generates a difficulty set for review and confirms it into a start", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    expect(generateMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { tier: "MEDIUM", targetCardCount: 30 } },
      expect.anything(),
    );
    expect(screen.getByText("Song One")).toBeVisible();
    expect(screen.getByText("1999", { exact: true })).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Confirm and start" }));

    expect(startWithSongsMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { songIds: [31, 32] } },
      expect.anything(),
    );
  });

  it("switches difficulty tiers inside the chip popup", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Hard" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    expect(generateMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { tier: "HARD", targetCardCount: 30 } },
      expect.anything(),
    );
  });

  it("opens the fullscreen custom picker with a back path to the tiers", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));

    expect(screen.getByRole("button", { name: "Back" })).toBeVisible();
    expect(screen.getByText("No playlists selected")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: /Party mix/ }));
    expect(screen.getByText("Chosen: Party mix")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByRole("button", { name: "Generate" })).toBeVisible();
  });

  it("confirms a custom multi-playlist selection without starting", async () => {
    startSessionMutate.mockImplementation((_args, options) => options?.onSuccess?.({}));
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));
    fireEvent.click(screen.getByRole("button", { name: /Party mix/ }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    expect(updateSettingsMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { playlistIds: [21] } },
      expect.anything(),
    );
    expect(startSessionMutate).not.toHaveBeenCalled();
  });

  it("highlights Custom once a custom selection is active", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));
    fireEvent.click(screen.getByRole("button", { name: /Party mix/ }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));

    expect(screen.getByRole("button", { name: "Custom" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Medium" })).toHaveAttribute("aria-pressed", "false");
  });

  it("closes the custom picker on outside click", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));
    expect(screen.getByRole("button", { name: "Confirm" })).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Close playlist selection" }));

    expect(screen.queryByRole("button", { name: "Confirm" })).toBeNull();
  });

  it("opens the custom picker with the playlist preselected from the link", async () => {
    lobbySearchParams = new URLSearchParams("playlist=21");
    await renderPage();

    expect(screen.getByText("Chosen: Party mix")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    expect(updateSettingsMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { playlistIds: [21] } },
      expect.anything(),
    );
  });

  it("saves a fixed DJ choice from settings", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    fireEvent.click(screen.getByRole("combobox", { name: "DJ mode" }));
    fireEvent.click(screen.getByRole("option", { name: "Fixed" }));
    fireEvent.click(screen.getByRole("combobox", { name: "Fixed DJ" }));
    fireEvent.click(screen.getByRole("option", { name: "Sam" }));
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(updateSettingsMutate).toHaveBeenCalledWith(
      {
        groupId: 1,
        data: { djMode: "FIXED", winConditionCardCount: 5, fixedDjMemberId: 2 },
      },
      expect.anything(),
    );
    expect(toastMocks.success).toHaveBeenCalledWith("Settings saved");
  });

  it("shows the min-players popup only when starting below the minimum", async () => {
    lobbyMembers = [
      { id: 1, userId: 11, displayName: "Admin", isAdmin: true, isConnected: true },
    ];
    await renderPage();

    expect(screen.queryByText("Not enough players")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Start game" }));

    expect(screen.getByText("Not enough players")).toBeVisible();
    expect(startSessionMutate).not.toHaveBeenCalled();
  });

  it("closes settings on outside click", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    expect(screen.getByText("Cards to win")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Close settings" }));

    expect(screen.queryByText("Cards to win")).toBeNull();
  });

  it("keeps only one popup open at a time", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    expect(screen.getByText("Cards to win")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    expect(screen.queryByText("Cards to win")).toBeNull();
    expect(screen.getByRole("button", { name: "Generate" })).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    expect(screen.queryByRole("button", { name: "Generate" })).toBeNull();
    expect(screen.getByText("Cards to win")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Chat" }));
    expect(screen.queryByText("Cards to win")).toBeNull();
    expect(screen.getByLabelText("Chat")).toBeVisible();
  });

  it("closes every popup when the session starts", async () => {
    startSessionMutate.mockImplementation((_args, options) => options?.onSuccess?.({}));
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    expect(screen.getByText("Cards to win")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Start game" }));

    expect(startSessionMutate).toHaveBeenCalled();
    expect(screen.queryByText("Cards to win")).toBeNull();
  });

  it("asks for confirmation before leaving the lobby", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Leave lobby" }));

    expect(await screen.findByText("Leave the lobby?")).toBeVisible();
    expect(leaveMutate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Leave" }));

    expect(leaveMutate).toHaveBeenCalledWith({ groupId: 1 }, expect.anything());
  });

  it("toasts an error when leaving the lobby fails", async () => {
    leaveMutate.mockImplementation((_args, options) => options?.onError?.(new Error("network down")));
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Leave lobby" }));
    fireEvent.click(screen.getByRole("button", { name: "Leave" }));

    await waitFor(() => expect(toastMocks.error).toHaveBeenCalled());
  });

  it("toasts an error when starting the game fails", async () => {
    startSessionMutate.mockImplementation((_args, options) => options?.onError?.(new Error("network down")));
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Start game" }));

    await waitFor(() => expect(toastMocks.error).toHaveBeenCalled());
  });
});
