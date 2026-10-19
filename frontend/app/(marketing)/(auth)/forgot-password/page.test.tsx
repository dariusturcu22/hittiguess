import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ForgotPasswordPage from "./page";

const requestMutate = vi.fn();

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useRequestPasswordReset: () => ({ mutate: requestMutate, isPending: false }),
}));

describe("ForgotPasswordPage", () => {
  beforeEach(() => {
    requestMutate.mockReset();
    requestMutate.mockImplementation((_args, options) => options?.onSuccess?.());
  });

  it("requests a reset link and confirms without revealing account existence", async () => {
    render(<ForgotPasswordPage />);

    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "player@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(requestMutate).toHaveBeenCalledWith(
      { data: { email: "player@example.com" } },
      expect.anything(),
    );
    await waitFor(() => expect(screen.getByText(/a reset link is on its way/i)).toBeVisible());
  });
});
