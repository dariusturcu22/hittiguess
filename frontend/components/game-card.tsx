const DEFAULT_GRADIENT_START = "#8B5CF6";
const DEFAULT_GRADIENT_END = "#EC4899";

interface GameCardProps {
  artist: string;
  year: number | string;
  title: string;
  gradientColor1?: string;
  gradientColor2?: string;
  size?: "lg" | "sm";
  /** No metadata resolved yet: renders a dashed, muted placeholder card. */
  placeholder?: boolean;
}

export function GameCard({
  artist,
  year,
  title,
  gradientColor1,
  gradientColor2,
  size = "lg",
  placeholder = false,
}: GameCardProps) {
  const start = gradientColor1
    ? gradientColor1.startsWith("#")
      ? gradientColor1
      : `#${gradientColor1}`
    : DEFAULT_GRADIENT_START;
  const end = gradientColor2
    ? gradientColor2.startsWith("#")
      ? gradientColor2
      : `#${gradientColor2}`
    : DEFAULT_GRADIENT_END;

  const isLarge = size === "lg";

  return (
    <div
      className={`flex aspect-square w-full flex-col items-center justify-center gap-2.5 rounded-[24px] text-center shadow-lg sm:rounded-[40px] ${
        isLarge ? "max-w-[320px] p-6 sm:p-8" : "max-w-[160px] p-3"
      } ${
        placeholder
          ? "border-4 border-dashed border-border text-muted-foreground sm:border-[5px]"
          : "border-[5px] border-border-strong text-white sm:border-8"
      }`}
      style={
        placeholder
          ? undefined
          : { background: `linear-gradient(to bottom, ${start}, ${end})` }
      }
    >
      <div
        className={`font-semibold tracking-wide uppercase ${isLarge ? "text-lg sm:text-xl" : "text-[11px]"}`}
      >
        {artist || "Artist unknown"}
      </div>
      <div
        className={`font-display leading-none ${
          placeholder ? "opacity-60" : ""
        } ${isLarge ? "text-5xl sm:text-[76px]" : "text-4xl"}`}
      >
        {year || "?"}
      </div>
      <div
        className={`${placeholder ? "" : "opacity-80"} ${isLarge ? "text-base sm:text-lg" : "text-[10px]"}`}
      >
        {title || "Untitled"}
      </div>
    </div>
  );
}
