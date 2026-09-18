"use client";

import * as React from "react";

const BAR_COUNT = 4;
const BAR_MINIMUM_PHASE_STEP = 1.2;
const BAR_RANDOM_PHASE_RANGE = 1.4;
const BAR_BASE_SPEED = 0.0016;
const BAR_RANDOM_SPEED_RANGE = 0.0007;
const BAR_HEIGHT_BASE_PX = 12;
const BAR_HEIGHT_AMPLITUDE_PX = 6;
const BAR_SECONDARY_WAVE_RATIO = 0.37;
const BAR_SECONDARY_WAVE_AMPLITUDE_RATIO = 0.35;

function useWaveformBarHeights() {
  const [heights, setHeights] = React.useState(() =>
    Array.from({ length: BAR_COUNT }, () => BAR_HEIGHT_BASE_PX),
  );
  const waveformConfig = React.useRef(
    Array.from({ length: BAR_COUNT }, (_, barIndex) => ({
      phase: barIndex * BAR_MINIMUM_PHASE_STEP + Math.random() * BAR_RANDOM_PHASE_RANGE,
      speed: BAR_BASE_SPEED + Math.random() * BAR_RANDOM_SPEED_RANGE,
    })),
  );

  React.useEffect(() => {
    let animationFrameId = 0;

    function animate(timestamp: number) {
      setHeights(
        waveformConfig.current.map(
          ({ phase, speed }) =>
            BAR_HEIGHT_BASE_PX +
            Math.round(
              BAR_HEIGHT_AMPLITUDE_PX *
                (Math.sin(timestamp * speed + phase) +
                  BAR_SECONDARY_WAVE_AMPLITUDE_RATIO *
                    Math.sin(
                      timestamp * speed * BAR_SECONDARY_WAVE_RATIO + phase * BAR_SECONDARY_WAVE_RATIO,
                    )),
            ),
        ),
      );
      animationFrameId = requestAnimationFrame(animate);
    }

    animationFrameId = requestAnimationFrame(animate);

    return () => cancelAnimationFrame(animationFrameId);
  }, []);

  return heights;
}

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
  const heights = useWaveformBarHeights();

  return (
    <div className={`flex items-center gap-[3px] shrink-0 ${containerClassName}`}>
      {heights.map((height, index) => (
        <span
          key={index}
          className={`rounded-full transition-[height] duration-150 ease-out ${colorClassName}`}
          style={{ width: `${barWidthPx}px`, height: `${height}px` }}
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
