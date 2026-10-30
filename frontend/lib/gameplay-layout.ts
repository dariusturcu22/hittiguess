const CARD_SIZE_HEIGHT_RATIO = 0.21;
const CARD_SIZE_WIDTH_RATIO = 0.2;
const MINIMUM_CARD_SIZE_PIXELS = 132;
const MAXIMUM_CARD_SIZE_PIXELS = 220;
const FONT_SEARCH_PRECISION_PIXELS = 0.25;

// One card size for the whole game screen, derived from the space the screen actually has,
// so the timeline grows on big windows and shrinks on small ones without anything overlapping.
export function computeGameplayCardSize(availableWidth: number, availableHeight: number): number {
  const fittingSize = Math.min(availableHeight * CARD_SIZE_HEIGHT_RATIO, availableWidth * CARD_SIZE_WIDTH_RATIO);
  return Math.round(Math.min(MAXIMUM_CARD_SIZE_PIXELS, Math.max(MINIMUM_CARD_SIZE_PIXELS, fittingSize)));
}

// The largest font size between the bounds for which `fits` holds. The maximum is tried
// first, so text that already fits is never shrunk; text that cannot fit even at the
// minimum gets the minimum.
export function fitFontSize(maximumSize: number, minimumSize: number, fits: (size: number) => boolean): number {
  if (maximumSize <= minimumSize || fits(maximumSize)) return Math.max(maximumSize, minimumSize);
  if (!fits(minimumSize)) return minimumSize;
  let fittingSize = minimumSize;
  let overflowingSize = maximumSize;
  while (overflowingSize - fittingSize > FONT_SEARCH_PRECISION_PIXELS) {
    const candidateSize = (fittingSize + overflowingSize) / 2;
    if (fits(candidateSize)) fittingSize = candidateSize;
    else overflowingSize = candidateSize;
  }
  return fittingSize;
}
