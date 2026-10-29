import { describe, expect, it, vi } from "vitest";

import {
  createPlacementPreviewThrottle,
  placementPreviewFractions,
  type PlacementPreviewMessage,
} from "./placement-preview";

function timelineTrack(bounds: Partial<DOMRect> = {}): HTMLElement {
  return {
    getBoundingClientRect: () => ({
      left: 100,
      top: 200,
      width: 1000,
      height: 200,
      right: 1100,
      bottom: 400,
      x: 100,
      y: 200,
      toJSON: () => ({}),
      ...bounds,
    } as DOMRect),
  } as HTMLElement;
}

function fakeScheduler() {
  let currentTime = 0;
  let nextHandle = 1;
  const frames = new Map<number, FrameRequestCallback>();
  return {
    scheduler: {
      now: () => currentTime,
      requestFrame: (callback: FrameRequestCallback) => {
        const handle = nextHandle++;
        frames.set(handle, callback);
        return handle;
      },
      cancelFrame: (handle: number) => {
        frames.delete(handle);
      },
    },
    runFrame(milliseconds: number) {
      currentTime += milliseconds;
      const [handle, callback] = frames.entries().next().value ?? [];
      if (handle === undefined || callback === undefined) return;
      frames.delete(handle);
      callback(currentTime);
    },
    pendingFrames: () => frames.size,
  };
}

const DRAGGING_PREVIEW: PlacementPreviewMessage = { position: 2, x: 0.5, y: 0.1, phase: "DRAGGING" };

describe("placementPreviewFractions", () => {
  it("maps the lifted card center onto fractions of the timeline box", () => {
    const fractions = placementPreviewFractions(timelineTrack(), 600, 350, 118);

    expect(fractions).toEqual({ x: 0.5, y: 0.16 });
  });

  it("returns null without a measurable timeline", () => {
    expect(placementPreviewFractions(null, 0, 0, 0)).toBeNull();
    expect(placementPreviewFractions(timelineTrack({ width: 0 }), 0, 0, 0)).toBeNull();
    expect(placementPreviewFractions(timelineTrack({ height: 0 }), 0, 0, 0)).toBeNull();
  });
});

describe("createPlacementPreviewThrottle", () => {
  it("sends the first preview on the next frame and drops queued ones until the interval passes", () => {
    const send = vi.fn();
    const clock = fakeScheduler();
    const throttle = createPlacementPreviewThrottle(send, clock.scheduler);

    throttle.push({ ...DRAGGING_PREVIEW, position: 1 });
    clock.runFrame(16);
    expect(send).toHaveBeenLastCalledWith({ ...DRAGGING_PREVIEW, position: 1 });

    throttle.push({ ...DRAGGING_PREVIEW, position: 2 });
    clock.runFrame(16);
    throttle.push({ ...DRAGGING_PREVIEW, position: 3 });
    clock.runFrame(16);
    expect(send).toHaveBeenCalledTimes(1);

    clock.runFrame(16);
    clock.runFrame(16);
    expect(send).toHaveBeenCalledTimes(2);
    expect(send).toHaveBeenLastCalledWith({ ...DRAGGING_PREVIEW, position: 3 });
  });

  it("sends an immediate preview without waiting for a frame", () => {
    const send = vi.fn();
    const clock = fakeScheduler();
    const throttle = createPlacementPreviewThrottle(send, clock.scheduler);

    throttle.push({ ...DRAGGING_PREVIEW, position: 1 });
    throttle.sendNow({ ...DRAGGING_PREVIEW, position: 4, phase: "DROPPED" });

    expect(send).toHaveBeenCalledOnce();
    expect(send).toHaveBeenCalledWith({ ...DRAGGING_PREVIEW, position: 4, phase: "DROPPED" });
    expect(clock.pendingFrames()).toBe(0);
  });

  it("cancels a queued preview without sending it", () => {
    const send = vi.fn();
    const clock = fakeScheduler();
    const throttle = createPlacementPreviewThrottle(send, clock.scheduler);

    throttle.push(DRAGGING_PREVIEW);
    throttle.cancel();

    expect(send).not.toHaveBeenCalled();
    expect(clock.pendingFrames()).toBe(0);
  });
});
