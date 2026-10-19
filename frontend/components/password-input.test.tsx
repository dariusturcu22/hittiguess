import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PasswordInput } from "./password-input";

describe("PasswordInput", () => {
  it("reveals and hides the entered value", () => {
    render(<PasswordInput aria-label="Password" defaultValue="CorrectHorseBatteryStaple1!" />);

    const passwordField = screen.getByLabelText("Password");
    expect(passwordField).toHaveAttribute("type", "password");

    fireEvent.click(screen.getByRole("button", { name: "Show password" }));
    expect(passwordField).toHaveAttribute("type", "text");
    expect(passwordField).toHaveValue("CorrectHorseBatteryStaple1!");

    fireEvent.click(screen.getByRole("button", { name: "Hide password" }));
    expect(passwordField).toHaveAttribute("type", "password");
  });
});
