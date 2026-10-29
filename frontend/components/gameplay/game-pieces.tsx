import { useEffect, useRef, type CSSProperties, type KeyboardEvent, type PointerEvent } from "react";

import { SILENCE_FLOOR_LEVEL } from "@/hooks/use-audio-levels";
import { readableCardColor } from "@/lib/card-color";

const CARD_TEXT_COLOR = "#1e1e2e";
const DEFAULT_CARD_COLOR = "#cba6f7";
const VISUALIZER_RESTING_PATTERN = [0.35, 0.75, 0.5, 0.9, 0.45];
const VISUALIZER_QUIET_PERCENT = 22;
const VISUALIZER_RANGE_PERCENT = 78;
const VISUALIZER_MAXIMUM_LEVEL = 1;
const VISUALIZER_BAR_WIDTH_PIXELS = 7;
const VISUALIZER_BAR_GAP_PIXELS = 6;
const VISUALIZER_BAR_RADIUS_RATIO = 0.5;
const VISUALIZER_COLOR_VARIABLE = "--green";
const VISUALIZER_FALLBACK_COLOR = "#499f36";
const CSS_PERCENT_SCALE = 100;
const DEVICE_PIXEL_RATIO_FALLBACK = 1;
const AVATAR_COLOR_CLASSES = ["bg-green", "bg-blue", "bg-warning", "bg-pink", "bg-accent", "bg-teal", "bg-primary", "bg-destructive"];
const TOKEN_PILE_MAXIMUM_COINS = 4;
const TOKEN_PILE_COIN_OFFSET_PIXELS = 8;
const TOKEN_PILE_COIN_ROTATIONS = [7, -9, 4, -5];

export const CARD_SIZE_CLASSES = "size-[132px] sm:size-[168px]";

function cardColor(color: string | undefined): string {
  if (!color) return DEFAULT_CARD_COLOR;
  return readableCardColor(color.startsWith("#") ? color : `#${color}`);
}

const VISUALIZER_RESTING_LEVELS = VISUALIZER_RESTING_PATTERN;

function readVisualizerColor(canvas: HTMLCanvasElement): string {
  const color = getComputedStyle(canvas).getPropertyValue(VISUALIZER_COLOR_VARIABLE).trim();
  return color || VISUALIZER_FALLBACK_COLOR;
}

// Redraws the bars inside the canvas backing store, sized to the canvas CSS box
// times the device pixel ratio, so no part of the drawing can land outside the
// canvas at any zoom level.
function drawSoundBars(canvas: HTMLCanvasElement | null, levels: number[]): void {
  if (!canvas) return;
  const context = canvas.getContext("2d");
  if (!context) return;
  const bounds = canvas.getBoundingClientRect();
  if (bounds.width <= 0 || bounds.height <= 0) return;
  const devicePixelRatio = window.devicePixelRatio || DEVICE_PIXEL_RATIO_FALLBACK;
  const backingWidth = Math.round(bounds.width * devicePixelRatio);
  const backingHeight = Math.round(bounds.height * devicePixelRatio);
  if (canvas.width !== backingWidth || canvas.height !== backingHeight) {
    canvas.width = backingWidth;
    canvas.height = backingHeight;
  }
  context.setTransform(devicePixelRatio, 0, 0, devicePixelRatio, 0, 0);
  context.clearRect(0, 0, bounds.width, bounds.height);
  context.fillStyle = readVisualizerColor(canvas);
  const barPitch = VISUALIZER_BAR_WIDTH_PIXELS + VISUALIZER_BAR_GAP_PIXELS;
  const barsWidth = levels.length * barPitch - VISUALIZER_BAR_GAP_PIXELS;
  const firstBarLeft = (bounds.width - barsWidth) / 2;
  const cornerRadius = Math.min(VISUALIZER_BAR_WIDTH_PIXELS, bounds.height) * VISUALIZER_BAR_RADIUS_RATIO;
  levels.forEach((level, bandIndex) => {
    const boundedLevel = Math.min(VISUALIZER_MAXIMUM_LEVEL, Math.max(SILENCE_FLOOR_LEVEL, level));
    const barHeight = bounds.height * ((VISUALIZER_QUIET_PERCENT + boundedLevel * VISUALIZER_RANGE_PERCENT) / CSS_PERCENT_SCALE);
    const barLeft = firstBarLeft + bandIndex * barPitch;
    const barTop = (bounds.height - barHeight) / 2;
    context.beginPath();
    context.roundRect(barLeft, barTop, VISUALIZER_BAR_WIDTH_PIXELS, barHeight, cornerRadius);
    context.fill();
  });
}

export function SongCard({ artist, title, year, color, className = "", style }: { artist?: string; title?: string; year?: number | string; color?: string; className?: string; style?: CSSProperties }) {
  return <article style={{ background: cardColor(color), color: CARD_TEXT_COLOR, ...style }} className={`flex shrink-0 flex-col items-center justify-center gap-1.5 rounded-[22px] border-[5px] border-background p-3.5 text-center ${CARD_SIZE_CLASSES} ${className}`}>
    <span className="line-clamp-2 text-[10px] font-bold uppercase sm:text-xs">{artist || "Unknown artist"}</span>
    <strong className="font-display text-[32px] leading-none sm:text-[40px]">{year ?? "?"}</strong>
    <span className="line-clamp-2 text-[10px] font-medium opacity-80 sm:text-[11px]">{title || "Untitled"}</span>
  </article>;
}

export function SoundBars({ levels, widthClassName = "w-16", heightClassName = "h-[60px]" }: { levels?: number[]; widthClassName?: string; heightClassName?: string }) {
  const canvasReference = useRef<HTMLCanvasElement>(null);
  const levelsReference = useRef<number[]>(VISUALIZER_RESTING_LEVELS);
  const barLevels = levels?.some((level) => level > SILENCE_FLOOR_LEVEL) ? levels : VISUALIZER_RESTING_LEVELS;

  useEffect(() => {
    levelsReference.current = barLevels;
    drawSoundBars(canvasReference.current, barLevels);
  }, [barLevels]);

  useEffect(() => {
    const canvas = canvasReference.current;
    if (!canvas) return;
    const redraw = () => drawSoundBars(canvasReference.current, levelsReference.current);
    redraw();
    const observer = new ResizeObserver(redraw);
    observer.observe(canvas);
    window.addEventListener("resize", redraw);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", redraw);
    };
  }, []);

  return <canvas ref={canvasReference} aria-hidden="true" className={`block ${widthClassName} ${heightClassName}`} />;
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
  const content = <SoundBars levels={levels} widthClassName={size === "small" ? "w-[59px]" : "w-16"} heightClassName={size === "small" ? "h-[46px]" : "h-[60px]"} />;
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

export function TokenPile({ tokenCount, label, isDimmed = false, earnedTokenRoundId }: { tokenCount: number; label: string; isDimmed?: boolean; earnedTokenRoundId?: number }) {
  const visibleCoins = Math.min(tokenCount, TOKEN_PILE_MAXIMUM_COINS);
  return <div className={`flex flex-col items-center gap-2.5 transition-opacity ${isDimmed ? "opacity-50" : ""}`} aria-label={`${label}: ${tokenCount}`}>
    <div className="relative h-[60px] w-[70px]">
      {earnedTokenRoundId !== undefined ? <Coin key={earnedTokenRoundId} className="token-earned-drop absolute left-3 top-1.5 z-10" /> : null}
      {visibleCoins === 0 ? <div className="absolute left-3 top-1.5 size-[46px] rounded-full border-2 border-dashed border-muted-foreground/60" /> : null}
      {Array.from({ length: visibleCoins }, (_, coinIndex) => <Coin key={coinIndex} className="absolute" style={{ left: (visibleCoins - 1 - coinIndex) * TOKEN_PILE_COIN_OFFSET_PIXELS, top: (visibleCoins - 1 - coinIndex) * (TOKEN_PILE_COIN_OFFSET_PIXELS / 2), transform: `rotate(${TOKEN_PILE_COIN_ROTATIONS.at(coinIndex) ?? 0}deg)` }} />)}
    </div>
    <span className="text-[11px] font-bold uppercase tracking-[2px] text-muted-foreground">{label}</span>
  </div>;
}
