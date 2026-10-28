import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  computeGameStageScale,
  GAME_STAGE_HEIGHT,
  GAME_STAGE_MAXIMUM_SCALE,
  GAME_STAGE_WIDTH,
  GameStage,
  useGameStage,
} from "./game-stage";

const NO_AVAILABLE_SPACE = 0;
const HALF_SCALE = 0.5;
const HALF_WIDTH_PIXELS = GAME_STAGE_WIDTH * HALF_SCALE;
const FULL_HEIGHT_PIXELS = GAME_STAGE_HEIGHT;
const LARGE_VIEWPORT_PIXELS = 4000;
const STAGE_LEFT_PIXELS = 100;
const STAGE_TOP_PIXELS = 50;
const POINTER_OFFSET_X_PIXELS = 100;
const POINTER_OFFSET_Y_PIXELS = 50;
const EXPECTED_STAGE_X = POINTER_OFFSET_X_PIXELS / HALF_SCALE;
const EXPECTED_STAGE_Y = POINTER_OFFSET_Y_PIXELS / HALF_SCALE;

function rectangle(width: number, height: number, left = 0, top = 0): DOMRect {
  return {
    width,
    height,
    left,
    top,
    right: left + width,
    bottom: top + height,
    x: left,
    y: top,
    toJSON: () => ({}),
  } as DOMRect;
}

function mockElementBounds(hostSize: { width: number; height: number }, stageBounds: { width: number; height: number; left: number; top: number }) {
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
    if ("gameStageHost" in this.dataset) return rectangle(hostSize.width, hostSize.height);
    if ("gameStage" in this.dataset) return rectangle(stageBounds.width, stageBounds.height, stageBounds.left, stageBounds.top);
    return rectangle(0, 0);
  });
}

function StageProbe() {
  const { scale, toStagePoint } = useGameStage();
  const point = toStagePoint(STAGE_LEFT_PIXELS + POINTER_OFFSET_X_PIXELS, STAGE_TOP_PIXELS + POINTER_OFFSET_Y_PIXELS);
  return <output data-testid="stage-probe" data-scale={scale} data-x={point.x} data-y={point.y} />;
}

describe("computeGameStageScale", () => {
  it("leaves the design size at scale 1", () => {
    expect(computeGameStageScale(GAME_STAGE_WIDTH, GAME_STAGE_HEIGHT)).toBe(1);
  });

  it("fits the narrower axis", () => {
    expect(computeGameStageScale(HALF_WIDTH_PIXELS, FULL_HEIGHT_PIXELS)).toBe(HALF_SCALE);
  });

  it("caps the scale for large windows", () => {
    expect(computeGameStageScale(LARGE_VIEWPORT_PIXELS, LARGE_VIEWPORT_PIXELS)).toBe(GAME_STAGE_MAXIMUM_SCALE);
  });

  it("stays at zero without available space", () => {
    expect(computeGameStageScale(NO_AVAILABLE_SPACE, FULL_HEIGHT_PIXELS)).toBe(NO_AVAILABLE_SPACE);
  });
});

describe("GameStage", () => {
  afterEach(() => vi.restoreAllMocks());

  it("scales to the narrower axis and converts client points into stage points", () => {
    const stageWidth = GAME_STAGE_WIDTH * HALF_SCALE;
    const stageHeight = GAME_STAGE_HEIGHT * HALF_SCALE;
    mockElementBounds(
      { width: HALF_WIDTH_PIXELS, height: FULL_HEIGHT_PIXELS },
      { width: stageWidth, height: stageHeight, left: STAGE_LEFT_PIXELS, top: STAGE_TOP_PIXELS },
    );

    const { container } = render(<GameStage><StageProbe /></GameStage>);

    const host = container.querySelector("[data-game-stage-host]") as HTMLElement;
    const scaledBox = host.firstElementChild as HTMLElement;
    const stage = container.querySelector("[data-game-stage]") as HTMLElement;

    expect(host).toHaveClass("items-center", "justify-center");
    expect(scaledBox.style.width).toBe(`${HALF_WIDTH_PIXELS}px`);
    expect(scaledBox.style.height).toBe(`${stageHeight}px`);
    expect(stage.style.transform).toBe(`scale(${HALF_SCALE})`);
    expect(stage.style.transformOrigin).toBe("top left");

    const probe = screen.getByTestId("stage-probe");
    expect(probe).toHaveAttribute("data-scale", String(HALF_SCALE));
    expect(probe).toHaveAttribute("data-x", String(EXPECTED_STAGE_X));
    expect(probe).toHaveAttribute("data-y", String(EXPECTED_STAGE_Y));
  });
});
