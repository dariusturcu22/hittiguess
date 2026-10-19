import { fireEvent, render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { PixelImageInput } from "./pixel-image-input";

vi.mock("@/lib/pixelate", () => ({
  pixelizeImage: vi.fn(async () => new Blob(["pixels"], { type: "image/png" })),
}));

describe("PixelImageInput", () => {
  beforeEach(() => {
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:preview"),
      revokeObjectURL: vi.fn(),
    });
  });
  it("pixelizes the chosen file and hands over the blob", async () => {
    const onPixelized = vi.fn();
    const { container } = render(
      <PixelImageInput label="Change picture" onPixelized={onPixelized}>
        Edit
      </PixelImageInput>,
    );

    fireEvent.change(container.querySelector('input[type="file"]')!, {
      target: { files: [new File(["source"], "photo.jpg", { type: "image/jpeg" })] },
    });

    await vi.waitFor(() => expect(onPixelized).toHaveBeenCalledOnce());
    expect(onPixelized.mock.calls[0]?.[0]).toBeInstanceOf(Blob);
    expect(onPixelized.mock.calls[0]?.[1]).toContain("blob:");
  });

  it("reports unreadable files without calling back", async () => {
    const { pixelizeImage } = await import("@/lib/pixelate");
    vi.mocked(pixelizeImage).mockRejectedValueOnce(new Error("File is not a readable image"));
    const onPixelized = vi.fn();
    const onError = vi.fn();
    const { container } = render(
      <PixelImageInput label="Change picture" onPixelized={onPixelized} onError={onError}>
        Edit
      </PixelImageInput>,
    );

    fireEvent.change(container.querySelector('input[type="file"]')!, {
      target: { files: [new File(["nope"], "photo.txt", { type: "text/plain" })] },
    });

    await vi.waitFor(() => expect(onError).toHaveBeenCalledWith("File is not a readable image"));
    expect(onPixelized).not.toHaveBeenCalled();
  });
});
