import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import LoginPage from "./page";

const loginMutate = vi.fn();
const loginPush = vi.fn();
let loginSearchParams = new URLSearchParams();
let loginMutationCallbacks: { onSuccess?: (data: unknown) => void } = {};

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: loginPush }),
  useSearchParams: () => loginSearchParams,
}));
vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useLogin: (options?: { mutation?: { onSuccess?: (data: unknown) => void } }) => {
    loginMutationCallbacks = options?.mutation ?? {};
    return { mutate: loginMutate, isPending: false };
  },
}));

describe("LoginPage", () => {
  beforeEach(() => {
    loginMutate.mockReset();
    loginPush.mockReset();
    loginSearchParams = new URLSearchParams();
  });

  it("does not submit invalid credentials", async () => {
    render(<LoginPage />);
    fireEvent.click(screen.getByRole("button", { name: "Log in" }));
    expect((await screen.findAllByText(/too small/i)).length).toBeGreaterThan(0);
    expect(loginMutate).not.toHaveBeenCalled();
  });

  it("submits valid credentials", async () => {
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "player@example.com" } });
    fireEvent.change(document.querySelector('input[type="password"]')!, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Log in" }));
    await waitFor(() => expect(loginMutate).toHaveBeenCalledWith({ data: { email: "player@example.com", password: "CorrectHorseBatteryStaple1!", rememberMe: false } }));
  });

  it("sends remember-me when the checkbox is checked", async () => {
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "player@example.com" } });
    fireEvent.change(document.querySelector('input[type="password"]')!, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.click(screen.getByLabelText("Remember me"));
    fireEvent.click(screen.getByRole("button", { name: "Log in" }));
    await waitFor(() => expect(loginMutate).toHaveBeenCalledWith({ data: { email: "player@example.com", password: "CorrectHorseBatteryStaple1!", rememberMe: true } }));
  });

  it("returns to the join prompt after login when one was requested", async () => {
    loginSearchParams = new URLSearchParams("returnTo=/playlists/join/abc123");
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "player@example.com" } });
    fireEvent.change(document.querySelector('input[type="password"]')!, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Log in" }));
    await waitFor(() => expect(loginMutate).toHaveBeenCalled());
    loginMutationCallbacks.onSuccess?.({});
    await waitFor(() => expect(loginPush).toHaveBeenCalledWith("/playlists/join/abc123"));
  });

  it("ignores an off-site return target", async () => {
    loginSearchParams = new URLSearchParams("returnTo=https://evil.example.com");
    render(<LoginPage />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "player@example.com" } });
    fireEvent.change(document.querySelector('input[type="password"]')!, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Log in" }));
    await waitFor(() => expect(loginMutate).toHaveBeenCalled());
    loginMutationCallbacks.onSuccess?.({});
    await waitFor(() => expect(loginPush).toHaveBeenCalledWith("/playlists"));
  });
});
