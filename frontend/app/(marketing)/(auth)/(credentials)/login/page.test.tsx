import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import LoginPage from "./page";

const { toastError, toastSuccess } = vi.hoisted(() => ({ toastError: vi.fn(), toastSuccess: vi.fn() }));
const resendMutate = vi.fn();
const loginMutate = vi.fn();
const verifyTwoFactorMutate = vi.fn();
const loginPush = vi.fn();
let loginSearchParams = new URLSearchParams();
type LoginErrorHandler = (error: unknown, variables: { data: { email: string } }) => void;
let loginMutationCallbacks: { onSuccess?: (data: unknown) => void; onError?: LoginErrorHandler } = {};
let resendMutationCallbacks: { onSuccess?: () => void; onError?: (error: unknown) => void } = {};
let verifyTwoFactorCallbacks: { onSuccess?: (data: unknown) => void; onError?: (error: unknown) => void } = {};

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: loginPush }),
  useSearchParams: () => loginSearchParams,
}));
vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useLogin: (options?: { mutation?: { onSuccess?: (data: unknown) => void; onError?: LoginErrorHandler } }) => {
    loginMutationCallbacks = options?.mutation ?? {};
    return { mutate: loginMutate, isPending: false };
  },
  useVerifyTwoFactor: (options?: { mutation?: { onSuccess?: (data: unknown) => void; onError?: (error: unknown) => void } }) => {
    verifyTwoFactorCallbacks = options?.mutation ?? {};
    return { mutate: verifyTwoFactorMutate, isPending: false };
  },
  useResendVerification: (options?: { mutation?: { onSuccess?: () => void; onError?: (error: unknown) => void } }) => {
    resendMutationCallbacks = options?.mutation ?? {};
    return { mutate: resendMutate, isPending: false };
  },
}));

vi.mock("sonner", () => ({ toast: { error: toastError, success: toastSuccess } }));

const FORBIDDEN_STATUS = 403;
const UNAUTHORIZED_STATUS = 401;
const NOT_VERIFIED_MESSAGE = "Email not verified, check your inbox for a verification link";
const LOGIN_VARIABLES = { data: { email: "player@example.com" } };

describe("LoginPage", () => {
  beforeEach(() => {
    toastError.mockReset();
    toastSuccess.mockReset();
    resendMutate.mockReset();
    loginMutate.mockReset();
    verifyTwoFactorMutate.mockReset();
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

  it("redirects to /playlists after successful password login without 2FA", async () => {
    render(<LoginPage />);
    act(() => loginMutationCallbacks.onSuccess?.({ id: 1, username: "alex" }));
    expect(loginPush).toHaveBeenCalledWith("/playlists");
  });

  it("honors safe returnTo query parameter after successful password login", async () => {
    loginSearchParams = new URLSearchParams("returnTo=/playlists/join/code123");
    render(<LoginPage />);
    act(() => loginMutationCallbacks.onSuccess?.({ id: 1, username: "alex" }));
    expect(loginPush).toHaveBeenCalledWith("/playlists/join/code123");
  });

  it("renders second step when 2FA is required", async () => {
    render(<LoginPage />);
    act(() => loginMutationCallbacks.onSuccess?.({ twoFactorRequired: true, pendingToken: "pending-jwt-token" }));

    expect(screen.getByRole("heading", { name: "Two-factor authentication" })).toBeVisible();
    expect(screen.getByLabelText("Authentication or backup code")).toBeVisible();
    expect(screen.getByRole("button", { name: "Verify and log in" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Back to login" })).toBeVisible();
  });

  it("submits 2FA code and redirects on success", async () => {
    render(<LoginPage />);
    act(() => loginMutationCallbacks.onSuccess?.({ twoFactorRequired: true, pendingToken: "pending-jwt-token" }));

    const codeInput = screen.getByLabelText("Authentication or backup code");
    fireEvent.change(codeInput, { target: { value: "123456" } });

    const verifyButton = screen.getByRole("button", { name: "Verify and log in" });
    fireEvent.click(verifyButton);

    expect(verifyTwoFactorMutate).toHaveBeenCalledWith({
      data: {
        pendingToken: "pending-jwt-token",
        code: "123456",
      },
    });

    act(() => verifyTwoFactorCallbacks.onSuccess?.({ id: 1, username: "alex" }));
    expect(toastSuccess).toHaveBeenCalledWith("Logged in successfully.");
    expect(loginPush).toHaveBeenCalledWith("/playlists");
  });

  it("submits one-time backup code and redirects on success", async () => {
    loginSearchParams = new URLSearchParams("returnTo=/settings");
    render(<LoginPage />);
    act(() => loginMutationCallbacks.onSuccess?.({ twoFactorRequired: true, pendingToken: "pending-jwt-token" }));

    const codeInput = screen.getByLabelText("Authentication or backup code");
    fireEvent.change(codeInput, { target: { value: "ABCDEFGH23" } });

    fireEvent.click(screen.getByRole("button", { name: "Verify and log in" }));

    expect(verifyTwoFactorMutate).toHaveBeenCalledWith({
      data: {
        pendingToken: "pending-jwt-token",
        code: "ABCDEFGH23",
      },
    });

    act(() => verifyTwoFactorCallbacks.onSuccess?.({ id: 1, username: "alex" }));
    expect(loginPush).toHaveBeenCalledWith("/settings");
  });

  it("displays error when 2FA verification fails", async () => {
    render(<LoginPage />);
    act(() => loginMutationCallbacks.onSuccess?.({ twoFactorRequired: true, pendingToken: "pending-jwt-token" }));

    const codeInput = screen.getByLabelText("Authentication or backup code");
    fireEvent.change(codeInput, { target: { value: "000000" } });
    fireEvent.click(screen.getByRole("button", { name: "Verify and log in" }));

    act(() =>
      verifyTwoFactorCallbacks.onError?.({
        response: { status: UNAUTHORIZED_STATUS, data: { message: "Invalid verification code" } },
      }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid verification code");
    expect(toastError).toHaveBeenCalledWith("Invalid verification code");
  });

  it("returns to credentials form when clicking Back to login", async () => {
    render(<LoginPage />);
    act(() => loginMutationCallbacks.onSuccess?.({ twoFactorRequired: true, pendingToken: "pending-jwt-token" }));

    expect(screen.getByRole("heading", { name: "Two-factor authentication" })).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Back to login" }));

    expect(screen.getByRole("heading", { name: "Welcome back" })).toBeVisible();
    expect(screen.getByLabelText("Email")).toBeVisible();
  });

  it("tells an unverified account why it cannot log in", async () => {
    render(<LoginPage />);
    act(() =>
      loginMutationCallbacks.onError?.(
        {
          response: { status: FORBIDDEN_STATUS, data: { message: NOT_VERIFIED_MESSAGE } },
        },
        LOGIN_VARIABLES,
      ),
    );
    expect(toastError).toHaveBeenCalledWith(NOT_VERIFIED_MESSAGE);
    expect(await screen.findByText(/isn't verified yet/i)).toBeVisible();
  });

  it("surfaces a generic error toast for other bad logins", () => {
    render(<LoginPage />);
    act(() =>
      loginMutationCallbacks.onError?.(
        {
          response: { status: UNAUTHORIZED_STATUS, data: { message: "Wrong password" } },
        },
        LOGIN_VARIABLES,
      ),
    );
    expect(toastError).toHaveBeenCalledWith("Invalid email or password.");
  });

  it("resends verification email on click", async () => {
    render(<LoginPage />);
    act(() =>
      loginMutationCallbacks.onError?.(
        {
          response: { status: FORBIDDEN_STATUS, data: { message: NOT_VERIFIED_MESSAGE } },
        },
        LOGIN_VARIABLES,
      ),
    );

    fireEvent.click(screen.getByRole("button", { name: /resend verification email/i }));
    expect(resendMutate).toHaveBeenCalledWith({ data: { email: "player@example.com" } });
  });

  it("toasts confirmation after resending verification", () => {
    render(<LoginPage />);
    act(() => resendMutationCallbacks.onSuccess?.());
    expect(toastSuccess).toHaveBeenCalled();
  });

  it("toasts an error when resending verification fails", () => {
    render(<LoginPage />);
    act(() => resendMutationCallbacks.onError?.(new Error("network failure")));
    expect(toastError).toHaveBeenCalledWith("Couldn't send the email. Try again in a moment.");
  });
});
