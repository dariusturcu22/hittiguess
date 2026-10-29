"use client";

import { useEffect, useState } from "react";

const SAMPLE_WINDOW_SIZE = 5;
const HOOK_DISABLED_TICK_MILLISECONDS = 0;

const clockOffsetSamples: number[] = [];
let lastRecordedServerTime: string | undefined;

// Each server payload carries the server's clock at the moment it was built. The
// sample is that clock minus this browser's clock, and the median of the recent
// samples smooths out the network delay on any single response.
export function recordServerTime(serverTime: string | undefined): void {
  if (!serverTime || serverTime === lastRecordedServerTime) return;
  const parsedServerTime = Date.parse(serverTime);
  if (!Number.isFinite(parsedServerTime)) return;
  lastRecordedServerTime = serverTime;
  clockOffsetSamples.push(parsedServerTime - Date.now());
  if (clockOffsetSamples.length > SAMPLE_WINDOW_SIZE) clockOffsetSamples.shift();
}

function medianClockOffset(): number {
  if (clockOffsetSamples.length === 0) return 0;
  const sortedOffsets = [...clockOffsetSamples].sort((firstOffset, secondOffset) => firstOffset - secondOffset);
  const middleIndex = Math.floor(sortedOffsets.length / 2);
  if (sortedOffsets.length % 2 === 1) return sortedOffsets[middleIndex];
  const lowerMiddleOffset = sortedOffsets[middleIndex - 1];
  return (lowerMiddleOffset + sortedOffsets[middleIndex]) / 2;
}

export function serverNow(): number {
  return Date.now() + medianClockOffset();
}

// Re-renders on the given tick so callers holding a server deadline can recount
// against the corrected clock without managing their own interval.
export function useServerNow(tickMilliseconds: number): number {
  const [, setTick] = useState(0);

  useEffect(() => {
    if (tickMilliseconds <= HOOK_DISABLED_TICK_MILLISECONDS) return;
    const interval = window.setInterval(() => setTick((tick) => tick + 1), tickMilliseconds);
    return () => window.clearInterval(interval);
  }, [tickMilliseconds]);

  return serverNow();
}

export function resetServerClock(): void {
  clockOffsetSamples.length = 0;
  lastRecordedServerTime = undefined;
}
