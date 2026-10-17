const MINIMUM_CARD_LUMINANCE = 100;
const MAXIMUM_CHANNEL_VALUE = 255;
const HEX_CHANNEL_LENGTH = 2;
const HEX_RADIX = 16;
const RED_WEIGHT = 0.299;
const GREEN_WEIGHT = 0.587;
const BLUE_WEIGHT = 0.114;
const HEX_COLOR_PATTERN = /^#[0-9a-fA-F]{6}$/;

function channels(hexColor: string): [number, number, number] {
  const hex = hexColor.slice(1);
  return [0, 1, 2].map((index) => parseInt(hex.slice(index * HEX_CHANNEL_LENGTH, (index + 1) * HEX_CHANNEL_LENGTH), HEX_RADIX)) as [number, number, number];
}

function luminanceOf([red, green, blue]: [number, number, number]): number {
  return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue;
}

// Song cards print dark text on a flat color, so a very dark color is both hard to read
// and lost against the dark page. A color below the minimum brightness is mixed toward
// white just far enough to reach it, which keeps its hue. Anything else is returned as is.
export function readableCardColor(hexColor: string): string {
  if (!HEX_COLOR_PATTERN.test(hexColor)) return hexColor;
  const rgb = channels(hexColor);
  const luminance = luminanceOf(rgb);
  if (luminance >= MINIMUM_CARD_LUMINANCE) return hexColor;
  const mix = (MINIMUM_CARD_LUMINANCE - luminance) / (MAXIMUM_CHANNEL_VALUE - luminance);
  const lifted = rgb.map((channel) => Math.round(channel + (MAXIMUM_CHANNEL_VALUE - channel) * mix));
  return `#${lifted.map((channel) => channel.toString(HEX_RADIX).padStart(HEX_CHANNEL_LENGTH, "0")).join("")}`;
}
