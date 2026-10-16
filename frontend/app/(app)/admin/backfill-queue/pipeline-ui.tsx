"use client";

import React from "react";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Button } from "@/components/shadcn/button";
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogCancel,
  AlertDialogAction,
} from "@/components/shadcn/alert-dialog";
import {
  useBumpRun,
  useRetryRun,
  useDiscardRun,
  usePauseStage,
  useResumeStage,
} from "@/hooks/generated/admin-pipeline/admin-pipeline";
import type { QueueStageDTO } from "@/hooks/models/queueStageDTO";
import type { QueueJobDTO } from "@/hooks/models/queueJobDTO";

export const PIPELINE_REFRESH_MS = 15_000;
export const PAGE_SIZE = 25;
const MILLISECONDS_PER_SECOND = 1000;
const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;
const RESPONSE_TIME_DECIMAL_PLACES = 1;
const ADMIN_QUERY_PREFIX = "/api/admin/";
export { STAGES } from "./pipeline-stages";
import { STAGES } from "./pipeline-stages";
export const panelClass =
  "rounded-[16px] border-[3px] border-border-strong bg-card shadow-lg";
export const pageClass =
  "relative flex h-full min-w-0 flex-1 flex-col overflow-y-auto bg-dotted px-14 pt-10 pb-9";
export const inputClass =
  "w-full rounded-[13px] border-2 border-border bg-background px-3 py-2 text-xs outline-none focus-visible:ring-2 focus-visible:ring-accent";
export function count(value?: number | null) {
  return value == null ? "Unknown" : value.toLocaleString();
}
export function duration(seconds?: number | null): string {
  if (seconds == null) return "Unknown";
  if (seconds < SECONDS_PER_MINUTE) return `${Math.ceil(seconds)}s`;
  const minutes = Math.ceil(seconds / SECONDS_PER_MINUTE);
  return minutes < MINUTES_PER_HOUR
    ? `${minutes} min`
    : `${Math.floor(minutes / MINUTES_PER_HOUR)}h ${minutes % MINUTES_PER_HOUR}m`;
}
export function responseTime(milliseconds?: number | null) {
  return milliseconds == null
    ? "Unknown"
    : `${(milliseconds / MILLISECONDS_PER_SECOND).toFixed(RESPONSE_TIME_DECIMAL_PLACES)}s`;
}
export function dateTime(value?: string) {
  return value
    ? new Date(value).toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "Unavailable";
}
export function stageLabel(stage?: string) {
  return STAGES.find((item) => item.id === stage)?.label ?? stage ?? "Unknown";
}
export function useAdminMutationFeedback() {
  const cache = useQueryClient();
  return {
    async onSuccess() {
      await cache.invalidateQueries({
        predicate(query) {
          const [path] = query.queryKey;
          return (
            typeof path === "string" && path.startsWith(ADMIN_QUERY_PREFIX)
          );
        },
      });
      toast.success("Saved");
    },
    onError(error: unknown) {
      const response =
        error && typeof error === "object" && "response" in error
          ? error.response
          : null;
      const data =
        response && typeof response === "object" && "data" in response
          ? response.data
          : null;
      const message =
        data &&
        typeof data === "object" &&
        "message" in data &&
        typeof data.message === "string"
          ? data.message
          : "The change failed. Try again.";
      toast.error(message);
    },
  };
}
export function SmallButton({
  className = "",
  danger,
  primary,
  ...props
}: React.ComponentProps<typeof Button> & {
  danger?: boolean;
  primary?: boolean;
}) {
  return (
    <Button
      type="button"
      size="sm"
      variant="secondary"
      className={`h-auto rounded-full px-3 py-2 font-display font-normal text-[10px] ${danger ? "bg-destructive/15 text-destructive" : primary ? "bg-primary text-primary-foreground shadow-sm" : "bg-secondary text-secondary-foreground"} ${className}`}
      {...props}
    />
  );
}
export function PageHeader({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle: string;
  children?: React.ReactNode;
}) {
  return (
    <header className="mb-5 flex shrink-0 items-start justify-between gap-5">
      <div>
        <div className="mb-1 flex items-center gap-2.5">
          <h1 className="font-display text-[30px] leading-[1.2] text-accent [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
            {title}
          </h1>
          <span className="rounded-full bg-destructive/15 px-[11px] py-[5px] font-display text-[10px] text-destructive">
            ADMIN
          </span>
        </div>
        <p className="text-[13px] text-muted-foreground">{subtitle}</p>
      </div>
      <div className="flex gap-2.5">{children}</div>
    </header>
  );
}
export function StatePill({ state }: { state?: string }) {
  return (
    <span
      className={`whitespace-nowrap rounded-full px-2.5 py-1 font-display text-[9px] ${state === "PAUSED" ? "bg-warning/20 text-foreground dark:text-warning" : state === "COOLING_DOWN" ? "bg-peach/20 text-peach" : "bg-green/20 text-green"}`}
    >
      {state?.replaceAll("_", " ") ?? "Unknown"}
    </span>
  );
}
export function StageToggle({
  stage,
  fullLabel = false,
}: {
  stage: QueueStageDTO;
  fullLabel?: boolean;
}) {
  const feedback = useAdminMutationFeedback();
  const pause = usePauseStage({ mutation: feedback });
  const resume = useResumeStage({ mutation: feedback });
  const paused = stage.state === "PAUSED";
  return (
    <SmallButton
      className="flex-1 px-1 py-1.5 text-[9px]"
      aria-label={`${paused ? "Resume" : "Pause"} ${stageLabel(stage.stage)}`}
      disabled={!stage.stage || pause.isPending || resume.isPending}
      onClick={() =>
        stage.stage && (paused ? resume : pause).mutate({ stage: stage.stage })
      }
    >
      {paused ? "Resume" : "Pause"}
      {fullLabel ? " queue" : ""}
    </SmallButton>
  );
}
export function ConfirmAction({
  label,
  title,
  description,
  pending,
  onConfirm,
  children,
  danger = true,
  primary = false,
  compact = false,
}: {
  label: string;
  title: string;
  description: string;
  pending?: boolean;
  onConfirm: () => Promise<unknown>;
  children?: React.ReactNode;
  danger?: boolean;
  primary?: boolean;
  compact?: boolean;
}) {
  const [open, setOpen] = React.useState(false);
  return (
    <>
      <SmallButton
        danger={danger}
        primary={primary}
        className={primary ? "flex-1" : compact ? "px-2 py-1.5 text-[9px]" : ""}
        disabled={pending}
        onClick={() => setOpen(true)}
      >
        {label}
      </SmallButton>
      <AlertDialog open={open} onOpenChange={setOpen}>
        <AlertDialogContent>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
          {children}
          <AlertDialogFooter>
            <AlertDialogCancel disabled={pending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              disabled={pending}
              onClick={(event) => {
                event.preventDefault();
                void onConfirm()
                  .then(() => setOpen(false))
                  .catch(() => {});
              }}
            >
              {pending ? "Saving…" : "Confirm"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
export function RunActions({
  id,
  status,
  retrying,
}: {
  id?: number;
  status?: string;
  retrying?: boolean;
}) {
  const feedback = useAdminMutationFeedback();
  const bump = useBumpRun({ mutation: feedback });
  const retry = useRetryRun({ mutation: feedback });
  const discard = useDiscardRun({ mutation: feedback });
  const pending = bump.isPending || retry.isPending || discard.isPending;
  if (id == null || (status !== "ACTIVE" && status !== "FAILED")) return null;
  return (
    <div className="flex flex-wrap justify-end gap-1.5">
      {status === "ACTIVE" && (
        <SmallButton
          disabled={pending}
          className="px-2 py-1.5 text-[9px]"
          onClick={() => bump.mutate({ id })}
        >
          Bump
        </SmallButton>
      )}
      {(status === "FAILED" || retrying) && (
        <SmallButton
          disabled={pending}
          className="px-2 py-1.5 text-[9px]"
          onClick={() => retry.mutate({ id })}
        >
          Retry
        </SmallButton>
      )}
      {status === "FAILED" && (
        <ConfirmAction
          label="Discard"
          compact
          title="Discard this run?"
          description="The run will be cancelled. Its history remains available."
          pending={pending}
          onConfirm={() => discard.mutateAsync({ id })}
        />
      )}
    </div>
  );
}
export function QueryState({
  loading,
  error,
  retry,
  empty,
}: {
  loading?: boolean;
  error?: boolean;
  retry: () => unknown;
  empty?: boolean;
}) {
  return loading ? (
    <p role="status" className="p-8 text-center text-sm text-muted-foreground">
      Loading queues…
    </p>
  ) : error ? (
    <div role="alert" className="p-8 text-center text-sm text-destructive">
      Couldn&apos;t load queue data.{" "}
      <SmallButton onClick={() => void retry()}>Retry</SmallButton>
    </div>
  ) : empty ? (
    <p className="p-8 text-center text-sm text-muted-foreground">
      No items in this view.
    </p>
  ) : null;
}
export function Pagination({
  page,
  total,
  pages,
  change,
}: {
  page: number;
  total?: number;
  pages?: number;
  change: (page: number) => void;
}) {
  const first = total ? page * PAGE_SIZE + 1 : 0;
  const last = Math.min((page + 1) * PAGE_SIZE, total ?? 0);
  return (
    <footer className="mt-auto flex items-center justify-between border-t-2 border-background px-5 py-2.5 text-[11px] text-icon-muted">
      <span>
        Showing {first} to {last} of {count(total ?? 0)}
      </span>
      <div className="flex gap-2.5">
        <SmallButton disabled={page === 0} onClick={() => change(page - 1)}>
          Previous
        </SmallButton>
        <SmallButton
          disabled={page + 1 >= (pages ?? 0)}
          onClick={() => change(page + 1)}
        >
          Next
        </SmallButton>
      </div>
    </footer>
  );
}
export function StageChips({ jobs }: { jobs?: QueueJobDTO[] }) {
  return (
    <div className="flex gap-1">
      {STAGES.map((stage) => {
        const job = jobs?.find((item) => item.stage === stage.id);
        const state = job?.retrying
          ? "RETRYING"
          : (job?.status ?? "NOT_QUEUED");
        const color =
          state === "DONE"
            ? "bg-green/20 text-green"
            : state === "IN_PROGRESS"
              ? "bg-blue/20 text-blue ring-2 ring-blue"
              : state === "RETRYING"
                ? "bg-peach/20 text-peach ring-2 ring-peach"
                : state === "FAILED"
                  ? "bg-destructive/20 text-destructive ring-2 ring-destructive"
                  : state === "SKIPPED"
                    ? "border-2 border-dashed border-icon-muted text-icon-muted"
                    : "bg-secondary text-muted-foreground";
        return (
          <span
            key={stage.id}
            title={`${stage.label}: ${state.replaceAll("_", " ")}`}
            aria-label={`${stage.label}: ${state.replaceAll("_", " ")}`}
            className={`flex h-[22px] w-[30px] shrink-0 items-center justify-center rounded-[7px] font-display text-[8px] ${color}`}
          >
            {stage.short}
          </span>
        );
      })}
    </div>
  );
}
export function QueueTable({ stages }: { stages: QueueStageDTO[] }) {
  return (
    <section className={`${panelClass} mt-[18px] flex min-h-0 flex-1 flex-col`}>
      <div className="flex items-center justify-between px-5 py-3.5">
        <h2 className="font-display text-sm">Queues</h2>
        <Link
          href="/admin/backfill-queue"
          className="text-xs font-semibold text-accent"
        >
          Open backfill queue →
        </Link>
      </div>
      <div className="overflow-auto">
        <table className="w-full table-fixed text-left text-xs">
          <thead className="border-b-2 border-border bg-card text-[10px] uppercase text-icon-muted">
            <tr>
              {[
                "Stage",
                "State",
                "Waiting",
                "In progress",
                "Failed",
                "Pace",
              ].map((label) => (
                <th
                  key={label}
                  className="px-5 py-[10px] font-semibold first:w-[190px] [&:nth-child(2)]:w-[135px] [&:nth-child(3)]:w-[105px] [&:nth-child(4)]:w-[105px] [&:nth-child(5)]:w-[105px]"
                >
                  {label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {STAGES.flatMap((definition) =>
              stages.filter((stage) => stage.stage === definition.id),
            ).map((stage) => (
              <tr
                key={stage.stage}
                className="border-b-2 border-background last:border-0"
              >
                <td className="px-5 py-[10px] font-semibold first:w-[190px] [&:nth-child(2)]:w-[135px] [&:nth-child(3)]:w-[105px] [&:nth-child(4)]:w-[105px] [&:nth-child(5)]:w-[105px]">
                  <Link
                    href={`/admin/backfill-queue/${STAGES.find((item) => item.id === stage.stage)?.slug}`}
                  >
                    {stageLabel(stage.stage)}
                  </Link>
                </td>
                <td className="px-5 py-[10px]">
                  <StatePill state={stage.state} />
                </td>
                <td className="px-5 py-[10px]">{count(stage.waiting)}</td>
                <td className="px-5 py-[10px] text-blue">
                  {count(stage.inProgress)}
                </td>
                <td className="px-5 py-[10px] text-destructive">
                  {count(stage.failed)}
                </td>
                <td className="px-5 py-[10px] text-muted-foreground">
                  {responseTime(stage.currentDelayMs)} interval,{" "}
                  {responseTime(stage.averageResponseMs)} avg
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
export function RejectedKeyBanners({ stages }: { stages: QueueStageDTO[] }) {
  return (
    <>
      {stages
        .filter(
          (stage) =>
            stage.state === "PAUSED" &&
            stage.pausedReason?.includes("API key rejected"),
        )
        .map((stage) => (
          <div
            role="alert"
            key={stage.stage}
            className="mb-[18px] flex items-center gap-3 rounded-[14px] border-2 border-destructive/30 bg-destructive/15 px-4 py-2.5 text-[13px]"
          >
            <span aria-hidden="true" className="text-lg text-destructive">
              ⚠
            </span>
            <p className="flex-1">
              {stageLabel(stage.stage)} is paused. {stage.pausedReason}
            </p>
            <div>
              <StageToggle stage={stage} />
            </div>
          </div>
        ))}
    </>
  );
}
