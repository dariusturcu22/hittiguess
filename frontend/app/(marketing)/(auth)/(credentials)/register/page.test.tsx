import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import RegisterPage from "./page";

const registerMutate = vi.fn();
const registerPush = vi.fn();
let registerSearchParams = new URLSearchParams();
let registerMutationCallbacks: { onSuccess?: (data: unknown) => void } = {};

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: registerPush }),
  useSearchParams: () => registerSearchParams,
}));
vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useRegister: (options?: { mutation?: { onSuccess?: (data: unknown) => void } }) => {
    registerMutationCallbacks = options?.mutation ?? {};
    return { mutate: registerMutate, isPending: false };
  },
}));

describe("RegisterPage", () => {
  beforeEach(() => {
    registerMutate.mockReset();
    registerPush.mockReset();
    registerSearchParams = new URLSearchParams();
  });

  it("rejects mismatched passwords", async () => {
    render(<RegisterPage />);
    const [passwordField, confirmationField] = screen.getAllByLabelText(/password/i);
    fireEvent.change(passwordField, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.change(confirmationField, { target: { value: "DifferentHorseBatteryStaple1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Passwords do not match")).toBeInTheDocument();
    expect(registerMutate).not.toHaveBeenCalled();
  });

  it("submits matching valid details", async () => {
    render(<RegisterPage />);
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "player" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "player@example.com" } });
    const [passwordField, confirmationField] = screen.getAllByLabelText(/password/i);
    fireEvent.change(passwordField, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.change(confirmationField, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));
    await waitFor(() => expect(registerMutate).toHaveBeenCalled());
  });

  it("returns to the join prompt after signup when one was requested", async () => {
    registerSearchParams = new URLSearchParams("returnTo=/playlists/join/abc123");
    render(<RegisterPage />);
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "player" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "player@example.com" } });
    const [passwordField, confirmationField] = screen.getAllByLabelText(/password/i);
    fireEvent.change(passwordField, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.change(confirmationField, { target: { value: "CorrectHorseBatteryStaple1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));
    await waitFor(() => expect(registerMutate).toHaveBeenCalled());
    registerMutationCallbacks.onSuccess?.({});
    await waitFor(() => expect(registerPush).toHaveBeenCalledWith("/login?returnTo=%2Fplaylists%2Fjoin%2Fabc123"));
  });
});
