import { describe, expect, it } from "vitest";

import {
  DEFAULT_MAXIMUM_FAILED_TRIES,
  failedTriesOf,
  jobTriesText,
  visibleFailureReason,
} from "./queue-presentation";

const NO_FAILED_TRIES = 0;
const ONE_FAILED_TRY = 1;
const TWO_FAILED_TRIES = 2;
const THREE_FAILED_TRIES = 3;
const CUSTOM_MAXIMUM_TRIES = 4;

describe("jobTriesText", () => {
  it("says a job that failed once and then succeeded was retried once", () => {
    expect(jobTriesText({ status: "DONE", attempts: ONE_FAILED_TRY })).toBe("Retried once");
    expect(jobTriesText({ status: "DONE", attempts: TWO_FAILED_TRIES })).toBe("Retried 2 times");
  });

  it("says a job that succeeded straight away needed one try", () => {
    expect(jobTriesText({ status: "DONE", attempts: NO_FAILED_TRIES })).toBe("First try");
    expect(jobTriesText({ status: "WAITING" })).toBe("Not retried");
  });

  it("shows the retry number against the maximum while retrying", () => {
    expect(jobTriesText({ status: "WAITING", retrying: true, attempts: TWO_FAILED_TRIES })).toBe(
      `Retry 2 of ${DEFAULT_MAXIMUM_FAILED_TRIES}`,
    );
    expect(jobTriesText({ status: "WAITING", retrying: true, attempts: ONE_FAILED_TRY, maxAttempts: CUSTOM_MAXIMUM_TRIES })).toBe("Retry 1 of 4");
  });

  it("counts the tries of a job that failed for good", () => {
    expect(jobTriesText({ status: "FAILED", attempts: DEFAULT_MAXIMUM_FAILED_TRIES })).toBe("Failed after 6 tries");
    expect(jobTriesText({ status: "FAILED", attempts: ONE_FAILED_TRY })).toBe("Failed after 1 try");
  });
});

describe("failedTriesOf", () => {
  it("prefers failedTries and maxTries over attempts and maxAttempts", () => {
    expect(
      failedTriesOf({ failedTries: TWO_FAILED_TRIES, maxTries: CUSTOM_MAXIMUM_TRIES, attempts: ONE_FAILED_TRY, maxAttempts: DEFAULT_MAXIMUM_FAILED_TRIES }),
    ).toEqual({ failedTries: TWO_FAILED_TRIES, maximumFailedTries: CUSTOM_MAXIMUM_TRIES });
  });

  it("reads failed tries from attempts and falls back to the default maximum", () => {
    expect(failedTriesOf({ attempts: THREE_FAILED_TRIES })).toEqual({
      failedTries: THREE_FAILED_TRIES,
      maximumFailedTries: DEFAULT_MAXIMUM_FAILED_TRIES,
    });
    expect(DEFAULT_MAXIMUM_FAILED_TRIES).toBe(6);
  });
});

describe("visibleFailureReason", () => {
  it("never shows a failure reason on a finished job", () => {
    expect(visibleFailureReason({ status: "DONE", lastError: "Timeout" })).toBeUndefined();
    expect(visibleFailureReason({ status: "WAITING", attempts: ONE_FAILED_TRY, lastError: "Timeout" })).toBe("Timeout");
    expect(visibleFailureReason({ status: "FAILED", lastError: "Not found" })).toBe("Not found");
  });

  it("hides stale failures outside failed jobs and waiting retries", () => {
    for (const status of ["IN_PROGRESS", "SKIPPED", "CANCELLED", "WAITING"] as const) {
      expect(visibleFailureReason({ status, lastError: "Old failure" })).toBeUndefined();
    }
  });
});
