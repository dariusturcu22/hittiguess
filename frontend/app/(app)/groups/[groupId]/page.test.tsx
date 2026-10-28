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
const removeMemberMutate = vi.fn();
const promoteMemberMutate = vi.fn();
let lobbyMembers = [
  { id: 1, userId: 11, displayName: "Admin", isAdmin: true, isConnected: true },
  { id: 2, userId: 12, displayName: "Sam", isAdmin: false, isConnected: true },
];

// One router for the whole file: next/navigation's useRouter returns a stable instance,
// and a fresh object per render would change every callback that depends on it.
const routerMock = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
const lobbyState = vi.hoisted(() => ({ status: "OPEN", cachedSessionId: undefined as number | undefined }));
const lobbyQueryState = vi.hoisted(() => ({ isLoading: false, isError: false }));

vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
  usePathname: () => "/groups/1",
  useSearchParams: () => lobbySearchParams,
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetGroupQueryKey: (groupId: number) => [`/api/groups/${groupId}`],
  getGetActiveMembershipQueryKey: () => ["/api/users/me/active-group"],
  useGetGroup: () => ({
    data: lobbyQueryState.isLoading || lobbyQueryState.isError ? undefined : {
      id: 1,
      status: lobbyState.status,
      djMode: "ROTATING",
      winConditionCardCount: 5,
      joinCode: "ABCD",
      inviteCode: "invite-code",
      members: lobbyMembers,
      playlists: [],
    },
    isLoading: lobbyQueryState.isLoading,
    isError: lobbyQueryState.isError,
  }),
  useLeaveGroup: () => ({ mutate: leaveMutate, isPending: false }),
  useRemoveMember: () => ({ mutate: removeMemberMutate, isPending: false }),
  usePromoteMember1: () => ({ mutate: promoteMemberMutate, isPending: false }),
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
  useGetActiveSessionForGroup: () => ({ data: lobbyState.cachedSessionId === undefined ? undefined : { id: lobbyState.cachedSessionId } }),
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
  it("requires confirmation and refreshes admin permissions after transfer", async () => {
    promoteMemberMutate.mockImplementation((_arguments, options) => options.onSuccess({ id: 1, members: lobbyMembers.map((member) => ({ ...member, isAdmin: member.userId === 12 })) }));
    await renderPage();
    expect(screen.queryByRole("button", { name: "Make Admin group admin" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Make Sam group admin" }));
    expect(promoteMemberMutate).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole("button", { name: "Transfer admin" }));
    expect(promoteMemberMutate).toHaveBeenCalledWith({ groupId: 1, memberId: 2 }, expect.anything());
    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Group admin transferred"));
  });

  it("keeps the confirmation open and reports a failed transfer", async () => {
    promoteMemberMutate.mockImplementation((_arguments, options) => options.onError({ response: { data: { message: "Only the group admin can do that" } } }));
    await renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Make Sam group admin" }));
    fireEvent.click(await screen.findByRole("button", { name: "Transfer admin" }));
    await waitFor(() => expect(toastMocks.error).toHaveBeenCalledWith("Only the group admin can do that"));
    expect(screen.getByRole("button", { name: "Transfer admin" })).toBeVisible();
  });

  it("hides transfer controls from non-admin members", async () => {
    lobbyMembers = lobbyMembers.map((member) => ({ ...member, isAdmin: member.userId === 12 }));
    await renderPage();
    expect(screen.queryByRole("button", { name: /^Make .* group admin$/ })).not.toBeInTheDocument();
  });
  beforeEach(() => {
    lobbyState.status = "OPEN";
    lobbyState.cachedSessionId = undefined;
    routerMock.replace.mockClear();
    generateMutate.mockReset();
    startWithSongsMutate.mockReset();
    startSessionMutate.mockReset();
    updateSettingsMutate.mockReset();
    leaveMutate.mockReset();
    removeMemberMutate.mockReset();
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

  it("stays in the reopened lobby when a finished game's session is still cached", async () => {
    lobbyState.cachedSessionId = 44;
    await renderPage();

    expect(routerMock.replace).not.toHaveBeenCalledWith("/sessions/44");
  });

  it("joins the game in progress while the group is locked", async () => {
    lobbyState.status = "LOCKED";
    lobbyState.cachedSessionId = 44;
    await renderPage();

    expect(routerMock.replace).toHaveBeenCalledWith("/sessions/44");
  });

  it("saves the difficulty on Confirm without generating or starting", async () => {
    await renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
    expect(updateSettingsMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { difficultyTier: "MEDIUM" } }, expect.anything(),
    );
    expect(generateMutate).not.toHaveBeenCalled();
    expect(startWithSongsMutate).not.toHaveBeenCalled();
    expect(startSessionMutate).not.toHaveBeenCalled();
    expect(screen.queryByText("Song One")).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Start game" }));
    expect(startSessionMutate).toHaveBeenCalledWith({ groupId: 1 }, expect.anything());
  });

  it("saves the requested difficulty tier", async () => {
    await renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Hard" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
    expect(updateSettingsMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { difficultyTier: "HARD" } }, expect.anything(),
    );
    expect(generateMutate).not.toHaveBeenCalled();
  });

  it("opens the fullscreen custom picker with a back path to the tiers", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));

    expect(screen.getByRole("button", { name: "Back" })).toBeVisible();
    expect(screen.getByText("No playlists selected")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: /Party mix/ }));
    expect(screen.getByText("Chosen: Party mix (9 songs)")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByRole("button", { name: "Confirm" })).toBeVisible();
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

    expect(screen.getByText("Chosen: Party mix (9 songs)")).toBeVisible();

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
    expect(screen.getByRole("button", { name: "Confirm" })).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    expect(screen.queryByRole("button", { name: "Confirm" })).toBeNull();
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

  it("shows the server's reason next to the Start button when starting fails", async () => {
    startSessionMutate.mockImplementation((_args, options) =>
      options?.onError?.({ response: { data: { message: "Not enough internationally known verified songs: 3 available, 10 needed" } } }),
    );
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Start game" }));

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Could not start the game: Not enough internationally known verified songs: 3 available, 10 needed",
      ),
    );
  });

  it("lets the admin remove another member after confirming", async () => {
    await renderPage();

    expect(screen.queryByRole("button", { name: "Remove Admin" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Remove Sam" }));

    expect(await screen.findByText("Remove Sam?")).toBeVisible();
    expect(removeMemberMutate).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Remove" }));

    expect(removeMemberMutate).toHaveBeenCalledWith({ groupId: 1, memberId: 2 }, expect.anything());
  });

  it("toasts the server's reason when removing a member fails", async () => {
    removeMemberMutate.mockImplementation((_args, options) =>
      options?.onError?.({ response: { data: { message: "Members can't be removed while a game session is running" } } }),
    );
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Remove Sam" }));
    fireEvent.click(await screen.findByRole("button", { name: "Remove" }));

    await waitFor(() =>
      expect(toastMocks.error).toHaveBeenCalledWith("Members can't be removed while a game session is running"),
    );
  });

  it("shows no remove controls to a member who isn't the admin", async () => {
    lobbyMembers = [
      { id: 1, userId: 11, displayName: "Admin", isAdmin: false, isConnected: true },
      { id: 2, userId: 12, displayName: "Sam", isAdmin: true, isConnected: true },
    ];
    await renderPage();

    expect(screen.queryByRole("button", { name: /^Remove / })).not.toBeInTheDocument();
  });

  it("anchors the settings panel in the footer wrapper like the chat", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Settings" }));
    const settingsPanel = screen.getByLabelText("Group settings");
    const footerWrapper = settingsPanel.parentElement;

    expect(settingsPanel.className).toContain("bottom-full");
    expect(settingsPanel.className).toContain("mb-3");
    expect(settingsPanel.className).toContain("sm:left-[228px]");
    expect(footerWrapper?.className).toContain("relative");
    expect(footerWrapper).toContainElement(screen.getByRole("button", { name: "Leave lobby" }));
  });

  it("renders the start options as one compact row with the tier confirmation", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Choose playlists" }));

    const startOptions = screen.getByLabelText("Start options");
    const tierRow = screen.getByRole("button", { name: "Easy" }).parentElement;

    expect(startOptions.className).toContain("w-auto");
    expect(startOptions.className).not.toContain("w-[480px]");
    expect(screen.queryByText("Songs are selected when the session starts.")).toBeNull();
    expect(tierRow).toContainElement(screen.getByRole("button", { name: "Custom" }));
    expect(tierRow).toContainElement(screen.getByRole("button", { name: "Confirm" }));

    fireEvent.click(screen.getByRole("button", { name: "Easy" }));
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));

    expect(updateSettingsMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { difficultyTier: "EASY" } }, expect.anything(),
    );
  });
});

describe("GroupLobbyPage viewport layout", () => {
  beforeEach(() => {
    lobbyQueryState.isLoading = false;
    lobbyQueryState.isError = false;
    lobbyState.status = "OPEN";
    lobbyState.cachedSessionId = undefined;
    lobbyMembers = [
      { id: 1, userId: 11, displayName: "Admin", isAdmin: true, isConnected: true },
      { id: 2, userId: 12, displayName: "Sam", isAdmin: false, isConnected: true },
    ];
  });

  it("pins the lobby root to the viewport without page scrolling", async () => {
    await renderPage();
    const root = screen.getByRole("main");

    expect(root.className).toContain("h-full");
    expect(root.className).toContain("overflow-hidden");
    expect(root.className).not.toContain("min-h-[720px]");
  });

  it("centres the loading state inside the viewport", async () => {
    lobbyQueryState.isLoading = true;
    await renderPage();

    const root = screen.getByRole("main");
    expect(root.className).toContain("h-full");
    expect(root.className).toContain("overflow-hidden");
    expect(root.className).not.toContain("min-h-[720px]");

    const loadingStage = screen.getByLabelText("Loading group").parentElement;
    expect(loadingStage?.className).toContain("min-h-0");
    expect(loadingStage?.className).not.toContain("min-h-[480px]");
  });

  it("centres the error state inside the viewport", async () => {
    lobbyQueryState.isError = true;
    await renderPage();

    const root = screen.getByRole("main");
    expect(root.className).toContain("h-full");
    expect(root.className).toContain("overflow-hidden");
    expect(root.className).not.toContain("min-h-[720px]");
    expect(screen.getByRole("heading", { name: "Group unavailable" })).toBeVisible();
  });

  it("lets the orbit stage fill the height and keeps the footer outside it", async () => {
    await renderPage();
    const stage = screen.getByText("Enter code to join").closest("section");

    expect(stage?.className).toContain("flex-1");
    expect(stage?.className).toContain("min-h-0");
    expect(stage?.className).toContain("overflow-hidden");
    expect(stage?.className).not.toContain("min-h-[480px]");
    expect(stage?.className).not.toContain("overflow-y-auto");
    expect(stage).not.toContainElement(screen.getByRole("button", { name: "Leave lobby" }));
  });

  it("keeps the orbit members, their crowns, and the join code at their design sizes", async () => {
    await renderPage();

    const joinCode = screen.getByText("ABCD");
    expect(joinCode.className).toContain("sm:text-[76px]");
    expect(joinCode.className).not.toContain("short:");

    const memberBlock = screen.getByText(/^Admin/).parentElement;
    const avatar = memberBlock?.querySelector(".avatar-initial");
    expect(avatar?.className).toContain("sm:size-[108px]");
    expect(avatar?.className).not.toContain("short:");

    const crown = memberBlock?.querySelector("svg.text-warning");
    expect(crown?.getAttribute("class")).toContain("sm:size-7");
    expect(crown?.getAttribute("class")).not.toContain("short:");
  });
});
