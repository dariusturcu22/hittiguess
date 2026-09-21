"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { useQueryClient } from "@tanstack/react-query";
import { Moon, Sun } from "lucide-react";

import { LogoBars } from "@/components/logo";
import { NavUser } from "@/components/nav-user";
import { getGetActiveMembershipQueryKey, useCreateGroup, useGetActiveMembership } from "@/hooks/generated/group-management/group-management";
import { useBulkImportRealtime } from "@/hooks/use-bulk-import-realtime";

const RAIL_DIVIDER_CLASSES = "w-8 h-0.5 my-3.5 shrink-0 rounded-full bg-sidebar-border";

const RAIL_ICON_BASE_CLASSES =
  "flex h-[46px] w-[46px] items-center justify-center rounded-[14px]";

const RAIL_ICON_ACTIVE_CLASSES =
  "bg-accent text-accent-foreground shadow-xs";

// text-icon-muted lives here, not in the base classes shared with the active
// state: Tailwind's generated stylesheet order lets it win over
// text-accent-foreground when both are present on the same element, which
// left the active rail icon rendering in the inactive muted color.
const RAIL_ICON_INTERACTIVE_CLASSES =
  "text-icon-muted cursor-pointer transition-colors hover:text-sidebar-foreground";

function PlayIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
      <path d="M8 5v14l11-7z" />
    </svg>
  );
}

function PlaylistsIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <line x1="4" y1="6" x2="13" y2="6" />
      <line x1="4" y1="12" x2="13" y2="12" />
      <line x1="4" y1="18" x2="9" y2="18" />
      <circle cx="18" cy="16" r="2.3" />
      <path d="M20.3 16V6.5l-2.8 1" />
    </svg>
  );
}

function ExploreIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <circle cx="12" cy="12" r="9" />
      <polygon
        points="15.5 8.5 13 13 8.5 15.5 11 11"
        fill="currentColor"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function GroupLobbyIcon() {
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}

export function AppSidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = React.useState(false);
  const [isImporting, setIsImporting] = React.useState(false);
  const [importPlaylistId, setImportPlaylistId] = React.useState<number | null>(null);
  const { events: importEvents } = useBulkImportRealtime();
  const { data: activeGroup } = useGetActiveMembership({
    query: { retry: false },
  });
  const createGroup = useCreateGroup();

  function openGameLobby() {
    if (activeGroup?.id) {
      router.push(`/groups/${activeGroup.id}`);
      return;
    }

    createGroup.mutate(
      { data: {} },
      {
        onSuccess: (group) => {
          void queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
          router.push(`/groups/${group.id}`);
        },
      },
    );
  }

  React.useEffect(() => {
    setMounted(true);
    const updateImportState = (event: Event) => {
      const importState = (event as CustomEvent<{ active: boolean; playlistId?: number }>).detail;
      setIsImporting(importState.active);
      setImportPlaylistId(importState.playlistId ?? null);
    };
    window.addEventListener("playlist-import-progress", updateImportState);
    return () => window.removeEventListener("playlist-import-progress", updateImportState);
  }, []);

  const importProgressCount = importEvents.length;
  const importResolvedCount = importEvents.filter((event) => event.outcome === "RESOLVED").length;
  const importKnownCount = importEvents.filter((event) => event.outcome === "ALREADY_KNOWN").length;

  const isPlaylistsActive = pathname?.startsWith("/playlists") ?? false;
  const isExploreActive = pathname?.startsWith("/explore") ?? false;

  return (
    <aside className="flex w-[76px] shrink-0 flex-col items-center border-r-[3px] border-sidebar-border bg-sidebar py-5">
      <Link href="/playlists" aria-label="Go to playlists" className="flex h-[22px] items-center justify-center rounded focus-visible:ring-2 focus-visible:ring-ring">
        <LogoBars />
      </Link>

      <div className={RAIL_DIVIDER_CLASSES} />

      <button
        type="button"
        onClick={openGameLobby}
        disabled={createGroup.isPending}
        className={`${RAIL_ICON_BASE_CLASSES} ${RAIL_ICON_INTERACTIVE_CLASSES}`}
        title={activeGroup ? "Open group lobby" : "Create group lobby"}
      >
        <PlayIcon />
      </button>

      <div className={RAIL_DIVIDER_CLASSES} />

      <div className="flex flex-col gap-3.5">
        <Link
          href="/playlists"
          className={`${RAIL_ICON_BASE_CLASSES} ${
            isPlaylistsActive
              ? RAIL_ICON_ACTIVE_CLASSES
              : RAIL_ICON_INTERACTIVE_CLASSES
          }`}
          title="Your playlists"
        >
          <PlaylistsIcon />
        </Link>
        <Link
          href="/explore"
          className={`${RAIL_ICON_BASE_CLASSES} ${
            isExploreActive
              ? RAIL_ICON_ACTIVE_CLASSES
              : RAIL_ICON_INTERACTIVE_CLASSES
          }`}
          title="Explore public playlists"
        >
          <ExploreIcon />
        </Link>
      </div>

      <div className={RAIL_DIVIDER_CLASSES} />

      <Link
        href={activeGroup?.id ? `/groups/${activeGroup.id}` : "/playlists"}
        className={`${RAIL_ICON_BASE_CLASSES} ${
          pathname?.startsWith("/groups/") || activeGroup
            ? "border-2 border-primary text-primary"
            : RAIL_ICON_INTERACTIVE_CLASSES
        }`}
        title={activeGroup ? "Group lobby (active session)" : "Group lobby"}
      >
        <GroupLobbyIcon />
      </Link>

      {isImporting ? (
        <div className="group relative mt-3">
          <button type="button" onClick={() => importPlaylistId && router.push(`/playlists/${importPlaylistId}/import/youtube`)} className="flex size-[30px] cursor-pointer items-center justify-center rounded-full bg-primary text-primary-foreground" title="Import in progress" aria-describedby="import-progress-popup">
            <span className="size-3 rounded-full border-2 border-current border-t-transparent animate-spin" />
          </button>
          <div id="import-progress-popup" role="status" className="pointer-events-none absolute left-full top-1/2 z-30 ml-3 hidden w-52 -translate-y-1/2 rounded-2xl border-[3px] border-border-strong bg-card p-4 shadow-lg group-hover:block">
            <p className="font-display text-xs text-card-foreground">Importing playlist</p>
            <p className="mt-1 text-[11px] text-muted-foreground" aria-live="polite">
              {importProgressCount > 0 ? `${importProgressCount} song${importProgressCount === 1 ? "" : "s"} processed so far.` : "Starting..."}
            </p>
            <div className="mt-2 flex gap-1" aria-hidden="true">
              <span className="flex-1 rounded-full bg-secondary py-1 text-center text-[10px] font-bold text-muted-foreground">{importResolvedCount} added</span>
              <span className="flex-1 rounded-full bg-secondary py-1 text-center text-[10px] font-bold text-muted-foreground">{importKnownCount} known</span>
            </div>
          </div>
        </div>
      ) : null}

      <div className="flex-1" />

      <button
        type="button"
        onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
        className={`${RAIL_ICON_BASE_CLASSES} ${RAIL_ICON_INTERACTIVE_CLASSES}`}
        title="Toggle theme"
      >
        {mounted && resolvedTheme === "dark" ? (
          <Sun className="size-5" />
        ) : (
          <Moon className="size-5" />
        )}
      </button>

      <div className="h-4" />

      <NavUser />
    </aside>
  );
}
