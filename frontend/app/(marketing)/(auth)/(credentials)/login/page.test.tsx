import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import LoginPage from "./page";

const { toastError } = vi.hoisted(() => ({ toastError: vi.fn() }));
const loginMutate = vi.fn();
const loginPush = vi.fn();
let loginSearchParams = new URLSearchParams();
let loginMutationCallbacks: { onSuccess?: (data: unknown) => void; onError?: (error: unknown) => void } = {};

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: loginPush }),
  useSearchParams: () => loginSearchParams,
}));
vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useLogin: (options?: { mutation?: { onSuccess?: (data: unknown) => void; onError?: (error: unknown) => void } }) => {
    loginMutationCallbacks = options?.mutation ?? {};
    return { mutate: loginMutate, isPending: false };
  },
}));

vi.mock("sonner", () => ({ toast: { error: toastError } }));

const FORBIDDEN_STATUS = 403;
const UNAUTHORIZED_STATUS = 401;
const NOT_VERIFIED_MESSAGE = "Email not verified, check your inbox for a verification link";

describe("LoginPage", () => {
  beforeEach(() => {
    toastError.mockReset();
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

  it("carries the return target on the Google link", () => {
    loginSearchParams = new URLSearchParams("returnTo=/groups/join/abc123");
    render(<LoginPage />);

    expect(screen.getByRole("link", { name: "Continue with Google" })).toHaveAttribute(
      "href",
      expect.stringContaining("returnTo=%2Fgroups%2Fjoin%2Fabc123"),
    );
  });

  it("leaves the Google link bare without a return target", () => {
    render(<LoginPage />);

    expect(screen.getByRole("link", { name: "Continue with Google" })).toHaveAttribute(
      "href",
      expect.not.stringContaining("returnTo"),
    );
  });

  it("tells an unverified account why it cannot log in", () => {
    render(<LoginPage />);
    loginMutationCallbacks.onError?.({ response: { status: FORBIDDEN_STATUS, data: { message: NOT_VERIFIED_MESSAGE } } });
    expect(toastError).toHaveBeenCalledWith(NOT_VERIFIED_MESSAGE);
  });

  it("keeps the generic message for wrong credentials", () => {
    render(<LoginPage />);
    loginMutationCallbacks.onError?.({ response: { status: UNAUTHORIZED_STATUS, data: { message: "Invalid username or password" } } });
    expect(toastError).toHaveBeenCalledWith("Invalid email or password.");
  });
});
