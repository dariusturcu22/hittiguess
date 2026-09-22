import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import EditPlaylistPage from "./page";

const EDIT_PARAMS = Promise.resolve({ playlistId: "7" });
const uploadCoverMutate = vi.fn();

vi.mock("@/hooks/generated/pixel-art-images/pixel-art-images", () => ({
  useUploadPlaylistCover: () => ({ mutate: uploadCoverMutate, isPending: false }),
}));

vi.mock("@/lib/pixelate", () => ({
  pixelizeImage: vi.fn(async () => new Blob(["pixels"], { type: "image/png" })),
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={typeof href === "string" ? href : "#"}>{children}</a>
  ),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("sonner", () => ({
  toast: { error: vi.fn(), success: vi.fn() },
}));

vi.mock("@/components/playlist-cover-mosaic", () => ({
  PlaylistCoverMosaic: () => <div aria-hidden="true" />,
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  getGetMembersQueryKey: (playlistId: number) => ["members", playlistId],
  getGetPlaylistQueryKey: (playlistId: number) => ["playlist", playlistId],
  useGetPlaylist: () => ({
    data: {
      id: 7,
      name: "Party mix",
      color: "cba6f7",
      inviteCode: "ABCD1234",
      songs: [],
      members: [],
    },
  }),
  useGetMembers: () => ({ data: [] }),
  useUpdatePlaylist: () => ({ mutate: vi.fn() }),
  useUpdateMemberGrants: () => ({ mutate: vi.fn() }),
  useKickMember: () => ({ mutate: vi.fn() }),
  useBanMember: () => ({ mutate: vi.fn() }),
  usePublishPlaylist: () => ({ mutate: vi.fn(), isPending: false }),
  useUnpublishPlaylist: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  getGetUserPlaylistsQueryKey: () => ["user-playlists"],
}));

async function renderPage() {
  const queryClient = new QueryClient();
  await act(async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <EditPlaylistPage params={EDIT_PARAMS} />
      </QueryClientProvider>,
    );
  });
}

describe("EditPlaylistPage", () => {
  beforeEach(() => {
    Object.defineProperty(window.navigator, "clipboard", {
      value: { writeText: vi.fn(() => Promise.resolve()) },
      configurable: true,
    });
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:preview"),
      revokeObjectURL: vi.fn(),
    });
  });

  it("copies the invite link for the current origin", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Copy invite link" }));

    await waitFor(() =>
      expect(window.navigator.clipboard.writeText).toHaveBeenCalledWith(
        `${window.location.origin}/playlists/join/ABCD1234`,
      ),
    );
  });

  it("asks for confirmation before deleting", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Delete" }));

    expect(await screen.findByText("Delete this playlist?")).toBeVisible();
  });

  it("uploads the pixelized file as the playlist cover", async () => {
    uploadCoverMutate.mockReset();
    await renderPage();

    const coverPicker = screen.getByRole("button", { name: "Change playlist cover" });
    const fileInput = coverPicker.parentElement?.querySelector('input[type="file"]');
    expect(fileInput).not.toBeNull();
    fireEvent.change(fileInput!, {
      target: { files: [new File(["source"], "cover.jpg", { type: "image/jpeg" })] },
    });

    await waitFor(() =>
      expect(uploadCoverMutate).toHaveBeenCalledWith(
        { playlistId: 7, data: { cover: expect.any(Blob) } },
        expect.anything(),
      ),
    );
  });
});
