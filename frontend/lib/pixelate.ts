"use client";

export const PIXEL_GRID_SIZE = 32;

// Shrinks an uploaded picture onto a tiny grid without smoothing, so the stored
// bytes are pixel art by construction rather than by convention.
export function pixelizeImage(imageFile: File, gridSize: number = PIXEL_GRID_SIZE): Promise<Blob> {
  return new Promise((resolve, reject) => {
    const sourceUrl = URL.createObjectURL(imageFile);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(sourceUrl);
      const canvas = document.createElement("canvas");
      canvas.width = gridSize;
      canvas.height = gridSize;
      const context = canvas.getContext("2d");
      if (!context) {
        reject(new Error("Canvas is unavailable"));
        return;
      }
      context.imageSmoothingEnabled = false;
      const coverScale = Math.max(gridSize / image.width, gridSize / image.height);
      const drawnWidth = image.width * coverScale;
      const drawnHeight = image.height * coverScale;
      context.drawImage(
        image,
        (gridSize - drawnWidth) / 2,
        (gridSize - drawnHeight) / 2,
        drawnWidth,
        drawnHeight,
      );
      canvas.toBlob(
        (pixelBlob) => (pixelBlob ? resolve(pixelBlob) : reject(new Error("Pixel art export failed"))),
        "image/png",
      );
    };
    image.onerror = () => {
      URL.revokeObjectURL(sourceUrl);
      reject(new Error("File is not a readable image"));
    };
    image.src = sourceUrl;
  });
}
