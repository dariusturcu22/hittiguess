import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import JoinPlaylistPage from "./page";

const INVITE_PARAMS = Promise.resolve({ inviteCode: "abc123" });
const joinMutate = vi.fn();
let currentUserState: { data?: { id: number; username: string }; isLoading: boolean; isError: boolean } = {
  data: undefined,
  isLoading: false,
  isError: true,
};

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  getGetUserPlaylistsQueryKey: () => ["user-playlists"],
  useGetCurrentUser: () => currentUserState,
  useJoinPlaylist: () => ({ mutate: joinMutate, isPending: false }),
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  useGetInvitePreview: () => ({
    data: { name: "Party mix", color: "cba6f7", songCount: 9, members: [] },
  }),
}));

async function renderPage() {
  const queryClient = new QueryClient();
  await act(async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <JoinPlaylistPage params={INVITE_PARAMS} />
      </QueryClientProvider>,
    );
  });
}

describe("JoinPlaylistPage", () => {
  beforeEach(() => {
    joinMutate.mockReset();
    joinMutate.mockImplementation((_args, options) => options?.onSuccess?.({ id: 21 }));
    currentUserState = { data: undefined, isLoading: false, isError: true };
  });

  it("points logged-out users at login with a return to the invite", async () => {
    await renderPage();

    const loginLink = screen.getByRole("link", { name: "Log in to join" });
    expect(loginLink).toHaveAttribute("href", "/login?returnTo=%2Fplaylists%2Fjoin%2Fabc123");
    expect(screen.queryByRole("button", { name: "Join playlist" })).toBeNull();
  });

  it("joins directly for logged-in users", async () => {
    currentUserState = {
      data: { id: 11, username: "player" },
      isLoading: false,
      isError: false,
    };
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Join playlist" }));

    await waitFor(() => expect(joinMutate).toHaveBeenCalledWith(
      { playlistInviteCode: "abc123", data: { displayName: undefined, avatarUrl: undefined } },
      expect.anything(),
    ));
  });
});
