"use client";

import React from "react";
import Link from "next/link";
import { livePollingQueryOptions } from "../../live-admin-data";
import { jobTriesText, visibleFailureReason } from "../queue-presentation";
import {
  useJobs,
  useQueues,
  useRetryFailedRuns,
} from "@/hooks/generated/admin-pipeline/admin-pipeline";
import {
  STAGES,
  BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS,
  panelClass,
  pageClass,
  count,
  responseTime,
  duration,
  dateTime,
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
} from "../pipeline-ui";
import type { QueueStageDTO } from "@/hooks/models/queueStageDTO";

const PACE_SAMPLE_INTERVAL_MS = 60_000;
const MAX_PACE_SAMPLES = 20;
const PACE_BAR_HEIGHT = 40;
const MINIMUM_CHART_SCALE_MS = 1;
const JOB_FILTERS = [
  { label: "In progress", value: "IN_PROGRESS" },
  { label: "Waiting", value: "WAITING" },
  { label: "Retrying", value: "RETRYING" },
  { label: "Failed", value: "FAILED" },
  { label: "Done today", value: "DONE_TODAY" },
];
function PaceCard({ queue }: { queue: QueueStageDTO }) {
  const [samples, setSamples] = React.useState<number[]>([]);
  const response = React.useRef(queue.averageResponseMs);
  React.useEffect(() => {
    response.current = queue.averageResponseMs;
  }, [queue.averageResponseMs]);
  React.useEffect(() => {
    const timer = window.setInterval(() => {
      const value = response.current;
      if (value != null)
        setSamples((previous) => [...previous, value].slice(-MAX_PACE_SAMPLES));
    }, PACE_SAMPLE_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, []);
  const maximum = Math.max(...samples, MINIMUM_CHART_SCALE_MS);
  return (
    <div
      className={`${panelClass} w-[410px] shrink-0 px-[18px] py-3.5 @max-6xl:w-[340px] @max-5xl:w-full`}
    >
      <div className="mb-2 text-[11px] font-semibold uppercase text-muted-foreground">
        Pace, this visit
      </div>
      <div
        className="flex h-10 items-end gap-[3px]"
        role="img"
        aria-label={`${samples.length} response-time samples collected, one per minute`}
      >
        {samples.length ? (
          samples.map((sample, index) => (
            <div
              key={index}
              className="w-[9px] rounded-t bg-accent"
              style={{ height: `${(sample / maximum) * PACE_BAR_HEIGHT}px` }}
            />
          ))
        ) : (
          <p className="self-center text-[11px] text-icon-muted">
            Collecting one sample per minute
          </p>
        )}
      </div>
      <div className="mt-2 text-[11px] text-muted-foreground">
        {responseTime(queue.averageResponseMs)} avg response ·{" "}
        {responseTime(queue.currentDelayMs)} interval ·{" "}
        {duration(queue.estimatedDrainSeconds)} to drain
      </div>
    </div>
  );
}
export function StageQueuePage({ stage }: { stage: string }) {
  const definition = STAGES.find((item) => item.slug === stage)!;
  const [status, setStatus] = React.useState("WAITING");
  const [page, setPage] = React.useState(0);
  const overview = useQueues({
    query: livePollingQueryOptions(BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS),
  });
  const jobs = useJobs(
    stage,
    { status, page },
    {
      query: livePollingQueryOptions(
        BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS,
      ),
    },
  );
  const queue = overview.data?.stages?.find(
    (item) => item.stage === definition.id,
  );
  const feedback = useAdminMutationFeedback();
  const retry = useRetryFailedRuns({ mutation: feedback });
  return (
    <div className={pageClass}>
      <Link
        href="/admin/backfill-queue"
        className="mb-2 self-start rounded text-xs text-muted-foreground hover:text-accent focus-visible:ring-2 focus-visible:ring-accent"
      >
        Backfill queue / {definition.label}
      </Link>
      <PageHeader
        title={`${definition.label} queue`}
        subtitle="Jobs in this stage, in processing order within each lane."
      >
        <UpdatedAgo
          updatedAt={Math.min(overview.dataUpdatedAt, jobs.dataUpdatedAt) || undefined}
          refreshFailed={
            (overview.isError && Boolean(overview.data)) ||
            (jobs.isError && Boolean(jobs.data))
          }
        />
        {definition.id !== "EMBEDDING" && (
          <ConfirmAction
            label="Retry all failed"
            title={`Retry failed ${definition.label} runs?`}
            description="Failed runs in this stage will receive fresh attempts."
            pending={retry.isPending}
            onConfirm={() => retry.mutateAsync({ params: { stage } })}
          />
        )}
        {queue && <StageToggle stage={queue} fullLabel />}
      </PageHeader>
      <QueryState
        loading={overview.isLoading}
        error={overview.isError}
        retry={overview.refetch}
      />
      {queue && (
        <div className="mb-[18px] flex shrink-0 flex-wrap gap-5 @max-6xl:gap-3.5">
          <div className={`${panelClass} w-[150px] shrink-0 px-[18px] py-3.5`}>
            <div className="mb-3 text-[11px] font-semibold uppercase text-muted-foreground">
              State
            </div>
            <StatePill state={queue.state} />
            {queue.pausedReason && (
              <p className="mt-2 text-[10px] text-muted-foreground">
                {queue.pausedReason}
              </p>
            )}
          </div>
          {[
            ["Waiting", queue.waiting, ""],
            ["In progress", queue.inProgress, "text-blue"],
            ["Done today", queue.doneToday, "text-green"],
            ["Failed", queue.failed, "text-destructive"],
          ].map(([label, value, color]) => (
            <div
              key={label}
              className={`${panelClass} min-w-[110px] flex-1 px-[18px] py-3.5`}
            >
              <div className="mb-2 truncate text-[11px] font-semibold uppercase text-muted-foreground">
                {label}
              </div>
              <div
                className={`font-display ${typeof value === "number" ? "text-[26px]" : "text-sm"} ${color}`}
              >
                {count(typeof value === "number" ? value : null)}
              </div>
            </div>
          ))}
          <PaceCard key={stage} queue={queue} />
        </div>
      )}
      <div
        className="mb-3.5 flex shrink-0 flex-wrap items-center gap-2"
        role="group"
        aria-label="Job filters"
      >
        {JOB_FILTERS.map((filter) => (
          <SmallButton
            key={filter.value}
            aria-pressed={status === filter.value}
            className={`border-2 border-input px-3.5 py-1.5 font-sans text-xs font-semibold dark:border-transparent ${status === filter.value ? "bg-card text-accent ring-2 ring-accent" : "bg-card"}`}
            onClick={() => {
              setStatus(filter.value);
              setPage(0);
            }}
          >
            {filter.label}
          </SmallButton>
        ))}
        <span className="ml-auto text-[11px] text-icon-muted">
          User imports always go first
        </span>
      </div>
      {definition.id === "EMBEDDING" && (
        <p className="mb-3 text-xs text-muted-foreground">
          The embedding executor retains waiting work only. Lane, enqueue time,
          failure history, and done-today totals are unavailable.
        </p>
      )}
      <section className={`${panelClass} ${scrollingTableCardClass}`}>
        <div className={tableScrollAreaClass}>
          <table className="w-full min-w-[720px] table-fixed text-left text-xs">
            <thead className={stickyTableHeadClass}>
              <tr>
                <th className="w-[64px] px-3 py-2.5">Priority</th>
                <th className="w-[26%] px-5 py-2.5 @max-5xl:px-3">Song</th>
                <th className="w-[110px] px-2 py-2.5">Lane</th>
                <th className="w-[120px] px-2 py-2.5 @max-5xl:hidden">
                  Queued
                </th>
                <th className="w-[110px] px-2 py-2.5">Tries</th>
                <th className="px-2 py-2.5">Status</th>
                <th className="w-[160px] px-5 py-2.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {jobs.data?.items?.map((job) => (
                <tr key={job.id} className="border-b-2 border-background">
                  <td className="px-3 py-[9px] text-accent">
                    {job.priority ? "↑" : ""}
                  </td>
                  <td className="px-5 py-[9px] @max-5xl:px-3">
                    <div
                      className="truncate text-[13px] font-semibold"
                      title={job.title ?? job.youtubeId}
                    >
                      {job.title ?? job.youtubeId}
                    </div>
                    <div className="mt-1 truncate text-[11px] text-muted-foreground">
                      {job.artists?.join(", ")} · {job.youtubeId}
                    </div>
                  </td>
                  <td className="px-2 py-[9px]">
                    <span
                      className={`rounded-full px-2 py-1 text-[10px] font-semibold ${job.lane === "USER_IMPORT" ? "bg-pink/20 text-pink" : "bg-secondary text-muted-foreground"}`}
                    >
                      {job.lane === "USER_IMPORT"
                        ? "User import"
                        : job.lane === "BACKFILL"
                          ? "Backfill"
                          : "Unavailable"}
                    </span>
                  </td>
                  <td className="px-2 py-[9px] text-[11px] text-muted-foreground @max-5xl:hidden">
                    {dateTime(job.queuedAt)}
                  </td>
                  <td className="px-2 py-[9px] text-[11px]">
                    {jobTriesText(job)}
                  </td>
                  <td className="px-2 py-[9px] text-[11px] text-muted-foreground">
                    <div
                      className="line-clamp-2"
                      title={visibleFailureReason(job)}
                    >
                      {visibleFailureReason(job) ??
                        job.status?.replaceAll("_", " ")}
                    </div>
                    {job.retrying && (
                      <div>Next try {dateTime(job.nextAttemptAt)}</div>
                    )}
                  </td>
                  <td className="px-5 py-[9px]">
                    <RunActions
                      id={job.runId}
                      status={
                        job.status === "FAILED"
                          ? "FAILED"
                          : job.status === "WAITING" ||
                              job.status === "IN_PROGRESS"
                            ? "ACTIVE"
                            : "DONE"
                      }
                      retrying={job.retrying}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <QueryState
            loading={jobs.isLoading}
            error={jobs.isError}
            retry={jobs.refetch}
            empty={
              !jobs.isLoading && !jobs.isError && !jobs.data?.items?.length
            }
          />
        </div>
        <Pagination
          page={page}
          total={jobs.data?.totalElements}
          pages={jobs.data?.totalPages}
          change={setPage}
        />
      </section>
    </div>
  );
}
