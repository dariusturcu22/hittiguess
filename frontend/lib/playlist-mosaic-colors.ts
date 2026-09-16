/**
 * The playlist list endpoint returns a single accent color per playlist
 * (`PlaylistSummaryDTO.color`), but the card mosaic in the design needs four
 * tile colors. These are derived from that one real color by shifting hue
 * and lightness, rather than invented independently of the playlist's own
 * data.
 */

interface HslColor {
  hue: number;
  saturation: number;
  lightness: number;
}

function normalizeHex(color: string): string {
  return color.startsWith("#") ? color : `#${color}`;
}

function hexToHsl(hex: string): HslColor {
  const normalized = normalizeHex(hex);
  const red = parseInt(normalized.slice(1, 3), 16) / 255;
  const green = parseInt(normalized.slice(3, 5), 16) / 255;
  const blue = parseInt(normalized.slice(5, 7), 16) / 255;

  const max = Math.max(red, green, blue);
  const min = Math.min(red, green, blue);
  const lightness = (max + min) / 2;

  if (max === min) {
    return { hue: 0, saturation: 0, lightness };
  }

  const delta = max - min;
  const saturation =
    lightness > 0.5 ? delta / (2 - max - min) : delta / (max + min);

  let hue: number;
  switch (max) {
    case red:
      hue = (green - blue) / delta + (green < blue ? 6 : 0);
      break;
    case green:
      hue = (blue - red) / delta + 2;
      break;
    default:
      hue = (red - green) / delta + 4;
  }
  hue *= 60;

  return { hue, saturation, lightness };
}

function hueToRgbChannel(p: number, q: number, hueFraction: number): number {
  let fraction = hueFraction;
  if (fraction < 0) fraction += 1;
  if (fraction > 1) fraction -= 1;
  if (fraction < 1 / 6) return p + (q - p) * 6 * fraction;
  if (fraction < 1 / 2) return q;
  if (fraction < 2 / 3) return p + (q - p) * (2 / 3 - fraction) * 6;
  return p;
}

function hslToHex({ hue, saturation, lightness }: HslColor): string {
  const normalizedHue = ((hue % 360) + 360) % 360;

  if (saturation === 0) {
    const channel = Math.round(lightness * 255);
    const hex = channel.toString(16).padStart(2, "0");
    return `#${hex}${hex}${hex}`;
  }

  const q =
    lightness < 0.5
      ? lightness * (1 + saturation)
      : lightness + saturation - lightness * saturation;
  const p = 2 * lightness - q;
  const hueFraction = normalizedHue / 360;

  const red = hueToRgbChannel(p, q, hueFraction + 1 / 3);
  const green = hueToRgbChannel(p, q, hueFraction);
  const blue = hueToRgbChannel(p, q, hueFraction - 1 / 3);

  const toHex = (channel: number) =>
    Math.round(channel * 255)
      .toString(16)
      .padStart(2, "0");

  return `#${toHex(red)}${toHex(green)}${toHex(blue)}`;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

const MOSAIC_TILE_HUE_SHIFTS = [0, 42, -36, 16];
const MOSAIC_TILE_LIGHTNESS_SHIFTS = [0, 0.07, -0.09, 0.16];

/**
 * Returns four hex colors for a playlist's cover mosaic: the playlist's own
 * color, unchanged, plus three variants shifted in hue and lightness.
 */
export function getPlaylistMosaicColors(color: string): [
  string,
  string,
  string,
  string,
] {
  const base = hexToHsl(color);

  return MOSAIC_TILE_HUE_SHIFTS.map((hueShift, index) =>
    hslToHex({
      hue: base.hue + hueShift,
      saturation: base.saturation,
      lightness: clamp(
        base.lightness + MOSAIC_TILE_LIGHTNESS_SHIFTS[index],
        0.15,
        0.85,
      ),
    }),
  ) as [string, string, string, string];
}
