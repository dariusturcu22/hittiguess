const BAR_ANIMATION_DELAYS_MS = [0, 150, 300, 450];

export const LogoBars = () => {
  return (
    <div className="flex items-center gap-[3px] h-6 shrink-0">
      {BAR_ANIMATION_DELAYS_MS.map((delayMs) => (
        <span
          key={delayMs}
          className="w-[5px] h-[22px] rounded-full bg-[#499f36] dark:bg-[#a6e3a1] waveform-bar-pulse"
          style={{ animationDelay: `${delayMs}ms` }}
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
