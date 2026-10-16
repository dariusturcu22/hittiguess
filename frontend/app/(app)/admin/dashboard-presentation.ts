export const DASHBOARD_REFRESH_INTERVAL_MILLISECONDS = 10_000;

const MILLISECONDS_PER_MINUTE = 60_000;
const MINUTES_PER_HOUR = 60;
const FULL_PERCENT = 100;
const NO_PERCENT = 0;
const THOUSAND = 1_000;
const MILLION = 1_000_000;
const MILLION_DECIMAL_PLACES = 2;
const SMALL_COST_LIMIT_USD = 10;
const COST_DECIMAL_PLACES = 2;

const COUNT_FORMAT = new Intl.NumberFormat("en-US");
const SMALL_COST_FORMAT = new Intl.NumberFormat("en-US", {
  minimumFractionDigits: COST_DECIMAL_PLACES,
  maximumFractionDigits: COST_DECIMAL_PLACES,
});
const LARGE_COST_FORMAT = new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 });

export function formatCount(count: number) {
  return COUNT_FORMAT.format(count);
}

export function formatTokenCount(tokens: number) {
  if (tokens >= MILLION) {
    return `${(tokens / MILLION).toFixed(MILLION_DECIMAL_PLACES)} M`;
  }
  if (tokens >= THOUSAND) {
    return `${Math.round(tokens / THOUSAND)} K`;
  }
  return formatCount(tokens);
}

export function formatCostUsd(costUsd: number) {
  const format = costUsd < SMALL_COST_LIMIT_USD ? SMALL_COST_FORMAT : LARGE_COST_FORMAT;
  return `$${format.format(costUsd)}`;
}

export function formatCallCount(calls: number) {
  return `${formatCount(calls)} ${calls === 1 ? "call" : "calls"}`;
}

export function quotaProgressPercent(unitsUsed: number, dailyLimit: number) {
  if (dailyLimit <= 0) {
    return NO_PERCENT;
  }
  const percent = (unitsUsed / dailyLimit) * FULL_PERCENT;
  return Math.min(FULL_PERCENT, Math.max(NO_PERCENT, percent));
}

export function formatResetCountdown(resetsAt: string | undefined, nowMilliseconds: number) {
  if (!resetsAt) {
    return null;
  }
  const remainingMilliseconds = Math.max(0, Date.parse(resetsAt) - nowMilliseconds);
  const totalMinutes = Math.floor(remainingMilliseconds / MILLISECONDS_PER_MINUTE);
  const hours = Math.floor(totalMinutes / MINUTES_PER_HOUR);
  const minutes = totalMinutes % MINUTES_PER_HOUR;
  return `resets in ${hours} h ${minutes} min (midnight Pacific)`;
}
