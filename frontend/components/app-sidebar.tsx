"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { useQueryClient } from "@tanstack/react-query";
import { History, Moon, Sun } from "lucide-react";

import { LogoBars } from "@/components/logo";
import { NavUser } from "@/components/nav-user";
import { useGetActiveMembership, useJoinGroup } from "@/hooks/generated/group-management/group-management";
import { useCreateGroupAndOpen } from "@/hooks/use-create-group-and-open";
import { LobbyLauncher, joinCodeErrorMessage } from "@/components/lobby-launcher";
import { getGetPlaylistQueryKey } from "@/hooks/generated/playlist-management/playlist-management";
import { useActiveImport } from "@/hooks/generated/playlist-import-jobs/playlist-import-jobs";
import { useGetCurrentUser } from "@/hooks/generated/user-management/user-management";
import { UserDetailDTORole } from "@/hooks/models";
import { ADMIN_NAVIGATION_ITEMS, isAdminNavigationItemActive } from "@/lib/admin-navigation";
import {
  PLAYLIST_IMPORT_STARTED_EVENT_NAME,
  clearActiveImportJob,
  countImportItems,
  loadActiveImportJob,
  type ActivePlaylistImportJob,
} from "@/lib/playlist-import-job";

const ACTIVE_IMPORT_REFRESH_MILLISECONDS = 5_000;

// Height steps keep rail controls reachable before scrolling is needed.
const RAIL_DIVIDER_CLASSES =
  "w-8 h-0.5 my-4 short:my-3 very-short:my-2 shrink-0 rounded-full bg-sidebar-border";

const ADMIN_RAIL_DIVIDER_CLASSES =
  "w-8 h-0.5 mt-3 mb-2 short:mt-2.5 short:mb-1.5 very-short:mt-1.5 shrink-0 rounded-full bg-destructive/40";

const RAIL_GROUP_GAP_CLASSES = "gap-6 short:gap-5 very-short:gap-3.5";

const ADMIN_RAIL_GROUP_GAP_CLASSES = "gap-2.5 short:gap-1.5 very-short:gap-1";

const ADMIN_RAIL_ICON_BASE_CLASSES =
  "flex h-[42px] w-[42px] short:size-[38px] very-short:size-9 shrink-0 items-center justify-center rounded-[14px] text-destructive transition-opacity";

const ADMIN_RAIL_ICON_ACTIVE_CLASSES = "bg-destructive/15 ring-2 ring-destructive";

const ADMIN_RAIL_ICON_INACTIVE_CLASSES = "opacity-70 hover:opacity-100";

const RAIL_ICON_BASE_CLASSES =
  "flex h-[46px] w-[46px] short:size-10 very-short:size-[38px] shrink-0 items-center justify-center rounded-[14px]";

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
  const [activeImport, setActiveImport] = React.useState<ActivePlaylistImportJob | null>(null);
  const { data: activeGroup } = useGetActiveMembership({
    query: { retry: false },
  });
  const { openGroup, createGroupAndOpen, isCreatingGroup } = useCreateGroupAndOpen();
  const joinGroup = useJoinGroup();
  const { data: currentUser } = useGetCurrentUser({ query: { retry: false } });
  const isAdmin = currentUser?.role === UserDetailDTORole.ADMIN;
  const [isLobbyMenuOpen, setIsLobbyMenuOpen] = React.useState(false);
  const [joinError, setJoinError] = React.useState("");
  const activeImportQuery = useActiveImport(activeImport?.playlistId ?? 0, {
    query: {
      enabled: activeImport !== null,
      retry: false,
      refetchInterval: ACTIVE_IMPORT_REFRESH_MILLISECONDS,
    },
  });

  function openGameLobby() {
    if (activeGroup?.id) {
      router.push(`/groups/${activeGroup.id}`);
      return;
    }
    setJoinError("");
    setIsLobbyMenuOpen((isOpen) => !isOpen);
  }

  function enterLobby(groupId: number | undefined) {
    setIsLobbyMenuOpen(false);
    openGroup(groupId);
  }

  function createLobby() {
    createGroupAndOpen();
  }

  function joinLobbyByCode(joinCode: string) {
    setJoinError("");
    joinGroup.mutate(
      { data: { joinCode } },
      {
        onSuccess: (group) => enterLobby(group.id),
        onError: (error) => setJoinError(joinCodeErrorMessage(error)),
      },
    );
  }

  React.useEffect(() => {
    setMounted(true);
    setActiveImport(loadActiveImportJob());
    const updateImportState = (event: Event) => {
      setActiveImport((event as CustomEvent<ActivePlaylistImportJob>).detail);
    };
    window.addEventListener(PLAYLIST_IMPORT_STARTED_EVENT_NAME, updateImportState);
    return () => window.removeEventListener(PLAYLIST_IMPORT_STARTED_EVENT_NAME, updateImportState);
  }, []);

  const finishedImportPlaylistId = activeImport?.playlistId;
  React.useEffect(() => {
    const isImportGone = activeImportQuery.isError || (!activeImportQuery.isLoading && !activeImportQuery.data);
    if (activeImport && isImportGone) {
      clearActiveImportJob();
      setActiveImport(null);
      if (finishedImportPlaylistId) {
        void queryClient.invalidateQueries({ queryKey: getGetPlaylistQueryKey(finishedImportPlaylistId) });
      }
    }
  }, [activeImport, activeImportQuery.data, activeImportQuery.isError, activeImportQuery.isLoading, finishedImportPlaylistId, queryClient]);

  const importItems = activeImportQuery.data?.items ?? [];
  const importCounts = countImportItems(importItems);
  const importProcessedCount = importCounts.settled;
  const importTotalCount = importCounts.total;
  const importResolvedCount = importItems.filter((item) => item.status === "RESOLVED").length;
  const importKnownCount = importItems.filter((item) => item.status === "ALREADY_KNOWN").length;

  const isPlaylistsActive = pathname?.startsWith("/playlists") ?? false;
  const isExploreActive = pathname?.startsWith("/explore") ?? false;
  const isGroupPage = pathname?.startsWith("/groups/") ?? false;

  return (
    <aside className="flex h-full min-h-0 w-[76px] shrink-0 flex-col items-center border-r-[3px] border-sidebar-border bg-sidebar py-5 short:py-4 very-short:py-3">
      <Link href="/playlists" aria-label="Go to playlists" className="flex h-[22px] shrink-0 items-center justify-center rounded focus-visible:ring-2 focus-visible:ring-ring">
        <LogoBars />
      </Link>

      <div className={RAIL_DIVIDER_CLASSES} />

      <div className="relative shrink-0">
        <button
          type="button"
          onClick={openGameLobby}
          aria-expanded={activeGroup ? undefined : isLobbyMenuOpen}
          className={`${RAIL_ICON_BASE_CLASSES} ${RAIL_ICON_INTERACTIVE_CLASSES}`}
          title={activeGroup ? "Open group lobby" : "Start or join a lobby"}
        >
          <PlayIcon />
        </button>
        {isLobbyMenuOpen && !activeGroup ? (
          <LobbyLauncher
            isCreating={isCreatingGroup}
            isJoining={joinGroup.isPending}
            joinError={joinError}
            onCreate={createLobby}
            onJoin={joinLobbyByCode}
            onClose={() => setIsLobbyMenuOpen(false)}
          />
        ) : null}
      </div>

      <div className={RAIL_DIVIDER_CLASSES} />

      <div data-testid="rail-scroll-area" className="min-h-0 w-full flex-1 overflow-y-auto overflow-x-hidden scrollbar-hidden">
        <div className="flex flex-col items-center pb-1">
          <div className={`flex flex-col ${RAIL_GROUP_GAP_CLASSES}`}>
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

          <Link href="/history" title="Game history" className={`${RAIL_ICON_BASE_CLASSES} ${pathname.startsWith("/history") ? RAIL_ICON_ACTIVE_CLASSES : RAIL_ICON_INTERACTIVE_CLASSES}`}>
            <History className="size-5" />
          </Link>

          {activeGroup?.id ? (
            <Link
              href={`/groups/${activeGroup.id}`}
              className={`${RAIL_ICON_BASE_CLASSES} border-2 border-primary text-primary ${isGroupPage ? "bg-primary/10 shadow-xs" : ""}`}
              title="Group lobby (active session)"
            >
              <GroupLobbyIcon />
            </Link>
          ) : null}

          {isAdmin ? (
            <>
              <div className={ADMIN_RAIL_DIVIDER_CLASSES} />
              <div className="mb-2 short:mb-1.5 font-display text-[8px] tracking-[1px] text-destructive very-short:hidden">ADMIN</div>
              <nav aria-label="Admin" className={`flex flex-col ${ADMIN_RAIL_GROUP_GAP_CLASSES}`}>
                {ADMIN_NAVIGATION_ITEMS.map((item) => {
                  const isActive = isAdminNavigationItemActive(item, pathname);
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      aria-label={`Admin: ${item.label}`}
                      aria-current={isActive ? "page" : undefined}
                      title={`Admin: ${item.label}`}
                      className={`${ADMIN_RAIL_ICON_BASE_CLASSES} ${
                        isActive ? ADMIN_RAIL_ICON_ACTIVE_CLASSES : ADMIN_RAIL_ICON_INACTIVE_CLASSES
                      }`}
                    >
                      <item.icon className="size-5" aria-hidden="true" />
                    </Link>
                  );
                })}
              </nav>
            </>
          ) : null}
        </div>
      </div>

      {activeImport && activeImportQuery.data && !activeImportQuery.isError ? (
        <div className="group relative mt-3 mb-3 shrink-0 short:mt-2 short:mb-2">
          <button type="button" onClick={() => router.push(`/playlists/${activeImport.playlistId}/import/youtube`)} className="flex size-[30px] cursor-pointer items-center justify-center rounded-full bg-primary text-primary-foreground" title="Import in progress" aria-describedby="import-progress-popup">
            <span className="size-3 rounded-full border-2 border-current border-t-transparent animate-spin" />
          </button>
          <div id="import-progress-popup" role="status" className="pointer-events-none absolute left-full top-1/2 z-30 ml-3 hidden w-52 -translate-y-1/2 rounded-2xl border-[3px] border-border-strong bg-card p-4 shadow-lg group-hover:block">
            <p className="font-display text-xs text-card-foreground">Importing playlist</p>
            <p className="mt-1 text-[11px] text-muted-foreground" aria-live="polite">
              {importTotalCount > 0 ? `${importProcessedCount} of ${importTotalCount} songs processed so far.` : "Starting..."}
            </p>
            <div className="mt-2 flex gap-1" aria-hidden="true">
              <span className="flex-1 rounded-full bg-secondary py-1 text-center text-[10px] font-bold text-muted-foreground">{importResolvedCount} added</span>
              <span className="flex-1 rounded-full bg-secondary py-1 text-center text-[10px] font-bold text-muted-foreground">{importKnownCount} known</span>
            </div>
          </div>
        </div>
      ) : null}

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

      <div className="h-4 shrink-0 short:h-3 very-short:h-2" />

      <NavUser />
    </aside>
  );
}
