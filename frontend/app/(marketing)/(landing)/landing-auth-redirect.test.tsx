import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { LandingAuthRedirect } from "./landing-auth-redirect";

const sessionProbes: unknown[][] = [];

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: (...args: unknown[]) => {
    sessionProbes.push(args);
    return { data: undefined };
  },
}));

describe("LandingAuthRedirect", () => {
  it("probes the session without triggering the login redirect", () => {
    render(<LandingAuthRedirect />);

    expect(sessionProbes).toContainEqual([
      {
        query: { retry: false },
        request: { skipAuthRedirect: true },
      },
    ]);
  });
});
