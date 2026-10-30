"use client";

import { useCallback, useEffect, useLayoutEffect, useRef } from "react";

import { fitFontSize } from "@/lib/gameplay-layout";

const MAXIMUM_LINES = 3;
const LINE_HEIGHT = 1.12;
const MINIMUM_FONT_PIXELS = 6;

const useBrowserLayoutEffect = typeof window === "undefined" ? useEffect : useLayoutEffect;

// Fills the area its parent gives it. The text wraps onto as many lines as the area
// holds (up to three at the largest size) and only then shrinks, so it never truncates.
export function FitText({ text, className = "" }: { text: string; className?: string }) {
  const areaReference = useRef<HTMLDivElement>(null);
  const textReference = useRef<HTMLSpanElement>(null);

  const fit = useCallback(() => {
    const area = areaReference.current;
    const textElement = textReference.current;
    if (!area || !textElement) return;
    const areaHeight = area.clientHeight;
    const areaWidth = area.clientWidth;
    if (areaHeight <= 0 || areaWidth <= 0) return;
    const maximumSize = areaHeight / (MAXIMUM_LINES * LINE_HEIGHT);
    const size = fitFontSize(maximumSize, MINIMUM_FONT_PIXELS, (candidateSize) => {
      textElement.style.fontSize = `${candidateSize}px`;
      return textElement.scrollHeight <= areaHeight && textElement.scrollWidth <= areaWidth;
    });
    textElement.style.fontSize = `${size}px`;
  }, []);

  useBrowserLayoutEffect(() => {
    fit();
  }, [fit, text]);

  useEffect(() => {
    const area = areaReference.current;
    if (!area) return;
    const resizeObserver = new ResizeObserver(fit);
    resizeObserver.observe(area);
    // Web fonts change the text metrics once they load, after the first fit.
    let isCancelled = false;
    void document.fonts?.ready.then(() => {
      if (!isCancelled) fit();
    });
    return () => {
      isCancelled = true;
      resizeObserver.disconnect();
    };
  }, [fit]);

  return <div ref={areaReference} className={`flex min-h-0 w-full self-stretch overflow-hidden ${className}`}>
    <span ref={textReference} className="block w-full [overflow-wrap:anywhere]" style={{ lineHeight: LINE_HEIGHT }}>{text}</span>
  </div>;
}
