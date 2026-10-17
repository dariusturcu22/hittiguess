"use client";

import React from "react";
import {
  pageClass,
  QueueTable,
  RejectedKeyBanners,
  UpdatedAgo,
} from "./backfill-queue/pipeline-ui";
import { livePollingQueryOptions } from "./live-admin-data";

import { Skeleton } from "@/components/shadcn/skeleton";
import { useDashboard } from "@/hooks/generated/admin-dashboard/admin-dashboard";
import type { ProviderUsageDTO } from "@/hooks/models/providerUsageDTO";

import {
  DASHBOARD_REFRESH_INTERVAL_MILLISECONDS,
  formatCallCount,
  formatCostUsd,
  formatCount,
  formatResetCountdown,
  formatTokenCount,
  quotaProgressPercent,
} from "./dashboard-presentation";

const STAT_TILE_SKELETON_COUNT = 4;

const STAT_TILE_GRID_CLASSES = "grid grid-cols-4 gap-5 @max-4xl:gap-3.5 @max-4xl:grid-cols-2";

const USAGE_CARD_GRID_CLASSES = "grid grid-cols-2 gap-5 @max-5xl:grid-cols-1";

function StatTile({
  label,
  value,
  valueClassName,
  description,
}: {
  label: string;
  value: number;
  valueClassName: string;
  description: string;
}) {
  return (
    <div className="box-border min-w-0 rounded-[16px] border-[3px] border-border-strong bg-card px-[22px] py-[18px] shadow-lg">
      <div className="mb-2 truncate text-[11px] font-semibold uppercase tracking-[0.5px] text-muted-foreground">
        {label}
      </div>
      <div
        className={`font-display text-[34px] leading-tight ${valueClassName}`}
        data-testid="stat-value"
      >
        {formatCount(value)}
      </div>
      <div className="mt-1 text-[11px] text-icon-muted">{description}</div>
    </div>
  );
}

function ProviderFigure({
  label,
  usage,
  callsClassName,
}: {
  label: string;
  usage: ProviderUsageDTO | undefined;
  callsClassName: string;
}) {
  const todayTokens =
    (usage?.today?.inputTokens ?? 0) + (usage?.today?.outputTokens ?? 0);
  return (
    <div>
      <div className="mb-2 text-[11px] font-semibold uppercase tracking-[0.5px] text-muted-foreground">
        {label}
      </div>
      <div
        className={`font-display text-[22px] leading-tight ${callsClassName}`}
      >
        {formatCallCount(usage?.today?.calls ?? 0)}
      </div>
      <div className="mt-[3px] text-[11px] text-icon-muted">
        {formatTokenCount(todayTokens)} tokens today
      </div>
    </div>
  );
}

function DashboardSkeleton() {
  return (
    <div data-testid="dashboard-skeleton" className="flex flex-col gap-5">
      <div className={STAT_TILE_GRID_CLASSES}>
        {Array.from(
          { length: STAT_TILE_SKELETON_COUNT },
          (_unused, tileIndex) => (
            <Skeleton key={tileIndex} className="h-[124px] rounded-[16px]" />
          ),
        )}
      </div>
      <div className={USAGE_CARD_GRID_CLASSES}>
        <Skeleton className="h-[170px] rounded-[16px]" />
        <Skeleton className="h-[170px] rounded-[16px]" />
      </div>
    </div>
  );
}

export default function AdminDashboardPage() {
  const {
    data: dashboard,
    isLoading,
    isError,
    refetch,
    dataUpdatedAt,
  } = useDashboard({
    query: livePollingQueryOptions(DASHBOARD_REFRESH_INTERVAL_MILLISECONDS),
  });

  const [nowMilliseconds, setNowMilliseconds] = React.useState(() =>
    Date.now(),
  );
  React.useEffect(() => {
    const clockTimer = window.setInterval(
      () => setNowMilliseconds(Date.now()),
      DASHBOARD_REFRESH_INTERVAL_MILLISECONDS,
    );
    return () => window.clearInterval(clockTimer);
  }, []);

  const songs = dashboard?.songs;
  const queue = dashboard?.queue;
  const usage = dashboard?.usage;
  const youtubeUnitsUsed = usage?.youtube?.unitsUsedToday ?? 0;
  const youtubeDailyLimit = usage?.youtube?.dailyLimit ?? 0;
  const resetCountdown = formatResetCountdown(
    usage?.youtube?.resetsAt,
    nowMilliseconds,
  );

  return (
    <div className={`${pageClass} box-border`}>
      <div className="mb-5 flex shrink-0 flex-wrap items-end justify-between gap-x-5 gap-y-2">
        <div className="min-w-0">
          <div className="mb-1 flex items-center gap-2.5">
            <h1 className="font-display text-[30px] leading-[1.2] text-accent [text-shadow:3px_3px_0_var(--text-shadow-on-page)]">
              Dashboard
            </h1>
            <span className="inline-flex rounded-full bg-destructive/15 px-[11px] py-[5px] font-display text-[10px] text-destructive">
              ADMIN
            </span>
          </div>
          <p className="text-[13px] text-muted-foreground">
            How the catalog and its metadata pipeline are doing right now.
          </p>
        </div>
        <UpdatedAgo
          updatedAt={dataUpdatedAt}
          refreshFailed={isError && Boolean(dashboard)}
        />
      </div>

      {isLoading ? (
        <DashboardSkeleton />
      ) : isError && !dashboard ? (
        <div className="flex flex-col items-center gap-3 px-5 py-10 text-center">
          <p className="text-[13px] text-destructive">
            Couldn&apos;t load the dashboard.
          </p>
          <button
            type="button"
            onClick={() => void refetch()}
            className="rounded-full bg-secondary px-3 py-1.5 font-display text-[9px] text-secondary-foreground"
          >
            Retry
          </button>
        </div>
      ) : (
        <>
          {dashboard?.pipeline?.stages && (
            <RejectedKeyBanners stages={dashboard.pipeline.stages} />
          )}
          <div className={`mb-[18px] shrink-0 ${STAT_TILE_GRID_CLASSES}`}>
            <StatTile
              label="Fully verified"
              value={songs?.verified ?? 0}
              valueClassName="text-green"
              description="songs locked to a source-confirmed year"
            />
            <StatTile
              label="Needs review"
              value={songs?.needsReview ?? 0}
              valueClassName="text-peach dark:text-warning"
              description="needs review, unverified, or manual entry"
            />
            <StatTile
              label="With errors"
              value={songs?.error ?? 0}
              valueClassName="text-destructive"
              description="permanent failures, see the song catalog"
            />
            <StatTile
              label="In the queue"
              value={(queue?.waiting ?? 0) + (queue?.inProgress ?? 0)}
              valueClassName="text-blue"
              description={`${formatCount(queue?.inProgress ?? 0)} in progress, ${formatCount(queue?.waiting ?? 0)} waiting`}
            />
          </div>

          <div className={`shrink-0 ${USAGE_CARD_GRID_CLASSES}`}>
            <section className="box-border min-w-0 rounded-[16px] border-[3px] border-border-strong bg-card px-[22px] py-[18px] shadow-lg">
              <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                <h2 className="font-display text-sm text-card-foreground">
                  YouTube Data API
                </h2>
                {resetCountdown ? (
                  <span className="text-[11px] text-icon-muted">
                    {resetCountdown}
                  </span>
                ) : null}
              </div>
              <div className="mb-2.5 flex items-baseline gap-2">
                <span
                  className="font-display text-[28px] leading-tight text-blue"
                  data-testid="youtube-units-used"
                >
                  {formatCount(youtubeUnitsUsed)}
                </span>
                <span className="text-[13px] text-icon-muted">
                  / {formatCount(youtubeDailyLimit)} units today
                </span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-background">
                <div
                  data-testid="youtube-quota-bar"
                  className="h-full rounded-full bg-blue"
                  style={{
                    width: `${quotaProgressPercent(youtubeUnitsUsed, youtubeDailyLimit)}%`,
                  }}
                />
              </div>
              {dashboard?.pipeline && (
                <p className="mt-2 text-[11px] text-icon-muted">
                  Backfill stops below{" "}
                  {formatCount(dashboard.pipeline.youtubeReserveUnits ?? 0)}{" "}
                  units reserved for user imports.
                  {dashboard.pipeline.youtubeBackfillLimited
                    ? " Backfill is waiting for quota."
                    : ""}
                </p>
              )}
            </section>

            <section className="box-border min-w-0 rounded-[16px] border-[3px] border-border-strong bg-card px-[22px] py-[18px] shadow-lg">
              <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                <h2 className="font-display text-sm text-card-foreground">
                  AI usage
                </h2>
                <span className="text-[11px] text-icon-muted">
                  no cap, tracked with estimated cost
                </span>
              </div>
              <div className="flex flex-wrap gap-x-7 gap-y-3">
                <ProviderFigure
                  label="LLM, DeepInfra"
                  usage={usage?.deepInfra}
                  callsClassName="text-accent"
                />
                <ProviderFigure
                  label="Embeddings, OpenAI"
                  usage={usage?.openAi}
                  callsClassName="text-pink"
                />
                <div className="ml-auto text-right">
                  <div className="mb-2 text-[11px] font-semibold uppercase tracking-[0.5px] text-muted-foreground">
                    Estimated cost
                  </div>
                  <div
                    className="font-display text-[22px] leading-tight text-green"
                    data-testid="estimated-cost-today"
                  >
                    {formatCostUsd(usage?.estimatedCostTodayUsd ?? 0)}
                  </div>
                  <div className="mt-[3px] text-[11px] text-icon-muted">
                    {formatCostUsd(usage?.estimatedCostMonthUsd ?? 0)} this
                    month
                  </div>
                </div>
              </div>
            </section>
          </div>
          {dashboard?.pipeline?.stages && (
            <QueueTable stages={dashboard.pipeline.stages} />
          )}
        </>
      )}
    </div>
  );
}
