import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import JoinGroupPage from "./page";

const INVITE_PARAMS = Promise.resolve({ inviteCode: "WXYZ" });
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
  useRouter: () => ({ push: routerPush }),
}));

const routerPush = vi.hoisted(() => vi.fn());

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => currentUserState,
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetActiveMembershipQueryKey: () => ["active-membership"],
  useJoinGroup: () => ({ mutate: joinMutate, isPending: false }),
}));

vi.mock("@/hooks/use-group-invite-preview", () => ({
  useGroupInvitePreview: () => groupPreviewState,
}));

let groupPreviewState: { data?: { memberCount: number; members: never[] }; isError: boolean } = {
  data: { memberCount: 3, members: [] },
  isError: false,
};

async function renderPage() {
  const queryClient = new QueryClient();
  await act(async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <JoinGroupPage params={INVITE_PARAMS} />
      </QueryClientProvider>,
    );
  });
}

describe("JoinGroupPage", () => {
  beforeEach(() => {
    joinMutate.mockReset();
    routerPush.mockReset();
    currentUserState = { data: undefined, isLoading: false, isError: true };
    groupPreviewState = { data: { memberCount: 3, members: [] }, isError: false };
  });

  it("points logged-out users at login with a return to the invite", async () => {
    await renderPage();

    const loginLink = screen.getByRole("link", { name: "Log in to join" });
    expect(loginLink).toHaveAttribute("href", "/login?returnTo=%2Fgroups%2Fjoin%2FWXYZ");
    expect(screen.queryByRole("button", { name: "Join group" })).toBeNull();
  });

  it("joins directly for logged-in users", async () => {
    currentUserState = {
      data: { id: 11, username: "player" },
      isLoading: false,
      isError: false,
    };
    joinMutate.mockImplementation((_args, options) => options?.onSuccess?.({}));
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Join group" }));

    await waitFor(() => expect(joinMutate).toHaveBeenCalled());
  });

  it("shows the server's reason when the chosen identity is refused", async () => {
    const avatarUrlMessage = "Avatar URL must be an https Google profile image";
    currentUserState = { data: { id: 11, username: "player" }, isLoading: false, isError: false };
    joinMutate.mockImplementation((_args, options) => options?.onError?.({ response: { status: 400, data: { avatarUrl: avatarUrlMessage } } }));
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Join group" }));

    expect(await screen.findByText(avatarUrlMessage)).toBeVisible();
  });

  it("shows an invalid screen for a dead invite instead of the join form", async () => {
    groupPreviewState = { data: undefined, isError: true };
    await renderPage();

    expect(screen.getByText("This invite link is no longer valid")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Join group" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Log in to join" })).toBeNull();

    fireEvent.click(screen.getByRole("button", { name: "Back to home" }));
    expect(routerPush).toHaveBeenCalledWith("/");
  });

  it("shows the live member count from the invite preview", async () => {
    await renderPage();

    expect(screen.getByText("3 members · Join this group to play together")).toBeVisible();
  });
});
