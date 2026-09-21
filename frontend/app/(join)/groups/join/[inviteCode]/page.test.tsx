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
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => currentUserState,
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetActiveMembershipQueryKey: () => ["active-membership"],
  useJoinGroup: () => ({ mutate: joinMutate, isPending: false }),
}));

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
    currentUserState = { data: undefined, isLoading: false, isError: true };
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
});
