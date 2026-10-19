import type { QueueJobDTO } from "@/hooks/models/queueJobDTO";

export const BACKFILL_QUEUE_REFRESH_INTERVAL_MILLISECONDS = 5_000;

export const DEFAULT_MAXIMUM_FAILED_TRIES = 6;

const DONE_STATUS = "DONE";
const FAILED_STATUS = "FAILED";
const WAITING_STATUS = "WAITING";

type JobTries = Pick<
  QueueJobDTO,
  "status" | "retrying" | "failedTries" | "maxTries" | "attempts" | "maxAttempts" | "lastError"
>;

// Failed tries come from failedTries and maxTries; attempts and maxAttempts carry the same
// numbers for older responses. A successful try never adds to the count.
export function failedTriesOf(job: JobTries) {
  return {
    failedTries: job.failedTries ?? job.attempts ?? 0,
    maximumFailedTries: job.maxTries ?? job.maxAttempts ?? DEFAULT_MAXIMUM_FAILED_TRIES,
  };
}

function timesText(count: number) {
  return count === 1 ? "once" : `${count} times`;
}

export function jobTriesText(job: JobTries) {
  const { failedTries, maximumFailedTries } = failedTriesOf(job);
  if (job.status === WAITING_STATUS && failedTries > 0) {
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
  const { failedTries } = failedTriesOf(job);
  return job.status === FAILED_STATUS || (job.status === WAITING_STATUS && failedTries > 0)
    ? job.lastError
    : undefined;
}
