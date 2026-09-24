import type { CSSProperties, KeyboardEvent, PointerEvent } from "react";

const CARD_TEXT_COLOR = "#1e1e2e";
const DEFAULT_CARD_COLOR = "#cba6f7";
const VISUALIZER_BAR_COUNT = 5;
const VISUALIZER_RESTING_LEVELS = [0.35, 0.75, 0.5, 0.9, 0.45];
const VISUALIZER_QUIET_PERCENT = 22;
const VISUALIZER_RANGE_PERCENT = 78;
const AVATAR_COLOR_CLASSES = ["bg-green", "bg-blue", "bg-warning", "bg-pink", "bg-accent", "bg-teal", "bg-primary", "bg-destructive"];
const TOKEN_PILE_MAXIMUM_COINS = 4;
const TOKEN_PILE_COIN_OFFSET_PIXELS = 8;
const TOKEN_PILE_COIN_ROTATIONS = [7, -9, 4, -5];

export const CARD_SIZE_CLASSES = "size-[132px] sm:size-[168px]";

function cardColor(color: string | undefined): string {
  if (!color) return DEFAULT_CARD_COLOR;
  return color.startsWith("#") ? color : `#${color}`;
}

export function SongCard({ artist, title, year, color, className = "", style }: { artist?: string; title?: string; year?: number | string; color?: string; className?: string; style?: CSSProperties }) {
  return <article style={{ background: cardColor(color), color: CARD_TEXT_COLOR, ...style }} className={`flex shrink-0 flex-col items-center justify-center gap-1.5 rounded-[22px] border-[5px] border-background p-3.5 text-center ${CARD_SIZE_CLASSES} ${className}`}>
    <span className="line-clamp-2 text-[10px] font-bold uppercase sm:text-xs">{artist || "Unknown artist"}</span>
    <strong className="font-display text-[32px] leading-none sm:text-[40px]">{year ?? "?"}</strong>
    <span className="line-clamp-2 text-[10px] font-medium opacity-80 sm:text-[11px]">{title || "Untitled"}</span>
  </article>;
}

export function SoundBars({ levels, barClassName = "w-[7px]", heightClassName = "h-[46px]" }: { levels?: number[]; barClassName?: string; heightClassName?: string }) {
  const barLevels = levels?.some((level) => level > 0) ? levels : VISUALIZER_RESTING_LEVELS;
  return <div className={`flex items-center gap-1.5 ${heightClassName}`} aria-hidden="true">
    {Array.from({ length: VISUALIZER_BAR_COUNT }, (_, barIndex) => <span key={barIndex} className={`rounded-full bg-green transition-[height] duration-100 ${barClassName}`} style={{ height: `${Math.round(VISUALIZER_QUIET_PERCENT + (barLevels.at(barIndex) ?? 0) * VISUALIZER_RANGE_PERCENT)}%` }} />)}
  </div>;
}

interface MysteryCardProps {
  levels?: number[];
  size?: "small" | "timeline";
  label?: string;
  isInteractive?: boolean;
  className?: string;
  onPointerDown?: (event: PointerEvent<HTMLElement>) => void;
  onKeyDown?: (event: KeyboardEvent<HTMLElement>) => void;
}

export function MysteryCard({ levels, size = "timeline", label, isInteractive = false, className = "", onPointerDown, onKeyDown }: MysteryCardProps) {
  const sizeClasses = size === "small" ? "size-[128px] rounded-[20px]" : `${CARD_SIZE_CLASSES} rounded-[22px]`;
  const content = <SoundBars levels={levels} barClassName={size === "small" ? "w-[7px]" : "w-2"} heightClassName={size === "small" ? "h-[46px]" : "h-[60px]"} />;
  const classes = `flex shrink-0 items-center justify-center border-[5px] border-border bg-card shadow-[5px_5px_0_var(--shadow-color)] ${sizeClasses} ${className}`;
  if (!isInteractive) return <div className={classes} aria-hidden="true">{content}</div>;
  return <button type="button" aria-label={label} onPointerDown={onPointerDown} onKeyDown={onKeyDown} className={`${classes} cursor-grab touch-none select-none active:cursor-grabbing focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring`}>{content}</button>;
}

export function QuestionCard({ className = "" }: { className?: string }) {
  return <div className={`flex shrink-0 items-center justify-center rounded-[22px] border-[5px] border-border bg-card font-display text-5xl text-muted-foreground/50 shadow-[5px_5px_0_var(--shadow-color)] ${CARD_SIZE_CLASSES} ${className}`} aria-label="Locked card">?</div>;
}

export function Coin({ size = "medium", className = "", style }: { size?: "small" | "medium" | "large"; className?: string; style?: CSSProperties }) {
  const sizeClasses = size === "small" ? "size-11 text-[15px] border-4" : size === "large" ? "size-[52px] text-[17px] border-4" : "size-[46px] text-base border-[3px]";
  return <div style={style} className={`flex items-center justify-center rounded-full border-foreground bg-peach font-display text-white shadow-[3px_3px_0_var(--shadow-color)] dark:border-background dark:bg-warning dark:text-[#1e1e2e] ${sizeClasses} ${className}`} aria-hidden="true">$</div>;
}

export function PlayerAvatar({ name, colorIndex, className = "size-8 text-xs" }: { name?: string; colorIndex: number; className?: string }) {
  const colorClass = AVATAR_COLOR_CLASSES[colorIndex % AVATAR_COLOR_CLASSES.length];
  return <div className={`flex shrink-0 items-center justify-center rounded-full font-display text-[#11111b] ${colorClass} ${className}`} aria-hidden="true">{name?.charAt(0).toUpperCase() ?? "?"}</div>;
}

export function TokenPile({ tokenCount, label, isDimmed = false }: { tokenCount: number; label: string; isDimmed?: boolean }) {
  const visibleCoins = Math.min(tokenCount, TOKEN_PILE_MAXIMUM_COINS);
  return <div className={`flex flex-col items-center gap-2.5 transition-opacity ${isDimmed ? "opacity-50" : ""}`} aria-label={`${label}: ${tokenCount}`}>
    <div className="relative h-[60px] w-[70px]">
      {visibleCoins === 0 ? <div className="absolute left-3 top-1.5 size-[46px] rounded-full border-2 border-dashed border-muted-foreground/60" /> : null}
      {Array.from({ length: visibleCoins }, (_, coinIndex) => <Coin key={coinIndex} className="absolute" style={{ left: (visibleCoins - 1 - coinIndex) * TOKEN_PILE_COIN_OFFSET_PIXELS, top: (visibleCoins - 1 - coinIndex) * (TOKEN_PILE_COIN_OFFSET_PIXELS / 2), transform: `rotate(${TOKEN_PILE_COIN_ROTATIONS.at(coinIndex) ?? 0}deg)` }} />)}
    </div>
    <span className="text-[11px] font-bold uppercase tracking-[2px] text-muted-foreground">{label}</span>
  </div>;
}
