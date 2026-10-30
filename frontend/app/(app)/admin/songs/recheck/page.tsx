"use client";

import React from "react";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { Checkbox as CheckboxPrimitive } from "radix-ui";
import { CheckIcon, ChevronDownIcon, ChevronRightIcon, MinusIcon } from "lucide-react";
import { toast } from "sonner";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogTitle,
} from "@/components/shadcn/alert-dialog";
import { Skeleton } from "@/components/shadcn/skeleton";
import { useRecheckCandidates } from "@/hooks/generated/admin-song-catalog/admin-song-catalog";
import { useRecheckSongs } from "@/hooks/generated/admin-song-pipeline/admin-song-pipeline";
import type { RecheckCandidateDTO } from "@/hooks/models/recheckCandidateDTO";

import { isAdminQueryKey } from "../../live-admin-data";
import {
  pageClass,
  scrollingTableCardClass,
  SmallButton,
  useAdminMutationFeedback,
} from "../../backfill-queue/pipeline-ui";
import { formatCatalogDate, STATUS_PRESENTATION } from "../song-catalog-presentation";
import {
  GROUPING_NOTE,
  groupByMonth,
  selectableSongIds,
  selectedCount,
  type SelectionState,
  selectionState,
  songCountLabel,
  toggleSongs,
} from "./recheck-presentation";

const SKELETON_ROW_COUNT = 8;
const QUEUE_HINT = "Progress shows on the backfill queue.";

const VERIFICATION_PRESENTATION: Record<
  RecheckCandidateDTO["verificationStatus"],
  { label: string; pillClassName: string }
> = {
  VERIFIED: STATUS_PRESENTATION.VERIFIED,
  NEEDS_REVIEW: STATUS_PRESENTATION.NEEDS_REVIEW,
  UNVERIFIED: { label: "UNVERIFIED", pillClassName: STATUS_PRESENTATION.IN_QUEUE.pillClassName },
  MANUAL_ENTRY: { label: "MANUAL ENTRY", pillClassName: STATUS_PRESENTATION.IN_QUEUE.pillClassName },
};

function SelectionBox({
  state,
  label,
  disabled,
  onChange,
}: {
  state: SelectionState;
  label: string;
  disabled?: boolean;
  onChange: (selected: boolean) => void;
}) {
  const checked = state === "all" ? true : state === "some" ? "indeterminate" : false;
  return (
    <CheckboxPrimitive.Root
      aria-label={label}
      checked={checked}
      disabled={disabled}
      onCheckedChange={(next) => onChange(next === true)}
      className="flex size-5 shrink-0 items-center justify-center rounded-md border-2 border-icon-muted bg-background text-primary-foreground outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-35 data-[state=checked]:border-accent data-[state=checked]:bg-accent data-[state=indeterminate]:border-accent data-[state=indeterminate]:bg-accent"
    >
      <CheckboxPrimitive.Indicator className="flex items-center justify-center">
        {state === "some" ? (
          <MinusIcon className="size-3.5" strokeWidth={4} />
        ) : (
          <CheckIcon className="size-3.5" strokeWidth={4} />
        )}
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}

function SongRow({
  song,
  selected,
  onToggle,
}: {
  song: RecheckCandidateDTO;
  selected: boolean;
  onToggle: (songId: number, selected: boolean) => void;
}) {
  const { label, pillClassName } = VERIFICATION_PRESENTATION[song.verificationStatus];
  const locked = song.inActiveGame;
  const title = song.title ?? `Song ${song.songId}`;
  const artists = song.artists.length > 0 ? song.artists.join(", ") : "unknown";
  return (
    <div
      data-testid="recheck-song-row"
      className={`flex items-center gap-[14px] border-b-2 border-background py-2.5 pr-5 pl-[54px] ${
        selected ? "bg-accent/8" : ""
      }`}
    >
      <SelectionBox
        state={selected ? "all" : "none"}
        label={`Select ${title}`}
        disabled={locked}
        onChange={(next) => onToggle(song.songId, next)}
      />
      <span
        className={`w-[290px] shrink-0 truncate text-[13px] font-semibold ${locked ? "opacity-45" : ""}`}
        title={title}
      >
        {title}
      </span>
      <span
        className={`w-[220px] shrink-0 truncate text-[13px] text-muted-foreground ${locked ? "opacity-45" : ""}`}
        title={artists}
      >
        {artists}
      </span>
      <span className={`w-[70px] shrink-0 text-[13px] ${locked ? "opacity-45" : ""}`}>
        {song.releaseYear ?? <span className="text-icon-muted">pending</span>}
      </span>
      <span className="w-[130px] shrink-0">
        <span
          className={`inline-block whitespace-nowrap rounded-full px-2.5 py-1 font-display text-[9px] leading-none ${pillClassName} ${locked ? "opacity-45" : ""}`}
        >
          {label}
        </span>
      </span>
      <span className="min-w-0 flex-1 text-right text-xs text-muted-foreground">
        {locked ? (
          <span className="inline-block whitespace-nowrap rounded-full bg-peach/18 px-2.5 py-1 font-display text-[9px] leading-none text-peach">
            IN AN ACTIVE GAME
          </span>
        ) : song.lastCheckedAt ? (
          formatCatalogDate(song.lastCheckedAt)
        ) : null}
      </span>
    </div>
  );
}

export default function RecheckSongsPage() {
  const { data: candidates, isLoading, isError, refetch } = useRecheckCandidates();
  const cache = useQueryClient();
  const feedback = useAdminMutationFeedback();
  const recheck = useRecheckSongs({ mutation: { onError: feedback.onError } });
  const [selectedIds, setSelectedIds] = React.useState<ReadonlySet<number>>(new Set());
  const [collapsedKeys, setCollapsedKeys] = React.useState<ReadonlySet<string>>(new Set());
  const [openedKeys, setOpenedKeys] = React.useState<ReadonlySet<string>>(new Set());
  const [confirming, setConfirming] = React.useState(false);

  const songs = React.useMemo(() => candidates ?? [], [candidates]);
  const groups = React.useMemo(() => groupByMonth(songs), [songs]);
  const newestKey = groups[0]?.key;
  const totalSelectable = React.useMemo(() => selectableSongIds(songs).length, [songs]);
  const lockedCount = songs.length - totalSelectable;
  const chosenCount = selectedIds.size;

  function isExpanded(key: string) {
    if (key === newestKey) return !collapsedKeys.has(key);
    return openedKeys.has(key);
  }

  function toggleExpanded(key: string) {
    if (key === newestKey) {
      setCollapsedKeys((current) => flip(current, key));
    } else {
      setOpenedKeys((current) => flip(current, key));
    }
  }

  function setSongs(songIds: number[], selected: boolean) {
    setSelectedIds((current) => toggleSongs(current, songIds, selected));
  }

  async function confirmRecheck() {
    const songIds = [...selectedIds].sort((first, second) => first - second);
    try {
      const result = await recheck.mutateAsync({ data: { songIds, all: false } });
      await cache.invalidateQueries({ predicate: (query) => isAdminQueryKey(query.queryKey) });
      toast.success(`Queued ${(result.affected ?? 0).toLocaleString()} songs for a recheck.`, {
        description: QUEUE_HINT,
      });
      setSelectedIds(new Set());
      setConfirming(false);
    } catch {
      // The mutation's onError already reported the failure.
    }
  }

  const selectAllState: SelectionState =
    chosenCount === 0 ? "none" : chosenCount === totalSelectable ? "all" : "some";

  return (
    <div className="relative flex h-full min-w-0 flex-1">
      <div className={`${pageClass} box-border`}>
        <div className="mb-[18px] flex shrink-0 items-start justify-between">
          <div>
            <div className="mb-1 flex items-center gap-2.5">
              <h1 className="font-display text-[30px] text-accent [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
                Recheck songs
              </h1>
              <span className="inline-flex rounded-full bg-destructive/15 px-[11px] py-[5px] font-display text-[10px] text-destructive">
                ADMIN
              </span>
            </div>
            <p className="text-[13px] text-muted-foreground">
              Choose which songs go back through the metadata pipeline.
            </p>
          </div>
          <Link
            href="/admin/songs"
            className="rounded-full bg-secondary px-[18px] py-2.5 font-display text-[11px] text-secondary-foreground"
          >
            Back to song catalog
          </Link>
        </div>

        <div className="mb-3.5 flex shrink-0 flex-wrap items-center gap-4">
          <label className="flex items-center gap-2.5 rounded-full border-2 border-border bg-card py-2 pr-4 pl-3">
            <SelectionBox
              state={selectAllState}
              label="Select all"
              disabled={totalSelectable === 0}
              onChange={(next) => setSongs(selectableSongIds(songs), next)}
            />
            <span className="text-[13px] font-semibold">Select all</span>
            <span className="text-xs text-muted-foreground">
              {chosenCount.toLocaleString()} of {totalSelectable.toLocaleString()} selected
            </span>
          </label>
          <span className="text-[11px] text-icon-muted">
            {GROUPING_NOTE}
            {lockedCount > 0
              ? ` ${songCountLabel(lockedCount)} in active games cannot be selected.`
              : ""}
          </span>
        </div>

        <div
          className={`${scrollingTableCardClass} rounded-[16px] border-[3px] border-border-strong bg-card shadow-lg box-border`}
        >
          <div className="flex shrink-0 items-center gap-[14px] border-b-2 border-background px-5 py-2.5 text-[10px] font-semibold uppercase tracking-[0.5px] text-icon-muted">
            <span className="w-[34px] shrink-0" />
            <span className="ml-5 w-[290px] shrink-0">Title</span>
            <span className="w-[220px] shrink-0">Artist</span>
            <span className="w-[70px] shrink-0">Year</span>
            <span className="w-[130px] shrink-0">Status</span>
            <span className="min-w-0 flex-1 text-right">Last checked</span>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto pb-24 scrollbar-thin-visible contain-size">
            {isLoading ? (
              <div data-testid="recheck-skeleton" className="flex flex-col gap-2 px-5 py-3">
                {Array.from({ length: SKELETON_ROW_COUNT }, (_unused, rowIndex) => (
                  <Skeleton key={rowIndex} className="h-6 w-full rounded-md" />
                ))}
              </div>
            ) : isError ? (
              <div className="flex flex-col items-center gap-3 px-5 py-10 text-center">
                <p className="text-[13px] text-destructive">Couldn&apos;t load the songs.</p>
                <SmallButton onClick={() => void refetch()}>Retry</SmallButton>
              </div>
            ) : groups.length === 0 ? (
              <div className="px-5 py-10 text-center text-[13px] text-muted-foreground">No songs yet</div>
            ) : (
              groups.map((group) => {
                const expanded = isExpanded(group.key);
                const groupSelectable = selectableSongIds(group.songs).length;
                const groupSelected = selectedCount(group.songs, selectedIds);
                const groupLocked = group.songs.length - groupSelectable;
                return (
                  <section key={group.key} data-testid="recheck-month">
                    <div className="flex items-center gap-[14px] border-b-2 border-background bg-secondary/60 px-5 py-3">
                      <SelectionBox
                        state={selectionState(group.songs, selectedIds)}
                        label={`Select ${group.label}`}
                        disabled={groupSelectable === 0}
                        onChange={(next) => setSongs(selectableSongIds(group.songs), next)}
                      />
                      <button
                        type="button"
                        aria-expanded={expanded}
                        aria-label={`${expanded ? "Collapse" : "Expand"} ${group.label}`}
                        onClick={() => toggleExpanded(group.key)}
                        className="flex min-w-0 flex-1 items-center gap-[14px] text-left"
                      >
                        {expanded ? (
                          <ChevronDownIcon className="size-4 text-muted-foreground" />
                        ) : (
                          <ChevronRightIcon className="size-4 text-muted-foreground" />
                        )}
                        <span className="font-display text-[13px] text-card-foreground">{group.label}</span>
                        <span className="text-xs text-muted-foreground">
                          {groupSelected.toLocaleString()} of {groupSelectable.toLocaleString()} selected
                          {groupLocked > 0 ? (
                            <span className="text-peach">, {groupLocked.toLocaleString()} in an active game</span>
                          ) : null}
                        </span>
                      </button>
                    </div>
                    {expanded
                      ? group.songs.map((song) => (
                          <SongRow
                            key={song.songId}
                            song={song}
                            selected={selectedIds.has(song.songId)}
                            onToggle={(songId, next) => setSongs([songId], next)}
                          />
                        ))
                      : null}
                  </section>
                );
              })
            )}
          </div>
        </div>

        {chosenCount > 0 ? (
          <div
            data-testid="recheck-action-bar"
            className="absolute right-14 bottom-[22px] left-14 flex items-center justify-between rounded-[16px] border-[3px] border-border-strong bg-card px-5 py-3.5 shadow-lg max-xl:right-8 max-xl:left-8"
          >
            <div>
              <div className="font-display text-sm text-card-foreground">
                {songCountLabel(chosenCount)} selected
              </div>
              <div className="mt-[3px] text-xs text-muted-foreground">
                Songs in an active game are never selected and would be skipped.
              </div>
            </div>
            <div className="flex items-center gap-3">
              <SmallButton disabled={recheck.isPending} onClick={() => setSelectedIds(new Set())}>
                Clear selection
              </SmallButton>
              <SmallButton primary disabled={recheck.isPending} onClick={() => setConfirming(true)}>
                Recheck {songCountLabel(chosenCount)}
              </SmallButton>
            </div>
          </div>
        ) : null}
      </div>

      <AlertDialog open={confirming} onOpenChange={setConfirming}>
        <AlertDialogContent>
          <AlertDialogTitle>Recheck {songCountLabel(chosenCount)}?</AlertDialogTitle>
          <AlertDialogDescription>
            These songs are queued for a new run through the metadata pipeline, using the external sources and AI. The
            runs go in at low priority behind user imports, so a large selection takes a while to drain. Songs used by
            an active game are skipped, and songs that already have a run in progress are not queued twice.
          </AlertDialogDescription>
          <p className="rounded-[13px] bg-background px-4 py-3 text-[13px] leading-normal text-card-foreground">
            Verified songs keep their year. When the new result differs, it is stored as a proposed year to review
            under Proposed year in the song catalog.
          </p>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={recheck.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={recheck.isPending}
              onClick={(event) => {
                event.preventDefault();
                void confirmRecheck();
              }}
            >
              {recheck.isPending ? "Queueing…" : "Confirm"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function flip(current: ReadonlySet<string>, key: string): Set<string> {
  const next = new Set(current);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  return next;
}
