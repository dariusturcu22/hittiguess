import type { QueueJobDTO } from "@/hooks/models/queueJobDTO";

export const BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS = 5_000;

export const DEFAULT_MAXIMUM_FAILED_TRIES = 6;

const DONE_STATUS = "DONE";
const FAILED_STATUS = "FAILED";

type JobTries = Pick<QueueJobDTO, "status" | "retrying" | "attempts" | "maxAttempts" | "lastError">;

// The backend counts failed tries in attempts; a successful try does not add to it.
export function failedTriesOf(job: JobTries) {
  return {
    failedTries: job.attempts ?? 0,
    maximumFailedTries: job.maxAttempts ?? DEFAULT_MAXIMUM_FAILED_TRIES,
  };
}

function timesText(count: number) {
  return count === 1 ? "once" : `${count} times`;
}

export function jobTriesText(job: JobTries) {
  const { failedTries, maximumFailedTries } = failedTriesOf(job);
  if (job.retrying) {
    return `Retry ${failedTries} of ${maximumFailedTries}`;
  }
  if (job.status === FAILED_STATUS) {
    return `Failed after ${failedTries} ${failedTries === 1 ? "try" : "tries"}`;
  }
  if (failedTries === 0) {
    return job.status === DONE_STATUS ? "First try" : "Not retried";
  }
  return `Retried ${timesText(failedTries)}`;
}

export function visibleFailureReason(job: JobTries) {
  return job.status === DONE_STATUS ? undefined : job.lastError;
}
