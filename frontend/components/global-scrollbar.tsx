"use client";

import { useEffect, useState } from "react";

const MINIMUM_THUMB_HEIGHT_PX = 28;
const SCROLLBAR_RIGHT_OFFSET_PX = 4;

type ScrollbarThumb = {
  height: number;
  top: number;
  visible: boolean;
};

const INITIAL_THUMB: ScrollbarThumb = {
  height: 0,
  top: 0,
  visible: false,
};

export function GlobalScrollbar() {
  const [thumb, setThumb] = useState<ScrollbarThumb>(INITIAL_THUMB);

  useEffect(() => {
    let animationFrameId = 0;

    function updateThumb() {
      const documentElement = document.documentElement;
      const viewportHeight = window.innerHeight;
      const documentHeight = documentElement.scrollHeight;
      const maximumScrollTop = documentHeight - viewportHeight;

      if (maximumScrollTop <= 0) {
        setThumb(INITIAL_THUMB);
        return;
      }

      const calculatedHeight = Math.max(
        MINIMUM_THUMB_HEIGHT_PX,
        (viewportHeight / documentHeight) * viewportHeight,
      );
      const maximumThumbTop = viewportHeight - calculatedHeight;
      const calculatedTop =
        (documentElement.scrollTop / maximumScrollTop) * maximumThumbTop;

      setThumb({
        height: calculatedHeight,
        top: calculatedTop,
        visible: true,
      });
    }

    function scheduleUpdate() {
      cancelAnimationFrame(animationFrameId);
      animationFrameId = requestAnimationFrame(updateThumb);
    }

    const resizeObserver = new ResizeObserver(scheduleUpdate);
    resizeObserver.observe(document.documentElement);
    window.addEventListener("resize", scheduleUpdate);
    window.addEventListener("scroll", scheduleUpdate, { passive: true });
    scheduleUpdate();

    return () => {
      cancelAnimationFrame(animationFrameId);
      resizeObserver.disconnect();
      window.removeEventListener("resize", scheduleUpdate);
      window.removeEventListener("scroll", scheduleUpdate);
    };
  }, []);

  if (!thumb.visible) {
    return null;
  }

  return (
    <div
      aria-hidden="true"
      className="global-scrollbar-thumb"
      style={{
        height: `${thumb.height}px`,
        right: `${SCROLLBAR_RIGHT_OFFSET_PX}px`,
        transform: `translateY(${thumb.top}px)`,
      }}
    />
  );
}
