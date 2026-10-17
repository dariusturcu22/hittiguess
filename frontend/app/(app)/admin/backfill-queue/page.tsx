"use client";

import React from "react";
import Link from "next/link";
import { livePollingQueryOptions } from "../live-admin-data";
import { jobTriesText, visibleFailureReason } from "./queue-presentation";
import { toast } from "sonner";
import {
  useQueues,
  useBatches,
  useRuns,
  usePausePipeline,
  useResumePipeline,
  useEnqueuePipeline,
  useRunPipelineNow,
  useCancelBatch,
} from "@/hooks/generated/admin-pipeline/admin-pipeline";
import type { QueueStageDTO } from "@/hooks/models/queueStageDTO";
import {
  STAGES,
  BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS,
  panelClass,
  pageClass,
  inputClass,
  count,
  duration,
  responseTime,
  dateTime,
  stageLabel,
  useAdminMutationFeedback,
  SmallButton,
  PageHeader,
  UpdatedAgo,
  scrollingTableCardClass,
  tableScrollAreaClass,
  stickyTableHeadClass,
  StatePill,
  StageToggle,
  ConfirmAction,
  RunActions,
  QueryState,
  Pagination,
  StageChips,
} from "./pipeline-ui";

const FULL_PROGRESS_PERCENT = 100;

function StageCard({ stage }: { stage: QueueStageDTO }) {
  const definition = STAGES.find((item) => item.id === stage.stage);
  return (
    <section className={`${panelClass} flex min-w-0 flex-col px-3.5 pt-3.5 pb-3`}>
      <h2
        className="mb-2 truncate text-xs font-bold"
        title={stageLabel(stage.stage)}
      >
        {stageLabel(stage.stage)}
      </h2>
      <StatePill state={stage.state} />
      <div className="mt-3 text-[11px] uppercase tracking-wide text-icon-muted">
        Waiting
      </div>
      <div className="mb-2 font-display text-2xl leading-tight">
        {count(stage.waiting)}
      </div>
      <dl className="text-[11px] leading-[1.75] text-muted-foreground">
        {[
          ["In progress", count(stage.inProgress), "text-blue"],
          ["Done today", count(stage.doneToday), "text-green"],
          ["Failed", count(stage.failed), "text-destructive"],
          ["Avg response", responseTime(stage.averageResponseMs), ""],
          ["Time to drain", stage.state === "PAUSED" ? "Paused" : duration(stage.estimatedDrainSeconds), ""],
        ].map(([label, value, color]) => (
          <div className="flex justify-between gap-1" key={label}>
            <dt className="min-w-0 truncate" title={label}>
              {label}
            </dt>
            <dd className={`whitespace-nowrap ${color}`}>{value}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-2 mb-2.5 min-h-6 flex-1 text-[10px] leading-3 text-icon-muted">
        {stage.pausedReason ??
          (stage.cooldownUntil
            ? `Cooldown until ${dateTime(stage.cooldownUntil)}`
            : stage.stage === "EMBEDDING"
              ? "Verified songs only"
              : stage.stage === "WIKIPEDIA"
                ? "Only when sources disagree"
                : "User imports first")}
      </p>
      <div className="flex gap-1.5">
        <StageToggle stage={stage} />
        <SmallButton asChild className="flex-1 px-1 py-1.5 text-[9px]">
          <Link href={`/admin/backfill-queue/${definition?.slug}`}>Open</Link>
        </SmallButton>
      </div>
    </section>
  );
}
function BatchList() {
  const [page, setPage] = React.useState(0);
  const query = useBatches(
    { page },
    {
      query: livePollingQueryOptions(
        BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS,
      ),
    },
  );
  const feedback = useAdminMutationFeedback();
  const cancel = useCancelBatch({ mutation: feedback });
  return (
    <section className={`${panelClass} flex min-w-0 flex-1 flex-col @max-4xl:h-[360px] @max-4xl:flex-none`}>
      <h2 className="px-5 py-3.5 font-display text-sm">Batches</h2>
      <QueryState
        loading={query.isLoading}
        error={query.isError}
        retry={query.refetch}
        empty={!query.isLoading && !query.isError && !query.data?.items?.length}
      />
      <div className="min-h-0 flex-1 overflow-y-auto scrollbar-thin-visible contain-size">
        {query.data?.items?.map((batch) => {
          const completed =
            (batch.done ?? 0) + (batch.failed ?? 0) + (batch.cancelled ?? 0);
          const progress = batch.total
            ? Math.min(FULL_PROGRESS_PERCENT, (completed / batch.total) * FULL_PROGRESS_PERCENT)
            : 0;
          return (
            <div
              className="border-t-2 border-background px-5 py-[11px]"
              key={batch.id}
            >
              <div className="flex items-start gap-3">
                <div className="min-w-0 flex-1">
                  <h3
                    className="truncate text-[13px] font-semibold"
                    title={batch.label ?? `Batch ${batch.id}`}
                  >
                    {batch.label ?? `Batch ${batch.id}`}
                  </h3>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {dateTime(batch.createdAt)} ·{" "}
                    {count(batch.skippedDuplicates ?? 0)} duplicates skipped ·{" "}
                    {batch.cancelledAt
                      ? "Cancelled"
                      : `${duration(batch.estimatedDrainSeconds)} to drain`}
                  </p>
                </div>
                <span className="whitespace-nowrap text-xs">
                  {count(batch.done ?? 0)} / {count(batch.total ?? 0)}
                </span>
                {!batch.cancelledAt &&
                  (batch.waiting ?? 0) + (batch.inProgress ?? 0) > 0 &&
                  batch.id != null && (
                    <ConfirmAction
                      label="Cancel batch"
                      title="Cancel this batch?"
                      description="Waiting work will be cancelled. Results from running jobs will be discarded."
                      pending={cancel.isPending}
                      onConfirm={() => cancel.mutateAsync({ id: batch.id! })}
                    />
                  )}
              </div>
              <div
                role="progressbar"
                aria-label={`${batch.label ?? "Batch"} completed`}
                aria-valuenow={completed}
                aria-valuemin={0}
                aria-valuemax={batch.total ?? 0}
                className="mt-2 h-2 overflow-hidden rounded-full bg-background"
              >
                <div
                  className={`h-full ${(batch.failed ?? 0) > 0 ? "bg-destructive" : completed === batch.total ? "bg-green" : "bg-blue"}`}
                  style={{ width: `${progress}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>
      <Pagination
        page={page}
        total={query.data?.totalElements}
        pages={query.data?.totalPages}
        change={setPage}
      />
    </section>
  );
}
function EnqueueForm() {
  const [input, setInput] = React.useState("");
  const feedback = useAdminMutationFeedback();
  const enqueue = useEnqueuePipeline({ mutation: feedback });
  const run = useRunPipelineNow({ mutation: feedback });
  const entries = input
    .trim()
    .split(/[\s,]+/)
    .filter(Boolean);
  const submit = (immediate: boolean) => {
    if (immediate) {
      const [youtubeId] = entries;
      if (entries.length !== 1 || !youtubeId || youtubeId.includes("list=")) {
        toast.error("Enter one video ID or video link to run now.");
        return;
      }
      run.mutate({ data: { youtubeId } }, { onSuccess: () => setInput("") });
    } else {
      const [first] = entries;
      const data =
        entries.length === 1 && first?.includes("list=")
          ? { playlistLink: first }
          : { youtubeIds: entries };
      enqueue.mutate(
        { data },
        {
          onSuccess: (result) => {
            setInput("");
            toast.success(
              `${result.enqueuedYoutubeIds?.length ?? 0} queued, ${(result.skippedAlreadyKnownYoutubeIds?.length ?? 0) + (result.skippedAlreadyQueuedYoutubeIds?.length ?? 0)} duplicates skipped`,
            );
          },
        },
      );
    }
  };
  return (
    <section
      id="add-to-backlog"
      className={`${panelClass} flex w-[clamp(300px,36%,420px)] shrink-0 flex-col px-5 py-[18px] @max-4xl:w-full`}
    >
      <h2 className="mb-4 font-display text-sm">Add to backlog</h2>
      <label
        htmlFor="backlog-input"
        className="mb-2 block text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
      >
        Playlist link or YouTube IDs
      </label>
      <textarea
        id="backlog-input"
        className={`${inputClass} h-[108px] resize-y font-mono`}
        value={input}
        onChange={(event) => setInput(event.target.value)}
        placeholder="Paste a playlist link or video IDs"
      />
      <p className="mt-2.5 mb-3.5 text-[11px] leading-[1.6] text-muted-foreground">
        Known and already queued videos are skipped. Backfill uses the quota
        left after the user import reserve. Run one now puts one video first in
        the backfill lane.
      </p>
      <div className="mt-auto flex flex-wrap gap-2.5">
        <SmallButton
          primary
          disabled={
            !entries.length || enqueue.isPending || run.isPending
          }
          onClick={() => submit(false)}
        >
          Add to backlog
        </SmallButton>
        <SmallButton
          disabled={
            !entries.length || enqueue.isPending || run.isPending
          }
          onClick={() => submit(true)}
        >
          Run one now
        </SmallButton>
      </div>
    </section>
  );
}
const RUN_FILTERS = [
  { label: "All", state: undefined },
  { label: "In progress", state: "IN_PROGRESS" },
  { label: "Waiting", state: "WAITING" },
  { label: "Retrying", state: "RETRYING" },
  { label: "Failed", state: "FAILED" },
] as const;
export function RunList() {
  const [state, setState] = React.useState<string | undefined>();
  const [page, setPage] = React.useState(0);
  const query = useRuns(
    { state, page },
    {
      query: livePollingQueryOptions(
        BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS,
      ),
    },
  );
  const counts = query.data?.counts;
  const total = counts
    ? Object.values(counts).reduce((sum, value) => sum + (value ?? 0), 0)
    : 0;
  return (
    <>
      <div
        className="mb-3.5 flex flex-wrap items-center gap-2"
        role="group"
        aria-label="Run filters"
      >
        {RUN_FILTERS.map((filter) => (
          <SmallButton
            key={filter.label}
            aria-pressed={state === filter.state}
            className={`border-2 border-input px-3.5 py-1.5 font-sans text-xs font-semibold dark:border-transparent ${state === filter.state ? "bg-card text-accent ring-2 ring-accent" : "bg-card"}`}
            onClick={() => {
              setState(filter.state);
              setPage(0);
            }}
          >
            {filter.label}{" "}
            {count(filter.state ? (counts?.[filter.state] ?? 0) : total)}
          </SmallButton>
        ))}
        <span className="ml-auto text-[11px] text-icon-muted">
          Green: done · Blue: running · Grey: waiting · Dashed: skipped · Peach:
          retrying · Red: failed
        </span>
      </div>

      <section className={`${panelClass} ${scrollingTableCardClass}`}>
        <div className={tableScrollAreaClass}>
          <table className="w-full min-w-[720px] table-fixed text-left text-xs">
            <thead className={stickyTableHeadClass}>
              <tr>
                <th className="px-5 py-2.5">Song</th>
                <th className="w-[15%] px-2 py-2.5 @max-5xl:hidden">Batch</th>
                <th className="w-[250px] px-2 py-2.5">Stages</th>
                <th className="w-[28%] px-2 py-2.5">Note</th>
                <th className="w-[160px] px-5 py-2.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {query.data?.items?.map((run) => {
                const failure = run.jobs?.find((job) =>
                  visibleFailureReason(job),
                );
                const retriedJob = run.jobs?.find(
                  (job) => job.status === "DONE" && (job.attempts ?? 0) > 0,
                );
                const songTitle = run.title ?? run.youtubeId;
                return (
                  <tr key={run.id} className="border-b-2 border-background">
                    <td className="px-5 py-[9px]">
                      <div
                        className="truncate text-[13px] font-semibold"
                        title={songTitle}
                      >
                        {songTitle}
                      </div>
                      <div className="mt-1 truncate text-[11px] text-muted-foreground">
                        {run.artists?.join(", ")} · {run.youtubeId}
                      </div>
                    </td>
                    <td className="truncate px-2 py-[9px] text-[11px] text-muted-foreground @max-5xl:hidden">
                      {run.batchLabel ??
                        (run.lane === "USER_IMPORT"
                          ? "User import"
                          : "Single video")}
                    </td>
                    <td className="px-2 py-[9px]">
                      <StageChips jobs={run.jobs} />
                    </td>
                    <td className="px-2 py-[9px] text-[11px] text-muted-foreground">
                      {failure ? (
                        <>
                          <div
                            className="line-clamp-2"
                            title={failure.lastError}
                          >
                            {failure.lastError}
                          </div>
                          <div>
                            {jobTriesText(failure)}
                            {failure.retrying &&
                              `, next ${dateTime(failure.nextAttemptAt)}`}
                          </div>
                        </>
                      ) : run.status === "DONE" ? (
                        retriedJob ? (
                          `Metadata saved. ${stageLabel(retriedJob.stage)} ${jobTriesText(retriedJob).toLowerCase()}.`
                        ) : (
                          "Metadata saved"
                        )
                      ) : run.status === "CANCELLED" ? (
                        "Cancelled"
                      ) : (
                        "Waiting for required stages"
                      )}
                    </td>
                    <td className="px-5 py-[9px]">
                      <RunActions
                        id={run.id}
                        status={run.status}
                        retrying={run.jobs?.some((job) => job.retrying)}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <QueryState
            loading={query.isLoading}
            error={query.isError}
            retry={query.refetch}
            empty={
              !query.isLoading && !query.isError && !query.data?.items?.length
            }
          />
        </div>
        <Pagination
          page={page}
          total={query.data?.totalElements}
          pages={query.data?.totalPages}
          change={setPage}
        />
      </section>
    </>
  );
}
export default function BackfillQueuePage() {
  const [tab, setTab] = React.useState("Overview");
  const query = useQueues({
    query: livePollingQueryOptions(BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS),
  });
  const feedback = useAdminMutationFeedback();
  const pause = usePausePipeline({ mutation: feedback });
  const resume = useResumePipeline({ mutation: feedback });
  const allPaused =
    !!query.data?.stages?.length &&
    query.data.stages.every((stage) => stage.state === "PAUSED");
  return (
    <div className={pageClass}>
      <PageHeader
        title="Backfill queue"
        subtitle="Each stage runs at its own pace. User imports go first."
      >
        <UpdatedAgo
          updatedAt={query.dataUpdatedAt}
          refreshFailed={query.isError && Boolean(query.data)}
        />
        <SmallButton
          disabled={!query.data || pause.isPending || resume.isPending}
          onClick={() => (allPaused ? resume : pause).mutate()}
        >
          {allPaused ? "Resume all" : "Pause all"}
        </SmallButton>
        <SmallButton
          primary
          onClick={() => {
            setTab("Overview");
            window.setTimeout(
              () => document.getElementById("backlog-input")?.focus(),
              0,
            );
          }}
        >
          Add to backlog
        </SmallButton>
      </PageHeader>
      <div
        role="tablist"
        aria-label="Queue views"
        onKeyDown={(event) => {
          if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key))
            return;
          event.preventDefault();
          const next =
            event.key === "Home"
              ? "Overview"
              : event.key === "End"
                ? "Songs"
                : tab === "Overview"
                  ? "Songs"
                  : "Overview";
          setTab(next);
          document.getElementById(`tab-${next}`)?.focus();
        }}
        className="mb-[18px] flex gap-2"
      >
        {["Overview", "Songs"].map((label) => (
          <SmallButton
            key={label}
            role="tab"
            aria-selected={tab === label}
            tabIndex={tab === label ? 0 : -1}
            id={`tab-${label}`}
            aria-controls={`panel-${label}`}
            className={`px-[18px] py-[9px] text-xs ${tab === label ? "bg-accent text-accent-foreground shadow-sm" : "bg-transparent"}`}
            onClick={() => setTab(label)}
          >
            {label}
          </SmallButton>
        ))}
      </div>
      <div
        role="tabpanel"
        id={`panel-${tab}`}
        aria-labelledby={`tab-${tab}`}
        className="flex flex-1 flex-col"
      >
        {tab === "Songs" ? (
          <RunList />
        ) : (
          <>
            <QueryState
              loading={query.isLoading}
              error={query.isError}
              retry={query.refetch}
            />
            {query.data && (
              <>
                <div className="mb-5 grid shrink-0 grid-cols-7 gap-3.5 @max-6xl:grid-cols-4 @max-4xl:grid-cols-2">
                  {STAGES.map((stage) => {
                    const queue = query.data.stages?.find(
                      (item) => item.stage === stage.id,
                    );
                    return queue && <StageCard key={stage.id} stage={queue} />;
                  })}
                </div>
                <div className="flex min-h-[250px] flex-1 gap-6 @max-4xl:flex-col">
                  <BatchList />
                  <EnqueueForm />
                </div>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}
