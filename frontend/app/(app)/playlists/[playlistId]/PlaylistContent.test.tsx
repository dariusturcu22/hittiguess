import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import PlaylistContent, { buildInviteMessage } from "./PlaylistContent";

const routerPush = vi.fn();
const leaveMutate = vi.fn();
let membership: { id: number } | undefined = { id: 4 };
let playlistMembers = [
  { userId: 11, displayName: "Alex", username: "alex", owner: true },
  { userId: 12, displayName: "Sam", username: "sam", owner: false },
];
let playlistSongs: Array<{ id: number; title: string; artists: Array<{ name: string }>; youtubeId: string }> = [];

const playlistDetail = {
  id: 7,
  name: "Party mix",
  color: "cba6f7",
  songCount: 0,
  inviteCode: "ABCD1234",
  members: playlistMembers,
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
    data: { ...playlistDetail, members: playlistMembers, songs: playlistSongs, songCount: playlistSongs.length },
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

let activeImportResponse: { data?: unknown; isError: boolean } = { isError: true };

vi.mock("@/hooks/generated/playlist-import-jobs/playlist-import-jobs", () => ({
  useActiveImport: () => activeImportResponse,
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
    playlistMembers = [
      { userId: 11, displayName: "Alex", username: "alex", owner: true },
      { userId: 12, displayName: "Sam", username: "sam", owner: false },
    ];
    activeImportResponse = { isError: true };
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

  it("links pending imports to the progress page", async () => {
    activeImportResponse = {
      isError: false,
      data: {
        id: "job-1",
        playlistId: 7,
        status: "RUNNING",
        items: [
          { youtubeId: "video-9", status: "PENDING" },
          { youtubeId: "video-8", status: "UNRESOLVED" },
          { youtubeId: "video-7", status: "RESOLVED", songId: 77 },
        ],
      },
    };
    await renderContent();

    expect(screen.getByText(/Importing 2 songs in the background/)).toBeVisible();
    expect(screen.getByRole("link", { name: /Importing 2 songs in the background/ })).toHaveAttribute(
      "href",
      "/playlists/7/import/youtube",
    );
  });

  it("builds a ready invite message for the code action", () => {
    expect(buildInviteMessage("Party mix", "ABCD1234", "http://localhost/playlists/join/ABCD1234")).toBe(
      'Hey, join my playlist "Party mix" on hittiguess! Invite code: ABCD1234. Open http://localhost/playlists/join/ABCD1234 to join.',
    );
  });

  it("shows an error toast and leaves the label unchanged when copying the invite link fails", async () => {
    Object.defineProperty(window.navigator, "clipboard", {
      value: { writeText: vi.fn(() => Promise.reject(new Error("denied"))) },
      configurable: true,
    });
    document.execCommand = vi.fn(() => false);
    const { toast } = await import("sonner");
    await renderContent();

    const inviteTrigger = screen.getByTitle("Invite");
    fireEvent.pointerDown(inviteTrigger, { button: 0, pointerId: 1 });
    fireEvent.click(inviteTrigger);
    fireEvent.click(await screen.findByText("Copy invite link"));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith("Couldn't copy the link. Try again."));
    expect(screen.queryByText("Link copied")).toBeNull();
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

  it("shows an overflow count on the avatar stack past three members", async () => {
    playlistMembers = [
      { userId: 11, displayName: "Alex", username: "alex", owner: true },
      { userId: 12, displayName: "Sam", username: "sam", owner: false },
      { userId: 13, displayName: "Mara", username: "mara", owner: false },
      { userId: 14, displayName: "Theo", username: "theo", owner: false },
      { userId: 15, displayName: "Lena", username: "lena", owner: false },
    ];
    await renderContent();

    expect(screen.getByText("+2")).toBeVisible();
  });

  it("opens the full member list from the avatar stack", async () => {
    await renderContent();

    fireEvent.click(screen.getByRole("button", { name: "Members (2)" }));

    expect(screen.getByText("Alex")).toBeVisible();
    expect(screen.getByText("Sam")).toBeVisible();
  });

  it("asks for confirmation before leaving", async () => {
    await renderContent();

    fireEvent.click(screen.getByTitle("Leave playlist"));

    expect(screen.getByText("Leave playlist?")).toBeVisible();
  });

  it("exports the chosen cards and paper size", async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      blob: async () => new Blob(["pdf"], { type: "application/pdf" }),
    }));
    vi.stubGlobal("fetch", fetchMock);
    const createObjectUrl = vi.fn(() => "blob:export-url");
    Object.defineProperty(URL, "createObjectURL", { value: createObjectUrl, configurable: true });
    Object.defineProperty(URL, "revokeObjectURL", { value: vi.fn(), configurable: true });
    playlistSongs = [
      { id: 1, title: "Song One", artists: [{ name: "Artist One" }], youtubeId: "video-1" },
    ];
    await renderContent();

    fireEvent.click(screen.getByTitle("Export cards"));
    fireEvent.click(screen.getByRole("combobox", { name: "Cards" }));
    fireEvent.click(screen.getByRole("option", { name: "QR cards (manual duplex)" }));
    fireEvent.click(screen.getByRole("combobox", { name: "Paper" }));
    fireEvent.click(screen.getByRole("option", { name: "Letter" }));
    fireEvent.click(screen.getByRole("button", { name: "Download PDF" }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining("/api/playlists/7/export/qr?paperSize=LETTER"),
        expect.anything(),
      ),
    );
    vi.unstubAllGlobals();
  });

  it("exports the single file duplex cards on the new paper sizes", async () => {
    const fetchMock = vi.fn(async () => ({
      ok: true,
      blob: async () => new Blob(["pdf"], { type: "application/pdf" }),
    }));
    vi.stubGlobal("fetch", fetchMock);
    const createObjectUrl = vi.fn(() => "blob:export-url");
    Object.defineProperty(URL, "createObjectURL", { value: createObjectUrl, configurable: true });
    Object.defineProperty(URL, "revokeObjectURL", { value: vi.fn(), configurable: true });
    playlistSongs = [
      { id: 1, title: "Song One", artists: [{ name: "Artist One" }], youtubeId: "video-1" },
    ];
    await renderContent();

    fireEvent.click(screen.getByTitle("Export cards"));
    fireEvent.click(screen.getByRole("combobox", { name: "Cards" }));
    fireEvent.click(screen.getByRole("option", { name: "Single file (auto duplex)" }));
    fireEvent.click(screen.getByRole("combobox", { name: "Paper" }));
    fireEvent.click(screen.getByRole("option", { name: "A3" }));
    fireEvent.click(screen.getByRole("button", { name: "Download PDF" }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining("/api/playlists/7/export/duplex?paperSize=A3"),
        expect.anything(),
      ),
    );
    vi.unstubAllGlobals();
  });

  it("refuses to open the export dialog for an empty playlist", async () => {
    const { toast } = await import("sonner");
    await renderContent();

    fireEvent.click(screen.getByTitle("Export cards"));

    expect(toast.error).toHaveBeenCalledWith("Can't export an empty playlist");
    expect(screen.queryByRole("button", { name: "Download PDF" })).toBeNull();
  });
});
