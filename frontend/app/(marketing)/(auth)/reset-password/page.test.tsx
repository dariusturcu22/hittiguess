import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ResetPasswordPage from "./page";

const confirmMutate = vi.fn();

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("token=reset-token"),
}));

vi.mock("@/hooks/use-password-reset", () => ({
  useConfirmPasswordReset: () => ({ mutate: confirmMutate, isPending: false }),
}));

describe("ResetPasswordPage", () => {
  beforeEach(() => {
    confirmMutate.mockReset();
    confirmMutate.mockImplementation((_args, options) => options?.onSuccess?.());
  });

  it("rejects mismatched passwords before calling the backend", () => {
    render(<ResetPasswordPage />);

    fireEvent.change(screen.getByLabelText("New password"), { target: { value: "new-password123" } });
    fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: "other-password123" } });
    fireEvent.click(screen.getByRole("button", { name: "Save new password" }));

    expect(screen.getByText("The passwords do not match.")).toBeVisible();
    expect(confirmMutate).not.toHaveBeenCalled();
  });

  it("confirms a matching password with the link token", async () => {
    render(<ResetPasswordPage />);

    fireEvent.change(screen.getByLabelText("New password"), { target: { value: "new-password123" } });
    fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: "new-password123" } });
    fireEvent.click(screen.getByRole("button", { name: "Save new password" }));

    expect(confirmMutate).toHaveBeenCalledWith(
      { data: { token: "reset-token", newPassword: "new-password123" } },
      expect.anything(),
    );
    await waitFor(() => expect(screen.getByText(/Password updated/)).toBeVisible());
  });
});
