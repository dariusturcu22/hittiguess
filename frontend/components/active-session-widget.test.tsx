import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ActiveSessionWidget } from "./active-session-widget";

vi.mock("next/navigation", () => ({
  usePathname: () => "/playlists",
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  useGetActiveMembership: () => ({ data: { id: 4, status: "LOCKED" } }),
}));

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetActiveSessionForGroup: () => ({ data: { id: 9 } }),
  useGetSession: () => ({
    data: {
      currentRound: { roundNumber: 2, activePlayerId: 6 },
      players: [{ id: 6, displayName: "Sam" }],
    },
  }),
}));

describe("ActiveSessionWidget", () => {
  it("keeps the return control keyboard accessible and within the viewport on mobile", () => {
    render(<ActiveSessionWidget />);

    const returnLink = screen.getByRole("link", { name: "Return to game in progress" });

    expect(returnLink).toHaveAttribute("href", "/sessions/9");
    expect(returnLink.className).toContain("w-[min(310px,calc(100vw-2rem))]");
    expect(returnLink).toHaveTextContent("Sam is placing a card");
    expect(returnLink).toHaveTextContent("Round 2");
  });
});
