import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { recordServerTime, resetServerClock, serverNow } from "./server-clock";

const CLIENT_NOW = Date.parse("2026-10-09T12:00:00Z");
const MILLISECONDS_PER_SECOND = 1_000;

function recordOffsetSeconds(offsetSeconds: number): void {
  recordServerTime(new Date(CLIENT_NOW + offsetSeconds * MILLISECONDS_PER_SECOND).toISOString());
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(CLIENT_NOW);
  resetServerClock();
});

afterEach(() => vi.useRealTimers());

describe("serverNow", () => {
  it("matches the local clock until a payload records an offset", () => {
    expect(serverNow()).toBe(CLIENT_NOW);
  });

  it("uses the server clock from the most recent sample", () => {
    const SERVER_AHEAD_SECONDS = 30;
    recordOffsetSeconds(SERVER_AHEAD_SECONDS);

    expect(serverNow()).toBe(CLIENT_NOW + SERVER_AHEAD_SECONDS * MILLISECONDS_PER_SECOND);
  });

  it("takes the median of the recent samples so one delayed payload does not skew it", () => {
    [10, 20, 30, 40, 300].forEach(recordOffsetSeconds);

    expect(serverNow()).toBe(CLIENT_NOW + 30 * MILLISECONDS_PER_SECOND);
  });

  it("keeps only the last five samples", () => {
    [1, 2, 3, 4, 5, 60].forEach(recordOffsetSeconds);

    expect(serverNow()).toBe(CLIENT_NOW + 4 * MILLISECONDS_PER_SECOND);
  });

  it("ignores a repeated payload and an unparseable time", () => {
    const repeatedServerTime = new Date(CLIENT_NOW + 15 * MILLISECONDS_PER_SECOND).toISOString();
    recordServerTime(repeatedServerTime);
    vi.advanceTimersByTime(MILLISECONDS_PER_SECOND);
    recordServerTime(repeatedServerTime);
    recordServerTime("not a time");

    expect(serverNow()).toBe(CLIENT_NOW + MILLISECONDS_PER_SECOND + 15 * MILLISECONDS_PER_SECOND);
  });
});
