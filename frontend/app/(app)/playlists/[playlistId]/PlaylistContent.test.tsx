import { act, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import PlaylistContent, { buildInviteMessage } from "./PlaylistContent";

const routerPush = vi.fn();
const leaveMutate = vi.fn();
let membership: { id: number } | undefined = { id: 4 };
let playlistSongs: Array<{ id: number; title: string; artists: Array<{ name: string }>; youtubeId: string }> = [];

const playlistDetail = {
  id: 7,
  name: "Party mix",
  color: "cba6f7",
  songCount: 0,
  inviteCode: "ABCD1234",
  members: [
    { userId: 11, displayName: "Alex", username: "alex", owner: true },
    { userId: 12, displayName: "Sam", username: "sam", owner: false },
  ],
};

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={typeof href === "string" ? href : "#"}>{children}</a>
  ),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

vi.mock("@/components/playlist-cover-mosaic", () => ({
  PlaylistCoverMosaic: () => <div aria-hidden="true" />,
}));

vi.mock("@/components/phantom-empty-state", () => ({
  PhantomEmptyState: ({ title, message }: { title: string; message?: string }) => (
    <div>
      <div>{title}</div>
      {message ? <div>{message}</div> : null}
    </div>
  ),
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  getGetPlaylistQueryKey: (playlistId: number) => ["playlist", playlistId],
  useGetPlaylist: () => ({
    data: { ...playlistDetail, songs: playlistSongs, songCount: playlistSongs.length },
    isLoading: false,
  }),
  useDeleteSong: () => ({ mutate: vi.fn() }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  getGetUserPlaylistsQueryKey: () => ["user-playlists"],
  useLeavePlaylist: () => ({ mutate: leaveMutate, isPending: false }),
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetActiveMembership: () => ({ data: membership }),
}));

async function renderContent() {
  const queryClient = new QueryClient();
  await act(async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <PlaylistContent playlistId={7} />
      </QueryClientProvider>,
    );
  });
}

describe("PlaylistContent detail states", () => {
  beforeEach(() => {
    routerPush.mockReset();
    leaveMutate.mockReset();
    membership = { id: 4 };
    playlistSongs = [];
    Object.defineProperty(window.navigator, "clipboard", {
      value: { writeText: vi.fn(() => Promise.resolve()) },
      configurable: true,
    });
  });

  it("shows the ghost empty state without a song search when empty", async () => {
    await renderContent();

    expect(screen.getByText("No songs yet")).toBeVisible();
    expect(screen.queryByPlaceholderText("Search songs...")).toBeNull();
  });

  it("builds a ready invite message for the code action", () => {
    expect(buildInviteMessage("Party mix", "ABCD1234", "http://localhost/playlists/join/ABCD1234")).toBe(
      'Hey, join my playlist "Party mix" on hittiguess! Invite code: ABCD1234 — or open http://localhost/playlists/join/ABCD1234',
    );
  });

  it("starts a session through the active group with the playlist selected", async () => {
    await renderContent();

    fireEvent.click(screen.getByRole("button", { name: "Start session" }));

    expect(routerPush).toHaveBeenCalledWith("/groups/4?playlist=7");
  });

  it("refuses to start without an active group", async () => {
    membership = undefined;
    const { toast } = await import("sonner");
    await renderContent();

    fireEvent.click(screen.getByRole("button", { name: "Start session" }));

    expect(toast.error).toHaveBeenCalledWith("Join or create a group to start a session.");
    expect(routerPush).not.toHaveBeenCalled();
  });

  it("opens the full member list from the avatar stack", async () => {
    await renderContent();

    fireEvent.click(screen.getByText("Members (2)"));

    expect(screen.getByText("Alex")).toBeVisible();
    expect(screen.getByText("Sam")).toBeVisible();
  });

  it("asks for confirmation before leaving", async () => {
    await renderContent();

    fireEvent.click(screen.getByTitle("Leave playlist"));

    expect(screen.getByText("Leave playlist?")).toBeVisible();
  });
});
