import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import RegisterPage from "./page";

const registerMutate = vi.fn();

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useRegister: () => ({ mutate: registerMutate, isPending: false }),
}));

describe("RegisterPage", () => {
  beforeEach(() => registerMutate.mockReset());

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
});
