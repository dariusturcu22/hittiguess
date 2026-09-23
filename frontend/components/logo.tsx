"use client";

import { useEffect, useState } from "react";

const BAR_COUNT = 5;
const MIN_BAR_HEIGHT_PERCENT = 30;
const MAX_BAR_HEIGHT_PERCENT = 100;
const MIN_BAR_DURATION_MS = 900;
const MAX_BAR_DURATION_MS = 2100;

interface WaveformBar {
  heightPercent: number;
  durationMs: number;
  delayMs: number;
}

function randomWaveformBars(): WaveformBar[] {
  return Array.from({ length: BAR_COUNT }, () => ({
    heightPercent: MIN_BAR_HEIGHT_PERCENT + Math.random() * (MAX_BAR_HEIGHT_PERCENT - MIN_BAR_HEIGHT_PERCENT),
    durationMs: MIN_BAR_DURATION_MS + Math.random() * (MAX_BAR_DURATION_MS - MIN_BAR_DURATION_MS),
    delayMs: -Math.random() * MAX_BAR_DURATION_MS,
  }));
}

const STATIC_BARS: WaveformBar[] = Array.from({ length: BAR_COUNT }, () => ({
  heightPercent: MAX_BAR_HEIGHT_PERCENT,
  durationMs: MAX_BAR_DURATION_MS,
  delayMs: 0,
}));

type LogoBarsProps = {
  barWidthPx?: number;
  colorClassName?: string;
  containerClassName?: string;
};

export const LogoBars = ({
  barWidthPx = 5,
  colorClassName = "bg-[#499f36] dark:bg-[#a6e3a1]",
  containerClassName = "h-6",
}: LogoBarsProps = {}) => {
  // Randomized once after mount so server and client render identically;
  // every mount gets its own organic rhythm instead of a fixed loop.
  const [bars, setBars] = useState<WaveformBar[]>(STATIC_BARS);

  useEffect(() => {
    setBars(randomWaveformBars());
  }, []);

  return (
    <div className={`flex items-center gap-[3px] shrink-0 ${containerClassName}`}>
      {bars.map((bar, index) => (
        <span
          key={index}
          className={`origin-center rounded-full animate-waveform-bar ${colorClassName}`}
          style={{
            width: `${barWidthPx}px`,
            height: `${bar.heightPercent}%`,
            animationDuration: `${bar.durationMs}ms`,
            animationDelay: `${bar.delayMs}ms`,
          }}
        />
      ))}
    </div>
  );
};

type LogoIconProps = {
  // "lg" matches the 26px wordmark the auth and join-invite card mockups
  // use for the logo at the top of a centered card; "default" is the 18px
  // size used in page headers and rails.
  size?: "default" | "lg";
};

export const LogoIcon = ({ size = "default" }: LogoIconProps = {}) => {
  const wordmarkSizeClassName = size === "lg" ? "text-[26px]" : "text-lg";

  return (
    <div className="flex items-center gap-2">
      <LogoBars />
      <span
        className={`font-wordmark font-extrabold ${wordmarkSizeClassName} text-foreground`}
      >
        hittiguess
      </span>
    </div>
  );
};
