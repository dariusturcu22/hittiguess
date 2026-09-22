import { act, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppSidebar } from "./app-sidebar";
import { PLAYLIST_IMPORT_STARTED_EVENT_NAME } from "@/lib/playlist-import-job";

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

let activeImportResponse: { data?: unknown; isError: boolean } = { isError: true };

vi.mock("@/hooks/generated/playlist-import-jobs/playlist-import-jobs", () => ({
  useActiveImport: () => activeImportResponse,
}));

vi.mock("@/components/nav-user", () => ({
  NavUser: () => <div aria-hidden="true" />,
}));

const RUNNING_JOB = {
  id: "job-1",
  playlistId: 7,
  status: "RUNNING",
  items: [
    { youtubeId: "video-1", status: "RESOLVED", songId: 101 },
    { youtubeId: "video-2", status: "ALREADY_KNOWN", songId: 102 },
    { youtubeId: "video-3", status: "PENDING" },
  ],
};

function renderSidebar() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <AppSidebar />
    </QueryClientProvider>,
  );
}

describe("AppSidebar import progress", () => {
  beforeEach(() => {
    window.localStorage.clear();
    activeImportResponse = { isError: true };
  });

  it("shows the progress popup with counts for the stored job", () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    activeImportResponse = { data: RUNNING_JOB, isError: false };
    renderSidebar();

    expect(screen.getByTitle("Import in progress")).toBeVisible();
    expect(screen.getByText("2 of 3 songs processed so far.")).toBeVisible();
    expect(screen.getByText("1 added")).toBeVisible();
    expect(screen.getByText("1 known")).toBeVisible();
  });

  it("appears when a new import starts", () => {
    activeImportResponse = { data: RUNNING_JOB, isError: false };
    renderSidebar();

    expect(screen.queryByTitle("Import in progress")).toBeNull();

    act(() => {
      window.dispatchEvent(
        new CustomEvent(PLAYLIST_IMPORT_STARTED_EVENT_NAME, {
          detail: { importJobId: "job-1", playlistId: 7 },
        }),
      );
    });

    expect(screen.getByTitle("Import in progress")).toBeVisible();
  });

  it("clears the indicator once the job is gone", () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    activeImportResponse = { data: RUNNING_JOB, isError: false };
    const view = renderSidebar();

    expect(screen.getByTitle("Import in progress")).toBeVisible();

    activeImportResponse = { isError: true };
    view.rerender(
      <QueryClientProvider client={new QueryClient()}>
        <AppSidebar />
      </QueryClientProvider>,
    );

    expect(screen.queryByTitle("Import in progress")).toBeNull();
    expect(window.localStorage.getItem("hittiguess-active-playlist-import")).toBeNull();
  });
});
