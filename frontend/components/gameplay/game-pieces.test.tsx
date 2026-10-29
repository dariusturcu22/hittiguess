import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SoundBars } from "./game-pieces";

const CANVAS_WIDTH_PIXELS = 64;
const CANVAS_HEIGHT_PIXELS = 60;
const EXPECTED_BAR_COUNT = 5;
const EXPECTED_BAR_WIDTH_PIXELS = 7;
const EXPECTED_BAR_PITCH_PIXELS = 13;

interface DrawnBar {
  left: number;
  top: number;
  width: number;
}

function stubCanvasRendering(): DrawnBar[] {
  const drawnBars: DrawnBar[] = [];
  const context = {
    setTransform: vi.fn(),
    clearRect: vi.fn(),
    beginPath: vi.fn(),
    roundRect: vi.fn((left: number, top: number, width: number) => {
      drawnBars.push({ left, top, width });
    }),
    fill: vi.fn(),
    fillStyle: "",
  };
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(context as unknown as CanvasRenderingContext2D);
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({
    width: CANVAS_WIDTH_PIXELS,
    height: CANVAS_HEIGHT_PIXELS,
    top: 0,
    left: 0,
    right: CANVAS_WIDTH_PIXELS,
    bottom: CANVAS_HEIGHT_PIXELS,
    x: 0,
    y: 0,
    toJSON: () => ({}),
  } as DOMRect);
  return drawnBars;
}

afterEach(() => vi.restoreAllMocks());

describe("SoundBars", () => {
  it("draws five resting bars with the design's width and gap", () => {
    const drawnBars = stubCanvasRendering();
    render(<SoundBars />);
    const restingBars = drawnBars.slice(0, EXPECTED_BAR_COUNT);

    expect(restingBars).toHaveLength(EXPECTED_BAR_COUNT);
    restingBars.forEach((bar) => expect(bar.width).toBe(EXPECTED_BAR_WIDTH_PIXELS));
    for (let barIndex = 1; barIndex < restingBars.length; barIndex += 1) {
      const barPitch = restingBars[barIndex].left - restingBars[barIndex - 1].left;
      expect(barPitch).toBe(EXPECTED_BAR_PITCH_PIXELS);
    }
  });
});
