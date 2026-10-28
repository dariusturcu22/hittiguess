"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";

export const GAME_STAGE_WIDTH = 1288;
export const GAME_STAGE_HEIGHT = 900;
export const GAME_STAGE_MAXIMUM_SCALE = 1.25;

const NO_AVAILABLE_SPACE = 0;

export function computeGameStageScale(availableWidth: number, availableHeight: number): number {
  if (availableWidth <= NO_AVAILABLE_SPACE || availableHeight <= NO_AVAILABLE_SPACE) return NO_AVAILABLE_SPACE;
  return Math.min(availableWidth / GAME_STAGE_WIDTH, availableHeight / GAME_STAGE_HEIGHT, GAME_STAGE_MAXIMUM_SCALE);
}

interface GameStagePoint {
  x: number;
  y: number;
}

interface GameStageContextValue {
  scale: number;
  toStagePoint: (clientX: number, clientY: number) => GameStagePoint;
}

const GameStageContext = createContext<GameStageContextValue | null>(null);

export function useGameStage(): GameStageContextValue {
  const contextValue = useContext(GameStageContext);
  if (!contextValue) throw new Error("useGameStage must be used inside GameStage");
  return contextValue;
}

// Lays the game screen out at its design size and scales the whole box evenly into
// the space available, so every element keeps its designed proportions at any window size.
export function GameStage({ children }: { children: ReactNode }) {
  const hostReference = useRef<HTMLDivElement>(null);
  const stageReference = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(NO_AVAILABLE_SPACE);

  useEffect(() => {
    const host = hostReference.current;
    if (!host) return;
    function measureHost() {
      const hostRectangle = hostReference.current?.getBoundingClientRect();
      if (!hostRectangle) return;
      setScale(computeGameStageScale(hostRectangle.width, hostRectangle.height));
    }
    measureHost();
    const resizeObserver = new ResizeObserver(measureHost);
    resizeObserver.observe(host);
    return () => resizeObserver.disconnect();
  }, []);

  const toStagePoint = useCallback((clientX: number, clientY: number): GameStagePoint => {
    const stageRectangle = stageReference.current?.getBoundingClientRect();
    if (!stageRectangle || scale <= NO_AVAILABLE_SPACE) return { x: clientX, y: clientY };
    return {
      x: (clientX - stageRectangle.left) / scale,
      y: (clientY - stageRectangle.top) / scale,
    };
  }, [scale]);

  const contextValue = useMemo(() => ({ scale, toStagePoint }), [scale, toStagePoint]);

  return <div ref={hostReference} data-game-stage-host="" className="relative flex h-full min-h-0 w-full items-center justify-center overflow-hidden">
    <div style={{ width: GAME_STAGE_WIDTH * scale, height: GAME_STAGE_HEIGHT * scale }} className="relative shrink-0">
      <div ref={stageReference} data-game-stage="" style={{ width: GAME_STAGE_WIDTH, height: GAME_STAGE_HEIGHT, transform: `scale(${scale})`, transformOrigin: "top left" }} className="absolute left-0 top-0 overflow-hidden">
        <GameStageContext.Provider value={contextValue}>{children}</GameStageContext.Provider>
      </div>
    </div>
  </div>;
}
