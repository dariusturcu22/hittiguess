import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import PlaylistsPage from "./page";

vi.mock("next/link", () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={typeof href === "string" ? href : "#"}>{children}</a>
  ),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/components/playlist-cover-mosaic", () => ({
  PlaylistCoverMosaic: () => <div aria-hidden="true" />,
}));

const ownedPlaylist = {
  id: 1,
  name: "Owned mix",
  color: "cba6f7",
  songCount: 4,
  previewYoutubeIds: [],
  ownedByCurrentUser: true,
};

const joinedPlaylist = {
  id: 2,
  name: "Joined mix",
  color: "fab387",
  songCount: 6,
  previewYoutubeIds: [],
  ownedByCurrentUser: false,
};

const savedPlaylist = {
  id: 3,
  name: "Saved mix",
  color: "a6e3a1",
  songCount: 8,
  owner: { username: "someone" },
  previewYoutubeIds: [],
};

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useCreatePlaylist: () => ({ mutate: vi.fn(), isPending: false }),
  getGetUserPlaylistsQueryKey: () => ["user-playlists"],
  useGetUserPlaylists: () => ({
    data: [ownedPlaylist, joinedPlaylist],
    isLoading: false,
    isError: false,
  }),
  useGetSavedPlaylists: () => ({
    data: [savedPlaylist],
    isLoading: false,
    isError: false,
  }),
}));

function renderPage() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <PlaylistsPage />
    </QueryClientProvider>,
  );
}

describe("PlaylistsPage library tabs", () => {
  it("shows owned playlists by default and joined ones on the Joined tab", () => {
    renderPage();

    expect(screen.getByText("Owned mix")).toBeVisible();
    expect(screen.queryByText("Joined mix")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Joined" }));

    expect(screen.getByText("Joined mix")).toBeVisible();
    expect(screen.queryByText("Owned mix")).toBeNull();
  });

  it("lists saved playlists on the Saved tab", () => {
    renderPage();

    expect(screen.queryByText("Saved mix")).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Saved" }));

    expect(screen.getByText("Saved mix")).toBeVisible();
    expect(screen.queryByText("Owned mix")).toBeNull();
    expect(screen.queryByText("Joined mix")).toBeNull();
  });
});
