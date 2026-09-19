const DEFAULT_COLOR = "#8B5CF6";

// Mirrors CardGenerator.java's readableTextColorFor: a single flat fill color
// can be light enough that fixed white text stops being legible, unlike the
// old two-stop gradient.
const LUMINANCE_THRESHOLD_FOR_DARK_TEXT = 150;
const DARK_TEXT_COLOR = "#1e1e2e";
const LIGHT_TEXT_COLOR = "#ffffff";

function readableTextColorFor(hexColor: string): string {
  const hex = hexColor.replace("#", "");
  const red = parseInt(hex.slice(0, 2), 16);
  const green = parseInt(hex.slice(2, 4), 16);
  const blue = parseInt(hex.slice(4, 6), 16);
  const luminance = 0.299 * red + 0.587 * green + 0.114 * blue;
  return luminance > LUMINANCE_THRESHOLD_FOR_DARK_TEXT ? DARK_TEXT_COLOR : LIGHT_TEXT_COLOR;
}

interface GameCardProps {
  artist: string;
  year: number | string;
  title: string;
  color?: string;
  size?: "lg" | "sm";
  /** No metadata resolved yet: renders a dashed, muted placeholder card. */
  placeholder?: boolean;
}

export function GameCard({ artist, year, title, color, size = "lg", placeholder = false }: GameCardProps) {
  const fillColor = color ? (color.startsWith("#") ? color : `#${color}`) : DEFAULT_COLOR;
  const textColor = readableTextColorFor(fillColor);

  const isLarge = size === "lg";

  return (
    <div
      className={`flex aspect-square w-full flex-col items-center justify-center gap-2.5 rounded-[24px] text-center shadow-lg sm:rounded-[40px] ${
        isLarge ? "max-w-[320px] p-6 sm:p-8" : "max-w-[160px] p-3"
      } ${
        placeholder
          ? "border-4 border-dashed border-border text-muted-foreground sm:border-[5px]"
          : "border-[5px] border-border-strong sm:border-8"
      }`}
      style={placeholder ? undefined : { background: fillColor, color: textColor }}
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
