"use client";

import * as React from "react";

// Mirrors docs/design/source/*.dc.html's own embedded animation exactly:
// each bar oscillates independently on a sine wave, phase-shifted from the
// others, rather than a uniform pulse stepped through in sequence. The
// mockups tick this every 120ms and compute height as
// 12 + 6 * sin(frame * 0.35 + phase); frame here is derived from elapsed
// time instead of a fixed-interval counter so the motion holds its speed
// regardless of the browser's actual repaint rate.
const BAR_PHASES = [0, 1.6, 3.1, 4.7];
const MS_PER_FRAME = 120;
const FRAME_ANGULAR_STEP = 0.35;
const BAR_HEIGHT_BASE_PX = 12;
const BAR_HEIGHT_AMPLITUDE_PX = 6;

function useWaveformBarHeights() {
  const [heights, setHeights] = React.useState(() =>
    BAR_PHASES.map(() => BAR_HEIGHT_BASE_PX),
  );

  React.useEffect(() => {
    let frame = 0;
    const intervalId = setInterval(() => {
      frame += 1;
      setHeights(
        BAR_PHASES.map(
          (phase) =>
            BAR_HEIGHT_BASE_PX +
            Math.round(
              BAR_HEIGHT_AMPLITUDE_PX *
                Math.sin(frame * FRAME_ANGULAR_STEP + phase),
            ),
        ),
      );
    }, MS_PER_FRAME);

    return () => clearInterval(intervalId);
  }, []);

  return heights;
}

export const LogoBars = () => {
  const heights = useWaveformBarHeights();

  return (
    <div className="flex items-center gap-[3px] h-6 shrink-0">
      {heights.map((height, index) => (
        <span
          key={index}
          className="w-[5px] rounded-full bg-[#499f36] dark:bg-[#a6e3a1]"
          style={{ height: `${height}px` }}
        />
      ))}
    </div>
  );
};

export const LogoIcon = () => {
  return (
    <div className="flex items-center gap-2">
      <LogoBars />
      <span className="font-wordmark font-extrabold text-lg text-foreground">
        hittiguess
      </span>
    </div>
  );
};
