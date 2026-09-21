import { act, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import GroupLobbyPage from "./page";

const GROUP_PARAMS = Promise.resolve({ groupId: "1" });

const generateMutate = vi.fn();
const startWithSongsMutate = vi.fn();
const startCustomMutate = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
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
      members: [
        { id: 1, userId: 11, displayName: "Admin", isAdmin: true, isConnected: true },
        { id: 2, userId: 12, displayName: "Sam", isAdmin: false, isConnected: true },
      ],
      playlists: [],
    },
    isLoading: false,
    isError: false,
  }),
  useLeaveGroup: () => ({ mutate: vi.fn(), isPending: false }),
  useStartGameSession: () => ({ mutate: vi.fn(), isPending: false }),
  useUpdateGroupSettings: () => ({ mutate: vi.fn(), isPending: false }),
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

vi.mock("@/hooks/use-difficulty-session-start", () => ({
  useGenerateDifficultySet: () => ({ mutate: generateMutate, isPending: false }),
  useStartSessionWithSongs: () => ({ mutate: startWithSongsMutate, isPending: false }),
  useStartCustomSession: () => ({ mutate: startCustomMutate, isPending: false }),
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
    startCustomMutate.mockReset();
    generateMutate.mockImplementation((_args, options) => options?.onSuccess?.(previews));
    startWithSongsMutate.mockImplementation((_args, options) => options?.onSuccess?.({}));
    startCustomMutate.mockImplementation((_args, options) => options?.onSuccess?.({}));
  });

  it("generates a difficulty set for review and confirms it into a start", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Custom start" }));
    fireEvent.click(screen.getByRole("button", { name: "Generate" }));

    expect(generateMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { tier: "MEDIUM", targetCardCount: 7 } },
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

  it("starts a custom session from a picked playlist", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Custom start" }));
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));
    fireEvent.click(screen.getByRole("button", { name: /Party mix/ }));
    fireEvent.click(screen.getByRole("button", { name: "Start from playlist" }));

    expect(startCustomMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { playlistId: 21 } },
      expect.anything(),
    );
  });

  it("starts a custom session from a pasted playlist link", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Custom start" }));
    fireEvent.click(screen.getByRole("button", { name: "Custom" }));
    fireEvent.change(screen.getByPlaceholderText("YouTube playlist link or id"), {
      target: { value: "https://youtube.com/playlist?list=abc" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Start" }));

    expect(startCustomMutate).toHaveBeenCalledWith(
      { groupId: 1, data: { playlistLink: "https://youtube.com/playlist?list=abc" } },
      expect.anything(),
    );
  });
});
