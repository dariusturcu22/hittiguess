"use client";

import { useCallback, useEffect, useState, type ReactNode, type RefObject } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

import type { PlayerCardDTO } from "@/hooks/models/playerCardDTO";
import { cardTiltAwayFromGap, timelineEntries } from "@/lib/gameplay-round";
import { SongCard } from "./game-pieces";

const TIMELINE_EDGE_FADE = "linear-gradient(90deg, transparent, #000 64px, #000 calc(100% - 64px), transparent)";
const SCROLL_STEP_CARDS = 3;
const CARD_STEP_PIXELS = 186;
const SCROLL_EDGE_TOLERANCE_PIXELS = 2;

export const TIMELINE_CARD_SELECTOR = "[data-timeline-card]";
export const TIMELINE_FOCUS_SELECTOR = "[data-timeline-focus]";

interface GameplayTimelineProps {
  cards: PlayerCardDTO[];
  slots: Map<number, ReactNode>;
  tiltGapIndex?: number | null;
  focusKey?: string | null;
  isDimmed?: boolean;
  trackReference: RefObject<HTMLDivElement | null>;
}

// The active player's timeline, centered on the stage: a short timeline sits in the
// middle, a long one scrolls horizontally behind faded edges. Gap slots render whatever
// the round puts there (the hover gap, the dropped card, the locked card, bets).
export function GameplayTimeline({ cards, slots, tiltGapIndex = null, focusKey = null, isDimmed = false, trackReference }: GameplayTimelineProps) {
  const [scrollState, setScrollState] = useState({ canScrollBack: false, canScrollForward: false });

  const updateScrollState = useCallback(() => {
    const track = trackReference.current;
    if (!track) return;
    setScrollState({
      canScrollBack: track.scrollLeft > SCROLL_EDGE_TOLERANCE_PIXELS,
      canScrollForward: track.scrollLeft + track.clientWidth < track.scrollWidth - SCROLL_EDGE_TOLERANCE_PIXELS,
    });
  }, [trackReference]);

  useEffect(() => {
    const track = trackReference.current;
    if (!track) return;
    updateScrollState();
    const resizeObserver = new ResizeObserver(updateScrollState);
    resizeObserver.observe(track);
    if (track.firstElementChild) resizeObserver.observe(track.firstElementChild);
    return () => resizeObserver.disconnect();
  }, [trackReference, updateScrollState]);

  // Recenter on whatever the round is about right now: the dropped, locked, or revealed card.
  useEffect(() => {
    if (!focusKey) return;
    trackReference.current?.querySelector(TIMELINE_FOCUS_SELECTOR)?.scrollIntoView?.({ behavior: "smooth", block: "nearest", inline: "center" });
  }, [focusKey, trackReference]);

  function scrollByCards(direction: number) {
    trackReference.current?.scrollBy({ left: direction * SCROLL_STEP_CARDS * CARD_STEP_PIXELS, behavior: "smooth" });
  }

  return <div className="relative w-full max-w-[1100px]">
    <div ref={trackReference} onScroll={updateScrollState} className="w-full overflow-x-auto overflow-y-hidden [scrollbar-width:none] [&::-webkit-scrollbar]:hidden" style={{ maskImage: TIMELINE_EDGE_FADE, WebkitMaskImage: TIMELINE_EDGE_FADE }}>
      <div className={`mx-auto flex w-max items-center gap-[18px] px-16 py-6 transition-[filter,opacity] duration-300 ${isDimmed ? "opacity-60 saturate-[0.55]" : ""}`}>
        {timelineEntries(cards).map((entry) => {
          if (entry.kind === "gap") {
            const slot = slots.get(entry.gapIndex);
            return slot ? <div key={`gap-${entry.gapIndex}`} className="flex shrink-0 items-center justify-center">{slot}</div> : null;
          }
          const tilt = tiltGapIndex === null ? null : cardTiltAwayFromGap(entry.cardIndex, tiltGapIndex);
          return <div key={`card-${entry.card.songId}-${entry.cardIndex}`} data-timeline-card="" className="shrink-0 transition-transform duration-300 ease-out" style={tilt ? { transform: `translateX(${tilt.shiftPixels}px) rotate(${tilt.rotateDegrees}deg)` } : undefined}>
            <SongCard artist={entry.card.artist} title={entry.card.title} year={entry.card.releaseYear} color={entry.card.color} />
          </div>;
        })}
      </div>
    </div>
    <button type="button" aria-label="View earlier timeline cards" disabled={!scrollState.canScrollBack} onClick={() => scrollByCards(-1)} className="absolute -left-1.5 top-1/2 flex size-[42px] -translate-y-1/2 items-center justify-center rounded-full border-2 border-border bg-card text-card-foreground shadow-[3px_3px_0_var(--shadow-color)] transition-opacity disabled:opacity-40"><ChevronLeft className="size-[17px]" strokeWidth={2.5} /></button>
    <button type="button" aria-label="View later timeline cards" disabled={!scrollState.canScrollForward} onClick={() => scrollByCards(1)} className="absolute -right-1.5 top-1/2 flex size-[42px] -translate-y-1/2 items-center justify-center rounded-full border-2 border-border bg-card text-card-foreground shadow-[3px_3px_0_var(--shadow-color)] transition-opacity disabled:opacity-40"><ChevronRight className="size-[17px]" strokeWidth={2.5} /></button>
  </div>;
}
