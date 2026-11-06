import type { MetadataRoute } from "next";

const BACKGROUND_DARK = "#181825";
const THEME_LIGHT = "#eff1f5";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "hittiguess",
    short_name: "hittiguess",
    description:
      "A multiplayer music guessing game inspired by Hitster. Hear a song, guess when it was released, and build a timeline with friends.",
    start_url: "/",
    display: "standalone",
    background_color: BACKGROUND_DARK,
    theme_color: THEME_LIGHT,
    icons: [
      {
        src: "/icon-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icon-maskable-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "maskable",
      },
      {
        src: "/icon-maskable-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}
