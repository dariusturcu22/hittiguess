export const PLACEMENT_PREVIEW_IDLE_PHASE = "IDLE" as const;
export const PLACEMENT_PREVIEW_DRAGGING_PHASE = "DRAGGING" as const;
export const PLACEMENT_PREVIEW_DROPPED_PHASE = "DROPPED" as const;

// The active player's client is throttled to this interval; the backend drops anything
// beyond its own per-player limit instead of erroring.
export const PLACEMENT_PREVIEW_MINIMUM_INTERVAL_MILLISECONDS = 50;

export type PlacementPreviewPhase =
  | typeof PLACEMENT_PREVIEW_IDLE_PHASE
  | typeof PLACEMENT_PREVIEW_DRAGGING_PHASE
  | typeof PLACEMENT_PREVIEW_DROPPED_PHASE;

export interface PlacementPreviewMessage {
  position: number | null;
  x?: number;
  y?: number;
  phase: PlacementPreviewPhase;
}

export interface PlacementPreviewThrottle {
  push: (preview: PlacementPreviewMessage) => void;
  sendNow: (preview: PlacementPreviewMessage) => void;
  cancel: () => void;
}

interface ThrottleScheduler {
  now: () => number;
  requestFrame: (callback: FrameRequestCallback) => number;
  cancelFrame: (handle: number) => void;
}

const browserScheduler: ThrottleScheduler = {
  now: () => performance.now(),
  requestFrame: (callback) => window.requestAnimationFrame(callback),
  cancelFrame: (handle) => window.cancelAnimationFrame(handle),
};

// The card's center as fractions of the timeline box, with the same lift the active
// player's dragged card uses, so every client maps it onto its own screen size.
export function placementPreviewFractions(
  track: HTMLElement | null,
  clientX: number,
  clientY: number,
  liftPixels: number,
): { x: number; y: number } | null {
  if (!track) return null;
  const trackBounds = track.getBoundingClientRect();
  if (trackBounds.width <= 0 || trackBounds.height <= 0) return null;
  return {
    x: (clientX - trackBounds.left) / trackBounds.width,
    y: (clientY - liftPixels - trackBounds.top) / trackBounds.height,
  };
}

export function createPlacementPreviewThrottle(
  send: (preview: PlacementPreviewMessage) => void,
  scheduler: ThrottleScheduler = browserScheduler,
): PlacementPreviewThrottle {
  let pendingPreview: PlacementPreviewMessage | null = null;
  let frameHandle: number | null = null;
  let lastSentAt = Number.NEGATIVE_INFINITY;

  function flush(): void {
    frameHandle = null;
    if (pendingPreview === null) return;
    if (scheduler.now() - lastSentAt < PLACEMENT_PREVIEW_MINIMUM_INTERVAL_MILLISECONDS) {
      frameHandle = scheduler.requestFrame(flush);
      return;
    }
    const preview = pendingPreview;
    pendingPreview = null;
    lastSentAt = scheduler.now();
    send(preview);
  }

  return {
    push(preview) {
      pendingPreview = preview;
      if (frameHandle === null) frameHandle = scheduler.requestFrame(flush);
    },
    sendNow(preview) {
      if (frameHandle !== null) {
        scheduler.cancelFrame(frameHandle);
        frameHandle = null;
      }
      pendingPreview = null;
      lastSentAt = scheduler.now();
      send(preview);
    },
    cancel() {
      if (frameHandle !== null) {
        scheduler.cancelFrame(frameHandle);
        frameHandle = null;
      }
      pendingPreview = null;
    },
  };
}
