import { describe, expect, test, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import LoginPage from "./page";

const { loginMutateMock, pushMock, toastErrorMock } = vi.hoisted(() => ({
  loginMutateMock: vi.fn(),
  pushMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("sonner", () => ({
  toast: { error: toastErrorMock },
}));

vi.mock(
  "@/hooks/generated/authentication-management/authentication-management",
  () => ({
    useLogin: () => ({ mutate: loginMutateMock, isPending: false }),
  }),
);

beforeEach(() => {
  loginMutateMock.mockReset();
  pushMock.mockReset();
  toastErrorMock.mockReset();
});

describe("LoginPage validation", () => {
  test("rejects submission with both fields left blank", async () => {
    render(<LoginPage />);

    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() => {
      expect(loginMutateMock).not.toHaveBeenCalled();
    });
  });

  test("submits the entered email and password once both are filled in", async () => {
    render(<LoginPage />);

    fireEvent.change(screen.getByPlaceholderText("johndoe or johndoe@email.com"), {
      target: { value: "someone@example.com" },
    });
    fireEvent.change(screen.getByPlaceholderText("••••••••"), {
      target: { value: "password123" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

    await waitFor(() => {
      expect(loginMutateMock).toHaveBeenCalledWith({
        data: { email: "someone@example.com", password: "password123" },
      });
    });
  });
});
