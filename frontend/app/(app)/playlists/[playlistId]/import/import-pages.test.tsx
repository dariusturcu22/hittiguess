import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ImportChooseSourceContent } from "./page";
import { ImportFromPlaylistSelectContent } from "./from-playlist/page";
import { ImportFromPlaylistConfirmContent } from "./from-playlist/[sourceId]/page";

const toastMocks = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

vi.mock("sonner", () => ({
  toast: toastMocks,
}));

const mocks = vi.hoisted(() => ({
  ownPlaylists: {
    data: undefined as unknown,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
  publicPlaylists: {
    data: undefined as unknown,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
  sourcePlaylist: {
    data: undefined as unknown,
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  },
  importMutation: {
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  },
  push: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.push }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetUserPlaylists: () => mocks.ownPlaylists,
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  useGetPublicPlaylists: () => mocks.publicPlaylists,
  useGetPlaylist: () => mocks.sourcePlaylist,
  useImportFromPlaylist: () => mocks.importMutation,
}));

function renderSelectPage() {
  return render(
    <ImportFromPlaylistSelectContent destinationPlaylistId={12} />,
  );
}

function renderConfirmPage() {
  return render(
    <ImportFromPlaylistConfirmContent
      destinationPlaylistId={12}
      sourcePlaylistId={34}
    />,
  );
}

describe("playlist import pages", () => {
  beforeEach(() => {
    mocks.ownPlaylists.data = undefined;
    mocks.ownPlaylists.isLoading = false;
    mocks.ownPlaylists.isError = false;
    mocks.ownPlaylists.refetch.mockReset();
    mocks.publicPlaylists.data = undefined;
    mocks.publicPlaylists.isLoading = false;
    mocks.publicPlaylists.isError = false;
    mocks.publicPlaylists.refetch.mockReset();
    mocks.sourcePlaylist.data = undefined;
    mocks.sourcePlaylist.isLoading = false;
    mocks.sourcePlaylist.isError = false;
    mocks.sourcePlaylist.refetch.mockReset();
    mocks.importMutation.mutate.mockReset();
    mocks.push.mockReset();
    toastMocks.success.mockReset();
  });

  it("renders the designed source choices", async () => {
    render(<ImportChooseSourceContent playlistId={12} />);

    expect(await screen.findByRole("heading", { name: "Import playlist" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /From YouTube/ })).toHaveAttribute(
      "href",
      "/playlists/12/import/youtube",
    );
    expect(screen.getByRole("link", { name: /From an existing playlist/ })).toHaveAttribute(
      "href",
      "/playlists/12/import/from-playlist",
    );
  });

  it("shows loading, empty, and retryable error states for playlist selection", async () => {
    mocks.ownPlaylists.isLoading = true;
    mocks.publicPlaylists.isLoading = false;
    const view = renderSelectPage();
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Finding playlists you can copy from...",
    );

    mocks.ownPlaylists.isLoading = false;
    mocks.publicPlaylists.isLoading = false;
    mocks.ownPlaylists.isError = false;
    mocks.publicPlaylists.isError = false;
    mocks.ownPlaylists.data = [];
    mocks.publicPlaylists.data = [];
    view.rerender(
      <ImportFromPlaylistSelectContent destinationPlaylistId={12} />,
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      "No playlists to import from yet.",
    );

    mocks.ownPlaylists.isError = true;
    view.rerender(
      <ImportFromPlaylistSelectContent destinationPlaylistId={12} />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Couldn't load playlists right now.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(mocks.ownPlaylists.refetch).toHaveBeenCalled();
    expect(mocks.publicPlaylists.refetch).toHaveBeenCalled();
  });

  it("shows loading, empty, and retryable error states for source songs", async () => {
    mocks.sourcePlaylist.isLoading = true;
    const view = renderConfirmPage();
    expect(await screen.findByRole("status")).toHaveTextContent("Loading songs...");

    mocks.sourcePlaylist.isLoading = false;
    mocks.sourcePlaylist.isError = false;
    mocks.sourcePlaylist.data = {
      name: "Empty playlist",
      songCount: 0,
      songs: [],
    };
    view.rerender(
      <ImportFromPlaylistConfirmContent
        destinationPlaylistId={12}
        sourcePlaylistId={34}
      />,
    );
    expect(await screen.findByRole("status")).toHaveTextContent(
      "This playlist has no songs to copy.",
    );

    mocks.sourcePlaylist.isError = true;
    view.rerender(
      <ImportFromPlaylistConfirmContent
        destinationPlaylistId={12}
        sourcePlaylistId={34}
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Failed to load the source playlist.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(mocks.sourcePlaylist.refetch).toHaveBeenCalled();
  });

  it("toasts the added song count and navigates on a successful import", async () => {
    mocks.sourcePlaylist.data = {
      name: "Source playlist",
      songCount: 3,
      songs: [{ id: 1, title: "A song", releaseYear: 2000, artists: [] }],
    };
    mocks.importMutation.mutate.mockImplementation((_args, options) => options?.onSuccess?.());
    renderConfirmPage();

    fireEvent.click(screen.getByRole("button", { name: "Add 3 songs to playlist" }));

    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("3 songs added"));
    expect(mocks.push).toHaveBeenCalledWith("/playlists/12");
  });
});
