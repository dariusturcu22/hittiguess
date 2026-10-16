import { act, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppSidebar } from "./app-sidebar";
import { PLAYLIST_IMPORT_STARTED_EVENT_NAME } from "@/lib/playlist-import-job";

vi.mock("next/link", () => ({
  default: ({
    children,
    href,
    className,
    title,
    "aria-label": ariaLabel,
    "aria-current": ariaCurrent,
  }: {
    children: ReactNode;
    href: string;
    className?: string;
    title?: string;
    "aria-label"?: string;
    "aria-current"?: "page";
  }) => (
    <a
      href={typeof href === "string" ? href : "#"}
      className={className}
      title={title}
      aria-label={ariaLabel}
      aria-current={ariaCurrent}
    >
      {children}
    </a>
  ),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => currentPathname,
  useRouter: () => ({ push: routerPush }),
}));

const routerPush = vi.hoisted(() => vi.fn());
const createGroupMutate = vi.hoisted(() => vi.fn());
const joinGroupMutate = vi.hoisted(() => vi.fn());

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
  useCreateGroup: () => ({ mutate: createGroupMutate, isPending: false }),
  useJoinGroup: () => ({ mutate: joinGroupMutate, isPending: false }),
  useGetActiveMembership: () => ({ data: activeMembership }),
}));

let currentPathname = "/playlists";
let activeMembership: { id: number } | undefined;

let activeImportResponse: { data?: unknown; isError: boolean } = { isError: true };

vi.mock("@/hooks/generated/playlist-import-jobs/playlist-import-jobs", () => ({
  useActiveImport: () => activeImportResponse,
}));

let currentUserRole: string | undefined;

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({ data: currentUserRole ? { role: currentUserRole } : undefined }),
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
    routerPush.mockReset();
    activeImportResponse = { isError: true };
    currentPathname = "/playlists";
    activeMembership = undefined;
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

  it("opens the import progress page from the indicator", () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    activeImportResponse = { data: RUNNING_JOB, isError: false };
    renderSidebar();

    fireEvent.click(screen.getByTitle("Import in progress"));

    expect(routerPush).toHaveBeenCalledWith("/playlists/7/import/youtube");
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

describe("AppSidebar group button", () => {
  beforeEach(() => {
    currentPathname = "/playlists";
    activeMembership = undefined;
  });

  it("hides the group button without an active group", () => {
    const { container } = renderSidebar();

    expect(container.querySelector('a[href^="/groups/"]')).toBeNull();
  });

  it("shows the highlighted group button with an active group", () => {
    activeMembership = { id: 4 };
    const { container } = renderSidebar();

    const groupButton = container.querySelector('a[href="/groups/4"]');
    expect(groupButton).not.toBeNull();
    expect(groupButton?.className).toContain("border-primary");
  });

  it("strengthens the highlight on the group page", () => {
    activeMembership = { id: 4 };
    currentPathname = "/groups/4";
    const { container } = renderSidebar();

    expect(container.querySelector('a[href="/groups/4"]')?.className).toContain("bg-primary/10");
  });
});

describe("AppSidebar lobby menu", () => {
  beforeEach(() => {
    currentPathname = "/playlists";
    activeMembership = undefined;
    routerPush.mockReset();
    createGroupMutate.mockReset();
    joinGroupMutate.mockReset();
  });

  it("joins a lobby by its code and opens it", () => {
    joinGroupMutate.mockImplementation((_variables, options) => options?.onSuccess?.({ id: 12 }));
    renderSidebar();

    fireEvent.click(screen.getByTitle("Start or join a lobby"));
    fireEvent.change(screen.getByRole("textbox", { name: "Lobby code" }), { target: { value: "ab-cd9" } });
    expect(screen.getByRole("textbox", { name: "Lobby code" })).toHaveValue("ABCD");
    fireEvent.click(screen.getByRole("button", { name: "Join lobby" }));

    expect(joinGroupMutate).toHaveBeenCalledWith({ data: { joinCode: "ABCD" } }, expect.anything());
    expect(routerPush).toHaveBeenCalledWith("/groups/12");
  });

  it("explains an unknown code", () => {
    joinGroupMutate.mockImplementation((_variables, options) => options?.onError?.({ response: { status: 404 } }));
    renderSidebar();

    fireEvent.click(screen.getByTitle("Start or join a lobby"));
    fireEvent.change(screen.getByRole("textbox", { name: "Lobby code" }), { target: { value: "WXYZ" } });
    fireEvent.click(screen.getByRole("button", { name: "Join lobby" }));

    expect(screen.getByRole("alert")).toHaveTextContent("No lobby uses that code.");
    expect(routerPush).not.toHaveBeenCalled();
  });

  it("still creates a new lobby from the same menu", () => {
    createGroupMutate.mockImplementation((_variables, options) => options?.onSuccess?.({ id: 5 }));
    renderSidebar();

    fireEvent.click(screen.getByTitle("Start or join a lobby"));
    fireEvent.click(screen.getByRole("button", { name: "Create a lobby" }));

    expect(routerPush).toHaveBeenCalledWith("/groups/5");
  });
});

describe("AppSidebar admin group", () => {
  beforeEach(() => {
    currentPathname = "/playlists";
    currentUserRole = undefined;
    activeMembership = undefined;
    activeImportResponse = { isError: true };
  });

  it("hides the admin group for a regular account", () => {
    currentUserRole = "USER";
    renderSidebar();

    expect(screen.queryByText("ADMIN")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Admin: Dashboard")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Admin: Backfill queue")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Admin: Song catalog")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Admin: Reports")).not.toBeInTheDocument();
  });

  it("hides the admin group while the account is unknown", () => {
    renderSidebar();

    expect(screen.queryByText("ADMIN")).not.toBeInTheDocument();
  });

  it("shows the label and every admin link for an admin account", () => {
    currentUserRole = "ADMIN";
    renderSidebar();

    expect(screen.getByText("ADMIN")).toBeInTheDocument();
    expect(screen.getByLabelText("Admin: Dashboard")).toHaveAttribute("href", "/admin");
    expect(screen.getByLabelText("Admin: Backfill queue")).toHaveAttribute("href", "/admin/backfill-queue");
    expect(screen.getByLabelText("Admin: Song catalog")).toHaveAttribute("href", "/admin/songs");
    expect(screen.getByLabelText("Admin: Reports")).toHaveAttribute("href", "/admin/reports");
    expect(screen.getByTitle("Admin: Reports")).toBeInTheDocument();
  });

  it("marks only the link matching the pathname as active", () => {
    currentUserRole = "ADMIN";
    currentPathname = "/admin/reports";
    renderSidebar();

    expect(screen.getByLabelText("Admin: Reports")).toHaveAttribute("aria-current", "page");
    expect(screen.getByLabelText("Admin: Backfill queue")).not.toHaveAttribute("aria-current");
  });

  it("marks the song catalog link active on its route", () => {
    currentUserRole = "ADMIN";
    currentPathname = "/admin/songs";
    renderSidebar();

    expect(screen.getByLabelText("Admin: Song catalog")).toHaveAttribute("aria-current", "page");
    expect(screen.getByLabelText("Admin: Reports")).not.toHaveAttribute("aria-current");
    expect(screen.getByLabelText("Admin: Dashboard")).not.toHaveAttribute("aria-current");
  });

  it("marks the dashboard link active on the admin root only", () => {
    currentUserRole = "ADMIN";
    currentPathname = "/admin";
    renderSidebar();

    expect(screen.getByLabelText("Admin: Dashboard")).toHaveAttribute("aria-current", "page");
    expect(screen.getByLabelText("Admin: Song catalog")).not.toHaveAttribute("aria-current");
  });

  it("keeps the admin links in their configured order", () => {
    currentUserRole = "ADMIN";
    renderSidebar();

    const adminLinks = screen.getAllByRole("link", { name: /^Admin: / });
    expect(adminLinks.map((link) => link.getAttribute("aria-label"))).toEqual([
      "Admin: Dashboard",
      "Admin: Backfill queue",
      "Admin: Song catalog",
      "Admin: Reports",
    ]);
  });

  it("marks nothing active outside the admin routes", () => {
    currentUserRole = "ADMIN";
    renderSidebar();

    expect(screen.getByLabelText("Admin: Reports")).not.toHaveAttribute("aria-current");
    expect(screen.getByLabelText("Admin: Backfill queue")).not.toHaveAttribute("aria-current");
    expect(screen.getByLabelText("Admin: Song catalog")).not.toHaveAttribute("aria-current");
    expect(screen.getByLabelText("Admin: Dashboard")).not.toHaveAttribute("aria-current");
  });
});

describe("AppSidebar on short screens", () => {
  const MINIMUM_TOUCH_TARGET_PX = 36;
  const TAILWIND_SPACING_PX = 4;

  function smallestSizePx(className: string) {
    const arbitrarySizes = [...className.matchAll(/(?:size|h|w)-\[(?<pixels>\d+)px\]/g)].map(
      (match) => Number(match.groups?.pixels),
    );
    const scaleSizes = [...className.matchAll(/size-(?<steps>\d+)(?![\w[])/g)].map(
      (match) => Number(match.groups?.steps) * TAILWIND_SPACING_PX,
    );
    return Math.min(...arbitrarySizes, ...scaleSizes);
  }

  beforeEach(() => {
    currentPathname = "/admin";
    currentUserRole = "ADMIN";
    activeMembership = { id: 4 };
    activeImportResponse = { isError: true };
  });

  it("scrolls only the middle section and keeps the play button and theme toggle outside it", () => {
    renderSidebar();
    const scrollArea = screen.getByTestId("rail-scroll-area");

    expect(scrollArea.className).toContain("overflow-y-auto");
    expect(scrollArea.className).toContain("min-h-0");
    expect(scrollArea.className).toContain("scrollbar-hidden");
    expect(scrollArea).toContainElement(screen.getByTitle("Your playlists"));
    expect(scrollArea).toContainElement(screen.getByLabelText("Admin: Reports"));
    expect(scrollArea).not.toContainElement(screen.getByTitle("Open group lobby"));
    expect(scrollArea).not.toContainElement(screen.getByTitle("Toggle theme"));
  });

  it("keeps every rail target at least 36 px at every height step", () => {
    renderSidebar();
    const targets = [
      screen.getByTitle("Open group lobby"),
      screen.getByTitle("Your playlists"),
      screen.getByTitle("Game history"),
      screen.getByTitle("Group lobby (active session)"),
      screen.getByLabelText("Admin: Dashboard"),
      screen.getByTitle("Toggle theme"),
    ];

    for (const target of targets) {
      expect(target.className).toMatch(/very-short:/);
      expect(smallestSizePx(target.className)).toBeGreaterThanOrEqual(MINIMUM_TOUCH_TARGET_PX);
    }
  });

  it("keeps the admin group distinct and hides only its label on very short screens", () => {
    renderSidebar();

    expect(screen.getByText("ADMIN").className).toContain("very-short:hidden");
    expect(screen.getByText("ADMIN").className).toContain("text-destructive");
    expect(screen.getByLabelText("Admin: Dashboard").className).toContain("text-destructive");
    expect(screen.getByLabelText("Admin: Dashboard").className).toContain("ring-destructive");
  });
});
