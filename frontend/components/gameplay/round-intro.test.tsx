import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { RoundIntro } from "./round-intro";
import { recordServerTime, resetServerClock } from "@/lib/server-clock";

const CLIENT_NOW = Date.parse("2026-10-09T12:00:00Z");
const SERVER_AHEAD_MILLISECONDS = 30_000;
const INTRO_SECONDS = 3;
const MILLISECONDS_PER_SECOND = 1_000;

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(CLIENT_NOW);
  resetServerClock();
});

afterEach(() => vi.useRealTimers());

describe("RoundIntro", () => {
  it("counts the intro against the server clock when the browser clock is behind", () => {
    recordServerTime(new Date(CLIENT_NOW + SERVER_AHEAD_MILLISECONDS).toISOString());
    const endsAt = new Date(CLIENT_NOW + SERVER_AHEAD_MILLISECONDS + INTRO_SECONDS * MILLISECONDS_PER_SECOND).toISOString();
    const onFinished = vi.fn();

    render(<RoundIntro winConditionCardCount={8} roundNumber={1} countdownSeconds={INTRO_SECONDS} endsAt={endsAt} onFinished={onFinished} />);

    expect(screen.getByText(String(INTRO_SECONDS))).toBeVisible();
    act(() => vi.advanceTimersByTime(MILLISECONDS_PER_SECOND));
    expect(screen.getByText(String(INTRO_SECONDS - 1))).toBeVisible();
  });
});
