import { act, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { drawSoundBars, SoundBars } from "./game-pieces";

const CANVAS_WIDTH_PIXELS = 64;
const CANVAS_HEIGHT_PIXELS = 60;
const EXPECTED_BAR_COUNT = 5;
const EXPECTED_BAR_WIDTH_PIXELS = 7;
const EXPECTED_BAR_PITCH_PIXELS = 13;
const ZOOM_DEVICE_PIXEL_RATIOS = [1.0, 1.25, 1.5, 2.0];
const ACTIVE_AUDIO_LEVELS = [0.9, 0.7, 0.85, 0.6, 0.95];
const SILENT_AUDIO_LEVELS = [0, 0, 0, 0, 0];

interface DrawnBar {
  left: number;
  top: number;
  width: number;
  height: number;
}

interface CanvasContextStub {
  setTransform: ReturnType<typeof vi.fn>;
  clearRect: ReturnType<typeof vi.fn>;
  beginPath: ReturnType<typeof vi.fn>;
  roundRect: ReturnType<typeof vi.fn>;
  fill: ReturnType<typeof vi.fn>;
  fillStyle: string;
}

function stubCanvasRendering(): { drawnBars: DrawnBar[]; context: CanvasContextStub } {
  const drawnBars: DrawnBar[] = [];
  const context: CanvasContextStub = {
    setTransform: vi.fn(),
    clearRect: vi.fn(),
    beginPath: vi.fn(),
    roundRect: vi.fn((left: number, top: number, width: number, height: number) => {
      drawnBars.push({ left, top, width, height });
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
  return { drawnBars, context };
}

describe("SoundBars and drawSoundBars", () => {
  let mediaQueryListeners: Array<(event: { matches: boolean }) => void> = [];
  let reducedMotionMatches = false;

  beforeEach(() => {
    mediaQueryListeners = [];
    reducedMotionMatches = false;

    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query.includes("prefers-reduced-motion") ? reducedMotionMatches : false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn((_type: string, listener: (event: { matches: boolean }) => void) => {
        mediaQueryListeners.push(listener);
      }),
      removeEventListener: vi.fn((_type: string, listener: (event: { matches: boolean }) => void) => {
        mediaQueryListeners = mediaQueryListeners.filter((registered) => registered !== listener);
      }),
      dispatchEvent: vi.fn(),
    }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("draws five resting bars with the design width and gap", () => {
    const { drawnBars } = stubCanvasRendering();
    render(<SoundBars />);
    const restingBars = drawnBars.slice(0, EXPECTED_BAR_COUNT);

    expect(restingBars).toHaveLength(EXPECTED_BAR_COUNT);
    restingBars.forEach((bar) => expect(bar.width).toBe(EXPECTED_BAR_WIDTH_PIXELS));
    for (let barIndex = 1; barIndex < restingBars.length; barIndex += 1) {
      const barPitch = restingBars[barIndex].left - restingBars[barIndex - 1].left;
      expect(barPitch).toBe(EXPECTED_BAR_PITCH_PIXELS);
    }
  });

  it("handles devicePixelRatio scaling correctly across multiple zoom levels", () => {
    for (const ratio of ZOOM_DEVICE_PIXEL_RATIOS) {
      window.devicePixelRatio = ratio;
      const { context } = stubCanvasRendering();
      const canvas = document.createElement("canvas");

      drawSoundBars(canvas, SILENT_AUDIO_LEVELS);

      const expectedBackingWidth = Math.round(CANVAS_WIDTH_PIXELS * ratio);
      const expectedBackingHeight = Math.round(CANVAS_HEIGHT_PIXELS * ratio);

      expect(canvas.width).toBe(expectedBackingWidth);
      expect(canvas.height).toBe(expectedBackingHeight);
      expect(context.setTransform).toHaveBeenCalledWith(ratio, 0, 0, ratio, 0, 0);
    }
  });

  it("uses static resting pattern when levels are undefined, empty, or silent", () => {
    const { drawnBars } = stubCanvasRendering();
    render(<SoundBars levels={SILENT_AUDIO_LEVELS} />);

    const restingHeights = drawnBars.slice(-EXPECTED_BAR_COUNT).map((bar) => bar.height);

    const canvas = document.createElement("canvas");
    const { drawnBars: explicitRestingBars } = stubCanvasRendering();
    drawSoundBars(canvas, []);
    const explicitRestingHeights = explicitRestingBars.map((bar) => bar.height);

    expect(restingHeights).toEqual(explicitRestingHeights);
  });

  it("draws active levels when audio is playing and reduced motion is disabled", () => {
    const { drawnBars: restingBars } = stubCanvasRendering();
    const restingCanvas = document.createElement("canvas");
    drawSoundBars(restingCanvas, SILENT_AUDIO_LEVELS);

    const { drawnBars: activeBars } = stubCanvasRendering();
    const activeCanvas = document.createElement("canvas");
    drawSoundBars(activeCanvas, ACTIVE_AUDIO_LEVELS);

    expect(activeBars.map((bar) => bar.height)).not.toEqual(restingBars.map((bar) => bar.height));
  });

  it("respects prefers-reduced-motion by drawing the static resting state even when audio is active", () => {
    reducedMotionMatches = true;

    const { drawnBars: restingBars } = stubCanvasRendering();
    const restingCanvas = document.createElement("canvas");
    drawSoundBars(restingCanvas, SILENT_AUDIO_LEVELS);

    const { drawnBars: activeBarsUnderReducedMotion } = stubCanvasRendering();
    const activeCanvas = document.createElement("canvas");
    drawSoundBars(activeCanvas, ACTIVE_AUDIO_LEVELS);

    expect(activeBarsUnderReducedMotion.map((bar) => bar.height)).toEqual(restingBars.map((bar) => bar.height));
  });

  it("reacts to prefers-reduced-motion media query changes by redrawing", () => {
    const { drawnBars } = stubCanvasRendering();
    render(<SoundBars levels={ACTIVE_AUDIO_LEVELS} />);

    const barCountBeforeChange = drawnBars.length;
    expect(barCountBeforeChange).toBeGreaterThanOrEqual(EXPECTED_BAR_COUNT);

    act(() => {
      mediaQueryListeners.forEach((listener) => listener({ matches: true }));
    });

    expect(drawnBars.length).toBeGreaterThan(barCountBeforeChange);
  });
});
