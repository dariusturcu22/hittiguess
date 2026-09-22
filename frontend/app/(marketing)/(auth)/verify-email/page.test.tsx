import { render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import VerifyEmailPage from "./page";

const verifyMutate = vi.fn();

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("token=verify-token"),
}));

vi.mock("@/hooks/generated/authentication-management/authentication-management", () => ({
  useVerifyEmail: () => ({ mutate: verifyMutate, isPending: false }),
}));

describe("VerifyEmailPage", () => {
  beforeEach(() => {
    verifyMutate.mockReset();
    verifyMutate.mockImplementation((_args, options) => options?.onSuccess?.());
  });

  it("verifies the link token and points at login", async () => {
    render(<VerifyEmailPage />);

    expect(verifyMutate).toHaveBeenCalledWith(
      { data: { token: "verify-token" } },
      expect.anything(),
    );
    await waitFor(() => expect(screen.getByText("Email verified")).toBeVisible());
  });
});
