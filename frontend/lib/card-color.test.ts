import { describe, expect, it } from "vitest";

import { readableCardColor } from "./card-color";

function luminance(hexColor: string): number {
  const [red, green, blue] = [1, 3, 5].map((start) => parseInt(hexColor.slice(start, start + 2), 16));
  return 0.299 * red + 0.587 * green + 0.114 * blue;
}

describe("readableCardColor", () => {
  it("leaves a bright color unchanged", () => {
    expect(readableCardColor("#74c7ec")).toBe("#74c7ec");
    expect(readableCardColor("#ffffff")).toBe("#ffffff");
  });

  it("lifts a very dark color to the minimum brightness", () => {
    for (const darkColor of ["#000000", "#11111b", "#1e1e2e", "#2a1a4a", "#001a00"]) {
      expect(luminance(readableCardColor(darkColor))).toBeGreaterThanOrEqual(99);
    }
  });

  it("keeps the hue of a dark color while lifting it", () => {
    const [red, green, blue] = [1, 3, 5].map((start) => parseInt(readableCardColor("#10204a").slice(start, start + 2), 16));

    expect(blue).toBeGreaterThan(red);
    expect(blue).toBeGreaterThan(green);
  });

  it("passes through anything that is not a six digit hex color", () => {
    expect(readableCardColor("rebeccapurple")).toBe("rebeccapurple");
  });
});
