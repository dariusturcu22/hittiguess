import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import JoinGroupPage from "./page";

const INVITE_PARAMS = Promise.resolve({ inviteCode: "WXYZ" });
const joinMutate = vi.fn();
const leaveGroupMutate = vi.fn();
let currentUserState: { data?: { id: number; username: string }; isLoading: boolean; isError: boolean } = {
  data: undefined,
  isLoading: false,
  isError: true,
};
let activeMembershipState: { data?: { id: number } } = { data: undefined };

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
  useGetActiveMembership: () => activeMembershipState,
  useJoinGroup: () => ({ mutate: joinMutate, isPending: false }),
  useLeaveGroup: () => ({ mutate: leaveGroupMutate, isPending: false }),
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
    leaveGroupMutate.mockReset();
    routerPush.mockReset();
    currentUserState = { data: undefined, isLoading: false, isError: true };
    activeMembershipState = { data: undefined };
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

  it("shows the server's conflict message and retries the join after leaving the current group", async () => {
    const conflictMessage = "You are already in a group. Leave it first, then join this one.";
    const currentGroupId = 42;
    currentUserState = { data: { id: 11, username: "player" }, isLoading: false, isError: false };
    activeMembershipState = { data: { id: currentGroupId } };
    joinMutate.mockImplementation((_args, options) =>
      options?.onError?.({ response: { status: 409, data: { message: conflictMessage } } }),
    );
    leaveGroupMutate.mockImplementation((_args, options) => options?.onSuccess?.());
    await renderPage();

    fireEvent.change(screen.getByRole("textbox"), { target: { value: "Star" } });
    fireEvent.click(screen.getByRole("button", { name: "Join group" }));

    expect(await screen.findByText(conflictMessage)).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Leave your current group and join this one" }));

    await waitFor(() => expect(leaveGroupMutate).toHaveBeenCalledWith({ groupId: currentGroupId }, expect.anything()));
    const [retryPayload] = joinMutate.mock.lastCall ?? [];
    expect(retryPayload.data).toMatchObject({ inviteCode: "WXYZ", displayName: "Star" });
  });

  it("keeps the invalid-link wording for a missing invite code and offers no leave action", async () => {
    currentUserState = { data: { id: 11, username: "player" }, isLoading: false, isError: false };
    activeMembershipState = { data: { id: 42 } };
    joinMutate.mockImplementation((_args, options) =>
      options?.onError?.({ response: { status: 404, data: { message: "No group matches that invite code" } } }),
    );
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Join group" }));

    expect(await screen.findByText("That invite link isn't valid.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Leave your current group and join this one" })).toBeNull();
  });

  it("follows the success path when reopening an invite for a group you already belong to", async () => {
    currentUserState = { data: { id: 11, username: "player" }, isLoading: false, isError: false };
    activeMembershipState = { data: { id: 7 } };
    joinMutate.mockImplementation((_args, options) => options?.onSuccess?.({ id: 7 }));
    await renderPage();

    fireEvent.click(screen.getByRole("button", { name: "Join group" }));

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith("/groups/7"));
    expect(screen.queryByRole("button", { name: "Leave your current group and join this one" })).toBeNull();
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
