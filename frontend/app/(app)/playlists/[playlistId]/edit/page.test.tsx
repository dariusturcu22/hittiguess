import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import EditPlaylistPage from "./page";

const EDIT_PARAMS = Promise.resolve({ playlistId: "7" });
const uploadCoverMutate = vi.fn();
const updatePlaylistMutate = vi.fn();
const publishMutate = vi.fn();
const unpublishMutate = vi.fn();
const routerPush = vi.fn();
const toastMocks = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

const LOADED_PLAYLIST = {
  id: 7,
  name: "Party mix",
  color: "cba6f7",
  description: "Late-night driving songs",
  inviteCode: "ABCD1234",
  isPublic: false,
  songs: [],
  members: [],
};
const getPlaylistMock = vi.fn<() => { data: typeof LOADED_PLAYLIST | undefined; isLoading: boolean }>(
  () => ({ data: LOADED_PLAYLIST, isLoading: false }),
);

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
  useRouter: () => ({ push: routerPush }),
}));

vi.mock("sonner", () => ({
  toast: toastMocks,
}));

vi.mock("@/components/playlist-cover-mosaic", () => ({
  PlaylistCoverMosaic: () => <div aria-hidden="true" />,
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  getGetMembersQueryKey: (playlistId: number) => ["members", playlistId],
  getGetPlaylistQueryKey: (playlistId: number) => ["playlist", playlistId],
  useGetPlaylist: () => getPlaylistMock(),
  useGetMembers: () => ({ data: [] }),
  useUpdatePlaylist: () => ({ mutate: updatePlaylistMutate, isPending: false }),
  useUpdateMemberGrants: () => ({ mutate: vi.fn() }),
  useKickMember: () => ({ mutate: vi.fn() }),
  useBanMember: () => ({ mutate: vi.fn() }),
  usePublishPlaylist: () => ({ mutate: publishMutate, isPending: false }),
  useUnpublishPlaylist: () => ({ mutate: unpublishMutate, isPending: false }),
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
    updatePlaylistMutate.mockReset();
    publishMutate.mockReset();
    unpublishMutate.mockReset();
    getPlaylistMock.mockReset();
    getPlaylistMock.mockReturnValue({ data: LOADED_PLAYLIST, isLoading: false });
    routerPush.mockReset();
    toastMocks.success.mockReset();
    toastMocks.error.mockReset();
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

  it("saves a changed name with a toast and returns to the detail page", async () => {
    updatePlaylistMutate.mockImplementation((_args, options) => options?.onSuccess?.());
    await renderPage();

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Renamed mix" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(updatePlaylistMutate).toHaveBeenCalledWith(
      { playlistId: 7, data: { name: "Renamed mix", description: "Late-night driving songs" } },
      expect.anything(),
    );
    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Changes saved"));
    expect(routerPush).toHaveBeenCalledWith("/playlists/7");
  });

  it("hydrates the description field from the loaded playlist", async () => {
    await renderPage();

    expect(screen.getByLabelText("Description")).toHaveValue("Late-night driving songs");
  });

  it("saves a description-only change with the unchanged name", async () => {
    updatePlaylistMutate.mockImplementation((_args, options) => options?.onSuccess?.());
    await renderPage();

    fireEvent.change(screen.getByLabelText("Description"), { target: { value: "Songs for the drive home" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    expect(updatePlaylistMutate).toHaveBeenCalledWith(
      { playlistId: 7, data: { name: "Party mix", description: "Songs for the drive home" } },
      expect.anything(),
    );
    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Changes saved"));
  });

  it("shows an error when saving fails", async () => {
    updatePlaylistMutate.mockImplementation((_args, options) => options?.onError?.());
    await renderPage();

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Renamed mix" } });
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }));

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("Couldn't save the changes"));
    expect(routerPush).not.toHaveBeenCalled();
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

  it("shows a loading skeleton before the playlist loads", async () => {
    getPlaylistMock.mockReturnValue({ data: undefined, isLoading: true });
    await renderPage();

    expect(screen.getByTestId("edit-playlist-skeleton")).toBeInTheDocument();
    expect(screen.queryByLabelText("Name")).not.toBeInTheDocument();
  });

  it("toasts on a successful publish toggle", async () => {
    publishMutate.mockImplementation((_args, options) => options?.onSuccess?.());
    await renderPage();

    fireEvent.click(screen.getByRole("switch"));

    expect(publishMutate).toHaveBeenCalledWith({ playlistId: 7 }, expect.anything());
    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Playlist published"));
  });

  it("toasts an error when the publish toggle fails", async () => {
    publishMutate.mockImplementation((_args, options) => options?.onError?.());
    await renderPage();

    fireEvent.click(screen.getByRole("switch"));

    await waitFor(() =>
      expect(toastMocks.error).toHaveBeenCalledWith("Couldn't change the playlist's public status. Try again."),
    );
  });

  it("toasts on copying the invite link", async () => {
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Copy invite link" }));

    await waitFor(() => expect(toastMocks.success).toHaveBeenCalledWith("Invite link copied"));
  });
});
