"use client";

import React from "react";
import { keepPreviousData } from "@tanstack/react-query";

import { useSearchCatalog } from "@/hooks/generated/admin-song-catalog/admin-song-catalog";
import type { AdminCatalogSongDTO } from "@/hooks/models/adminCatalogSongDTO";
import { Skeleton } from "@/components/shadcn/skeleton";

import { SongDetailDrawer } from "./song-detail-drawer";
import {
  addedByLabel,
  CATALOG_PAGE_SIZE,
  type CatalogStatus,
  effectiveSearchText,
  FIRST_PAGE,
  formatCatalogDate,
  formatCount,
  pipelineLabel,
  presentStatus,
  SEARCH_DEBOUNCE_MILLISECONDS,
  SEARCH_HINT,
  showingRangeText,
  STATUS_FILTERS,
} from "./song-catalog-presentation";

const SKELETON_ROW_COUNT = 8;

const COLUMN_CLASSES = {
  title: "w-[270px] shrink-0 min-w-0",
  artist: "w-[210px] shrink-0 min-w-0",
  year: "w-[70px] shrink-0",
  status: "w-[130px] shrink-0",
  pipeline: "w-[90px] shrink-0",
  added: "w-[120px] shrink-0",
  addedBy: "flex-1 min-w-0",
};

const ROW_CLASSES = "flex w-full items-center gap-[14px] px-5 py-2.5 border-b-2 border-background last:border-b-0 text-left";

function StatusPill({ status }: { status: AdminCatalogSongDTO["status"] }) {
  const { label, pillClassName } = presentStatus(status);
  return (
    <span
      className={`inline-block whitespace-nowrap rounded-full px-2.5 py-1 font-display text-[9px] leading-none ${pillClassName}`}
    >
      {label}
    </span>
  );
}

function SearchIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      className="shrink-0 text-icon-muted"
      aria-hidden="true"
    >
      <circle cx="11" cy="11" r="7" />
      <line x1="21" y1="21" x2="16.5" y2="16.5" />
    </svg>
  );
}

function CatalogRowCells({ song }: { song: AdminCatalogSongDTO }) {
  const hasTitle = Boolean(song.title);
  return (
    <>
      <span className={COLUMN_CLASSES.title}>
        {hasTitle ? (
          <span className="block truncate text-[13px] font-semibold text-card-foreground">{song.title}</span>
        ) : (
          <>
            <span className="block truncate text-[13px] text-card-foreground">yt: {song.youtubeId}</span>
            <span className="mt-0.5 block text-[11px] text-icon-muted">title not resolved yet</span>
          </>
        )}
      </span>
      <span className={`${COLUMN_CLASSES.artist} truncate text-[13px] text-muted-foreground`}>
        {song.artists.length > 0 ? song.artists.join(", ") : "unknown"}
      </span>
      <span className={`${COLUMN_CLASSES.year} text-[13px] text-card-foreground`}>
        {song.releaseYear ?? <span className="text-icon-muted">pending</span>}
      </span>
      <span className={COLUMN_CLASSES.status}>
        <StatusPill status={song.status} />
      </span>
      <span className={`${COLUMN_CLASSES.pipeline} text-xs text-muted-foreground`}>
        {pipelineLabel(song.pipeline)}
      </span>
      <span className={`${COLUMN_CLASSES.added} text-xs text-muted-foreground`}>
        {formatCatalogDate(song.createdAt)}
      </span>
      <span className={`${COLUMN_CLASSES.addedBy} truncate text-xs text-muted-foreground`}>
        {addedByLabel(song.addedBy, song.addedByKind)}
      </span>
    </>
  );
}

function CatalogRow({
  song,
  selected,
  onSelect,
}: {
  song: AdminCatalogSongDTO;
  selected: boolean;
  onSelect: (songId: number) => void;
}) {
  const songId = song.songId;
  if (songId == null) {
    return (
      <div className={ROW_CLASSES} data-testid="catalog-row">
        <CatalogRowCells song={song} />
      </div>
    );
  }
  return (
    <button
      type="button"
      onClick={() => onSelect(songId)}
      aria-pressed={selected}
      data-testid="catalog-row"
      className={`${ROW_CLASSES} hover:bg-accent/5 ${selected ? "bg-accent/10" : ""}`}
    >
      <CatalogRowCells song={song} />
    </button>
  );
}

export default function SongCatalogPage() {
  const [typedSearch, setTypedSearch] = React.useState("");
  const [appliedSearch, setAppliedSearch] = React.useState<string | undefined>(undefined);
  const [statusFilter, setStatusFilter] = React.useState<CatalogStatus | null>(null);
  const [page, setPage] = React.useState(FIRST_PAGE);
  const [selectedSongId, setSelectedSongId] = React.useState<number | null>(null);

  React.useEffect(() => {
    const debounceTimer = window.setTimeout(() => {
      const nextSearch = effectiveSearchText(typedSearch);
      if (nextSearch !== appliedSearch) {
        setAppliedSearch(nextSearch);
        setPage(FIRST_PAGE);
      }
    }, SEARCH_DEBOUNCE_MILLISECONDS);
    return () => window.clearTimeout(debounceTimer);
  }, [typedSearch, appliedSearch]);

  const { data: catalogPage, isLoading, isError, refetch } = useSearchCatalog(
    {
      query: appliedSearch,
      status: statusFilter ?? undefined,
      page,
      size: CATALOG_PAGE_SIZE,
    },
    { query: { placeholderData: keepPreviousData } },
  );

  const closeDrawer = React.useCallback(() => setSelectedSongId(null), []);

  const items = catalogPage?.items ?? [];
  const totalElements = catalogPage?.totalElements ?? 0;
  const totalPages = catalogPage?.totalPages ?? 0;
  const shownPage = catalogPage?.page ?? page;

  function selectStatus(nextStatus: CatalogStatus | null) {
    setStatusFilter(nextStatus);
    setPage(FIRST_PAGE);
  }

  return (
    <div className="relative flex min-w-0 flex-1 flex-col box-border bg-dotted px-14 pt-10 pb-9">
      <div className="mb-5 shrink-0">
        <div className="mb-1 flex items-center gap-2.5">
          <h1 className="font-display text-[30px] text-accent [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
            Song catalog
          </h1>
          <span className="inline-flex rounded-full bg-destructive/15 px-[11px] py-[5px] font-display text-[10px] text-destructive">
            ADMIN
          </span>
        </div>
        <p className="text-[13px] text-muted-foreground">Every song with its status, pipeline, and who added it.</p>
      </div>

      <div className="mb-3.5 flex shrink-0 items-center gap-3.5">
        <label className="box-border flex w-[460px] items-center gap-2.5 rounded-md border-2 border-secondary bg-background px-4 py-3">
          <SearchIcon />
          <input
            type="search"
            value={typedSearch}
            onChange={(event) => setTypedSearch(event.target.value)}
            placeholder="Search title or artist"
            aria-label="Search title or artist"
            className="min-w-0 flex-1 bg-transparent text-sm text-card-foreground outline-none placeholder:text-icon-muted"
          />
        </label>
        <span className="text-[11px] text-icon-muted">{SEARCH_HINT}</span>
      </div>

      <div className="mb-3.5 flex shrink-0 gap-2" role="group" aria-label="Filter by status">
        {STATUS_FILTERS.map((filter) => {
          const isActive = statusFilter === filter.status;
          const count = catalogPage?.counts?.[filter.countKey];
          return (
            <button
              key={filter.label}
              type="button"
              aria-pressed={isActive}
              onClick={() => selectStatus(filter.status)}
              className={`whitespace-nowrap rounded-full border-2 px-3.5 py-[7px] text-xs font-semibold ${
                isActive
                  ? "border-accent bg-accent/12 text-accent dark:bg-accent/16"
                  : "border-secondary bg-card text-muted-foreground dark:border-transparent"
              }`}
            >
              {filter.label}
              {count != null ? ` ${formatCount(count)}` : ""}
            </button>
          );
        })}
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[16px] border-[3px] border-border-strong bg-card shadow-lg box-border">
        <div className="flex shrink-0 items-center gap-[14px] border-b-2 border-background px-5 py-2.5 text-[10px] font-semibold uppercase tracking-[0.5px] text-icon-muted">
          <span className={COLUMN_CLASSES.title}>Title</span>
          <span className={COLUMN_CLASSES.artist}>Artist</span>
          <span className={COLUMN_CLASSES.year}>Year</span>
          <span className={COLUMN_CLASSES.status}>Status</span>
          <span className={COLUMN_CLASSES.pipeline}>Pipeline</span>
          <span className={COLUMN_CLASSES.added}>Added</span>
          <span className={COLUMN_CLASSES.addedBy}>Added by</span>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {isLoading ? (
            <div data-testid="song-catalog-skeleton" className="flex flex-col gap-2 px-5 py-3">
              {Array.from({ length: SKELETON_ROW_COUNT }, (_unused, rowIndex) => (
                <Skeleton key={rowIndex} className="h-6 w-full rounded-md" />
              ))}
            </div>
          ) : isError ? (
            <div className="flex flex-col items-center gap-3 px-5 py-10 text-center">
              <p className="text-[13px] text-destructive">Couldn&apos;t load the song catalog.</p>
              <button
                type="button"
                onClick={() => void refetch()}
                className="rounded-full bg-secondary px-3 py-1.5 font-display text-[9px] text-secondary-foreground"
              >
                Retry
              </button>
            </div>
          ) : items.length === 0 ? (
            <div className="px-5 py-10 text-center text-[13px] text-muted-foreground">No songs match</div>
          ) : (
            items.map((song) => (
              <CatalogRow
                key={song.id}
                song={song}
                selected={song.songId != null && song.songId === selectedSongId}
                onSelect={setSelectedSongId}
              />
            ))
          )}
        </div>

        <div className="flex shrink-0 items-center justify-between border-t-2 border-background px-5 py-2.5 text-[11px] text-icon-muted">
          <span>{showingRangeText(shownPage, items.length, totalElements)}</span>
          <span className="flex gap-2.5">
            <button
              type="button"
              onClick={() => setPage((currentPage) => Math.max(FIRST_PAGE, currentPage - 1))}
              disabled={page <= FIRST_PAGE}
              className="rounded-full bg-secondary px-3 py-1.5 font-display text-[9px] text-secondary-foreground disabled:cursor-not-allowed disabled:opacity-50"
            >
              Previous
            </button>
            <button
              type="button"
              onClick={() => setPage((currentPage) => currentPage + 1)}
              disabled={page + 1 >= totalPages}
              className="rounded-full bg-secondary px-3 py-1.5 font-display text-[9px] text-secondary-foreground disabled:cursor-not-allowed disabled:opacity-50"
            >
              Next
            </button>
          </span>
        </div>
      </div>

      {selectedSongId != null ? <SongDetailDrawer songId={selectedSongId} onClose={closeDrawer} /> : null}
    </div>
  );
}
