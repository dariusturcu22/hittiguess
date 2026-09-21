import { fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ExplorePlaylistsPage from "./page";

const saveMutate = vi.fn();

const publicPlaylists = [
  {
    id: 11,
    name: "Saved mix",
    color: "cba6f7",
    songCount: 4,
    owner: { username: "someone" },
    previewYoutubeIds: [],
  },
  {
    id: 12,
    name: "Fresh mix",
    color: "fab387",
    songCount: 6,
    owner: { username: "other" },
    previewYoutubeIds: [],
  },
];

const savedPlaylists = [
  {
    id: 11,
    name: "Saved mix",
    color: "cba6f7",
    songCount: 4,
    owner: { username: "someone" },
    previewYoutubeIds: [],
  },
];

vi.mock("next/link", () => ({
  default: ({ children, href, ...rest }: { children: ReactNode; href: string }) => (
    <a href={typeof href === "string" ? href : "#"} {...rest}>
      {children}
    </a>
  ),
}));

vi.mock("@/components/phantom-empty-state", () => ({
  PhantomEmptyState: ({ title }: { title: string }) => <div>{title}</div>,
}));

vi.mock("@/components/playlist-cover-mosaic", () => ({
  PlaylistCoverMosaic: () => <div aria-hidden="true" />,
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  useGetPublicPlaylists: () => ({ data: publicPlaylists, isLoading: false, isError: false }),
  useSavePlaylist: () => ({ mutate: saveMutate, isPending: false, isSuccess: false }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetSavedPlaylists: () => ({ data: savedPlaylists, isLoading: false, isError: false }),
}));

vi.mock("@tanstack/react-query", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-query")>();
  return {
    ...actual,
    useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  };
});

function renderPage() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <ExplorePlaylistsPage />
    </QueryClientProvider>,
  );
}

describe("ExplorePlaylistsPage filters", () => {
  beforeEach(() => {
    saveMutate.mockReset();
  });

  function clickTab(name: string) {
    const matches = screen.getAllByRole("button", { name });
    fireEvent.click(matches[0]);
  }

  it("partitions saved and unsaved playlists across tabs", () => {
    renderPage();

    expect(screen.getByText("Saved mix")).toBeVisible();
    expect(screen.getByText("Fresh mix")).toBeVisible();

    clickTab("Saved");

    expect(screen.getByText("Saved mix")).toBeVisible();
    expect(screen.queryByText("Fresh mix")).toBeNull();

    clickTab("Not saved");

    expect(screen.getByText("Fresh mix")).toBeVisible();
    expect(screen.queryByText("Saved mix")).toBeNull();
  });

  it("shows an already saved playlist as saved and disabled", () => {
    renderPage();

    const savedButtons = screen.getAllByRole("button", { name: "Saved" });
    expect(savedButtons.some((button) => button.hasAttribute("disabled"))).toBe(true);
  });

  it("links each card to its playlist detail", () => {
    renderPage();

    expect(screen.getByRole("link", { name: "Open Saved mix" })).toHaveAttribute(
      "href",
      "/playlists/11",
    );
  });

  it("saves an unsaved playlist", () => {
    renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(saveMutate).toHaveBeenCalledWith({ playlistId: 12 });
  });
});
