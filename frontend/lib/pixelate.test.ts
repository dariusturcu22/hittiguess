import { beforeEach, describe, expect, it, vi } from "vitest";

import { PIXEL_GRID_SIZE, pixelizeImage } from "./pixelate";

const TEST_IMAGE_WIDTH = 200;
const TEST_IMAGE_HEIGHT = 100;

function mockImageEnvironment() {
  const drawnCalls: Array<unknown[]> = [];
  const context = {
    imageSmoothingEnabled: true,
    drawImage: vi.fn((...callArgs: unknown[]) => {
      drawnCalls.push(callArgs);
    }),
  };
  const canvas = {
    width: 0,
    height: 0,
    getContext: vi.fn(() => context),
    toBlob: vi.fn((callback: (pixelBlob: Blob | null) => void) => {
      callback(new Blob(["pixels"], { type: "image/png" }));
    }),
    toDataURL: vi.fn(() => "data:image/png;base64,pixels"),
  };
  class FakeImage {
    static current: FakeImage | null = null;
    onload: (() => void) | null = null;
    onerror: (() => void) | null = null;
    src = "";
    width = TEST_IMAGE_WIDTH;
    height = TEST_IMAGE_HEIGHT;

    constructor() {
      FakeImage.current = this;
    }
  }

  vi.stubGlobal("Image", FakeImage);
  vi.spyOn(document, "createElement").mockImplementation(((tagName: string) => {
    if (tagName === "canvas") {
      return canvas as unknown as HTMLElement;
    }
    return document.createElement(tagName);
  }) as typeof document.createElement);
  vi.stubGlobal("URL", {
    createObjectURL: vi.fn(() => "blob:source"),
    revokeObjectURL: vi.fn(),
  });
  return {
    context,
    canvas,
    drawnCalls,
    loadImage: () => FakeImage.current?.onload?.(),
    failImage: () => FakeImage.current?.onerror?.(),
  };
}

describe("pixelizeImage", () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("draws the upload cover-fit onto the pixel grid without smoothing", async () => {
    const { context, canvas, drawnCalls, loadImage } = mockImageEnvironment();
    const pendingPixels = pixelizeImage(new File(["source"], "cover.jpg", { type: "image/jpeg" }));

    loadImage();
    const pixelBlob = await pendingPixels;

    expect(pixelBlob).toBeInstanceOf(Blob);
    expect(canvas.width).toBe(PIXEL_GRID_SIZE);
    expect(canvas.height).toBe(PIXEL_GRID_SIZE);
    expect(context.imageSmoothingEnabled).toBe(false);
    expect(drawnCalls).toHaveLength(1);
  });

  it("rejects files that never load as images", async () => {
    const { failImage } = mockImageEnvironment();
    const pendingPixels = pixelizeImage(new File(["nope"], "cover.txt", { type: "text/plain" }));

    failImage();
    await expect(pendingPixels).rejects.toThrow("File is not a readable image");
  });
});
