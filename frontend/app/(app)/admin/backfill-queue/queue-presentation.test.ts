import { describe, expect, it } from "vitest";

import {
  DEFAULT_MAXIMUM_FAILED_TRIES,
  failedTriesOf,
  jobTriesText,
  visibleFailureReason,
} from "./queue-presentation";

describe("jobTriesText", () => {
  it("says a job that failed once and then succeeded was retried once", () => {
    expect(jobTriesText({ status: "DONE", attempts: 1 })).toBe("Retried once");
    expect(jobTriesText({ status: "DONE", attempts: 2 })).toBe("Retried 2 times");
  });

  it("says a job that succeeded straight away needed one try", () => {
    expect(jobTriesText({ status: "DONE", attempts: 0 })).toBe("First try");
    expect(jobTriesText({ status: "WAITING" })).toBe("Not retried");
  });

  it("shows the retry number against the maximum while retrying", () => {
    expect(jobTriesText({ status: "WAITING", retrying: true, attempts: 2 })).toBe(
      `Retry 2 of ${DEFAULT_MAXIMUM_FAILED_TRIES}`,
    );
    expect(jobTriesText({ status: "WAITING", retrying: true, attempts: 1, maxAttempts: 4 })).toBe("Retry 1 of 4");
  });

  it("counts the tries of a job that failed for good", () => {
    expect(jobTriesText({ status: "FAILED", attempts: 6 })).toBe("Failed after 6 tries");
    expect(jobTriesText({ status: "FAILED", attempts: 1 })).toBe("Failed after 1 try");
  });
});

describe("failedTriesOf", () => {
  it("reads failed tries from attempts and falls back to the default maximum", () => {
    expect(failedTriesOf({ attempts: 3 })).toEqual({
      failedTries: 3,
      maximumFailedTries: DEFAULT_MAXIMUM_FAILED_TRIES,
    });
    expect(DEFAULT_MAXIMUM_FAILED_TRIES).toBe(6);
  });
});

describe("visibleFailureReason", () => {
  it("never shows a failure reason on a finished job", () => {
    expect(visibleFailureReason({ status: "DONE", lastError: "Timeout" })).toBeUndefined();
    expect(visibleFailureReason({ status: "WAITING", lastError: "Timeout" })).toBe("Timeout");
    expect(visibleFailureReason({ status: "FAILED", lastError: "Not found" })).toBe("Not found");
  });
});
