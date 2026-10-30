import { describe, expect, it } from "vitest";

import { computeGameplayCardSize, fitFontSize } from "./gameplay-layout";

const PHONE_WIDTH_PIXELS = 375;
const PHONE_HEIGHT_PIXELS = 812;
const DESKTOP_WIDTH_PIXELS = 1440;
const DESKTOP_HEIGHT_PIXELS = 900;
const WIDE_WIDTH_PIXELS = 1920;
const WIDE_HEIGHT_PIXELS = 1080;
const MINIMUM_CARD_SIZE_PIXELS = 132;
const MAXIMUM_CARD_SIZE_PIXELS = 220;
const PREVIOUS_FIXED_CARD_SIZE_PIXELS = 168;
const MAXIMUM_FONT_PIXELS = 16;
const MINIMUM_FONT_PIXELS = 6;
const LARGEST_FITTING_FONT_PIXELS = 11;
const PRECISION_PIXELS = 0.25;

describe("computeGameplayCardSize", () => {
  it("keeps cards at the minimum on a phone", () => {
    expect(computeGameplayCardSize(PHONE_WIDTH_PIXELS, PHONE_HEIGHT_PIXELS)).toBe(MINIMUM_CARD_SIZE_PIXELS);
  });

  it("makes cards larger than the old fixed size on a desktop window", () => {
    expect(computeGameplayCardSize(DESKTOP_WIDTH_PIXELS, DESKTOP_HEIGHT_PIXELS)).toBeGreaterThan(PREVIOUS_FIXED_CARD_SIZE_PIXELS);
  });

  it("caps the card size on large windows", () => {
    expect(computeGameplayCardSize(WIDE_WIDTH_PIXELS, WIDE_HEIGHT_PIXELS)).toBeLessThanOrEqual(MAXIMUM_CARD_SIZE_PIXELS);
  });
});

describe("fitFontSize", () => {
  it("returns the maximum when the text already fits", () => {
    expect(fitFontSize(MAXIMUM_FONT_PIXELS, MINIMUM_FONT_PIXELS, () => true)).toBe(MAXIMUM_FONT_PIXELS);
  });

  it("returns the largest size that still fits", () => {
    const size = fitFontSize(MAXIMUM_FONT_PIXELS, MINIMUM_FONT_PIXELS, (candidate) => candidate <= LARGEST_FITTING_FONT_PIXELS);

    expect(size).toBeLessThanOrEqual(LARGEST_FITTING_FONT_PIXELS);
    expect(size).toBeGreaterThan(LARGEST_FITTING_FONT_PIXELS - PRECISION_PIXELS);
  });

  it("falls back to the minimum when nothing fits", () => {
    expect(fitFontSize(MAXIMUM_FONT_PIXELS, MINIMUM_FONT_PIXELS, () => false)).toBe(MINIMUM_FONT_PIXELS);
  });
});
