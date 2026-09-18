const WAVEFORM_ANIMATION_DELAYS = ["0ms", "-180ms", "-360ms", "-540ms"];

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
  return (
    <div className={`flex items-center gap-[3px] shrink-0 ${containerClassName}`}>
      {WAVEFORM_ANIMATION_DELAYS.map((animationDelay) => (
        <span
          key={animationDelay}
          className={`h-6 origin-center rounded-full animate-waveform-bar ${colorClassName}`}
          style={{ width: `${barWidthPx}px`, animationDelay }}
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
