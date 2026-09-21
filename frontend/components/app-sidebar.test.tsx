import { act, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { AppSidebar } from "./app-sidebar";

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={typeof href === "string" ? href : "#"}>{children}</a>
  ),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/playlists",
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("next-themes", () => ({
  useTheme: () => ({ resolvedTheme: "light", setTheme: vi.fn() }),
}));

vi.mock("@tanstack/react-query", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@tanstack/react-query")>();
  return {
    ...actual,
    useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  };
});

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetActiveMembershipQueryKey: () => ["active-membership"],
  useCreateGroup: () => ({ mutate: vi.fn(), isPending: false }),
  useGetActiveMembership: () => ({ data: undefined }),
}));

vi.mock("@/hooks/use-bulk-import-realtime", () => ({
  useBulkImportRealtime: () => ({
    events: [
      { importJobId: "job-1", youtubeId: "video-1", outcome: "RESOLVED" },
      { importJobId: "job-1", youtubeId: "video-2", outcome: "ALREADY_KNOWN" },
    ],
    isConnected: true,
    reset: vi.fn(),
  }),
}));

vi.mock("@/components/nav-user", () => ({
  NavUser: () => <div aria-hidden="true" />,
}));

describe("AppSidebar import progress", () => {
  it("shows the progress popup with counts while importing", () => {
    render(<AppSidebar />);

    expect(screen.queryByTitle("Import in progress")).toBeNull();

    act(() => {
      window.dispatchEvent(
        new CustomEvent("playlist-import-progress", { detail: { active: true, playlistId: 7 } }),
      );
    });

    expect(screen.getByTitle("Import in progress")).toBeVisible();
    expect(screen.getByText("2 songs processed so far.")).toBeVisible();
    expect(screen.getByText("1 added")).toBeVisible();
    expect(screen.getByText("1 known")).toBeVisible();
  });
});
