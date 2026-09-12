import { describe, expect, test, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import RegisterPage from "./page";

const { registerMutateMock, pushMock, toastErrorMock } = vi.hoisted(() => ({
  registerMutateMock: vi.fn(),
  pushMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock("sonner", () => ({
  toast: { error: toastErrorMock },
}));

vi.mock(
  "@/hooks/generated/authentication-management/authentication-management",
  () => ({
    useRegister: () => ({ mutate: registerMutateMock, isPending: false }),
  }),
);

function fillRegisterForm({
  username = "someuser",
  email = "someone@example.com",
  password = "password123",
  confirmPassword = password,
}: {
  username?: string;
  email?: string;
  password?: string;
  confirmPassword?: string;
} = {}) {
  fireEvent.change(screen.getByPlaceholderText("johndoe"), {
    target: { value: username },
  });
  fireEvent.change(screen.getByPlaceholderText("johndoe@email.com"), {
    target: { value: email },
  });
  const [passwordField, confirmPasswordField] =
    screen.getAllByPlaceholderText("••••••••");
  fireEvent.change(passwordField, { target: { value: password } });
  fireEvent.change(confirmPasswordField, {
    target: { value: confirmPassword },
  });
}

beforeEach(() => {
  registerMutateMock.mockReset();
  pushMock.mockReset();
  toastErrorMock.mockReset();
});

describe("RegisterPage validation", () => {
  test("rejects a password and confirmation that do not match", async () => {
    render(<RegisterPage />);

    fillRegisterForm({ password: "password123", confirmPassword: "different456" });
    fireEvent.click(screen.getByRole("button", { name: "Create Account" }));

    await waitFor(() => {
      expect(screen.getByText("Passwords do not match")).toBeInTheDocument();
    });
    expect(registerMutateMock).not.toHaveBeenCalled();
  });

  test("rejects a username shorter than the minimum length", async () => {
    render(<RegisterPage />);

    fillRegisterForm({ username: "ab" });
    fireEvent.click(screen.getByRole("button", { name: "Create Account" }));

    await waitFor(() => {
      expect(registerMutateMock).not.toHaveBeenCalled();
    });
  });

  test("submits the account details once every field is valid", async () => {
    render(<RegisterPage />);

    fillRegisterForm();
    fireEvent.click(screen.getByRole("button", { name: "Create Account" }));

    await waitFor(() => {
      expect(registerMutateMock).toHaveBeenCalledWith({
        data: {
          username: "someuser",
          email: "someone@example.com",
          password: "password123",
        },
      });
    });
  });
});
