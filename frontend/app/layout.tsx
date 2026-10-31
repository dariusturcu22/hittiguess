import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import "./globals.css";
import Providers from "@/components/providers";

const PRODUCTION_URL = "https://hittiguess.com";
const appBaseUrl = process.env.NEXT_PUBLIC_APP_URL || PRODUCTION_URL;

export const metadata: Metadata = {
  metadataBase: new URL(appBaseUrl),
  title: {
    template: "%s | hittiguess",
    default: "hittiguess | Multiplayer Music Guessing Game",
  },
  description:
    "A multiplayer music guessing game inspired by Hitster. Hear a song, guess when it was released, and build a chronological timeline with friends.",
  alternates: {
    canonical: "./",
  },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#eff1f5" },
    { media: "(prefers-color-scheme: dark)", color: "#1e1e2e" },
  ],
};

// Served from the repo rather than fetched from Google Fonts at build time, so a
// change in Google's responses can't break the build. Latin subsets only, as before.
const bungee = localFont({
  variable: "--font-bungee",
  src: [{ path: "./fonts/bungee-400.woff2", weight: "400" }],
});

const baloo2 = localFont({
  variable: "--font-baloo-2",
  src: [
    { path: "./fonts/baloo-2-700.woff2", weight: "700" },
    { path: "./fonts/baloo-2-800.woff2", weight: "800" },
  ],
});

const spaceGrotesk = localFont({
  variable: "--font-space-grotesk",
  src: [
    { path: "./fonts/space-grotesk-400.woff2", weight: "400" },
    { path: "./fonts/space-grotesk-500.woff2", weight: "500" },
    { path: "./fonts/space-grotesk-600.woff2", weight: "600" },
    { path: "./fonts/space-grotesk-700.woff2", weight: "700" },
  ],
});

// Loaded for the printed card preview's own typography (a settled, separate
// design from story 47), not for page chrome. See SongForm/AddSongForm/
// SongReadOnlyView, which hardcode 'Kanit' directly on the card preview.
const kanit = localFont({
  variable: "--font-kanit",
  src: [
    { path: "./fonts/kanit-300.woff2", weight: "300" },
    { path: "./fonts/kanit-400.woff2", weight: "400" },
    { path: "./fonts/kanit-500.woff2", weight: "500" },
    { path: "./fonts/kanit-600.woff2", weight: "600" },
  ],
});

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${bungee.variable} ${baloo2.variable} ${spaceGrotesk.variable} ${kanit.variable} antialiased`}
      >
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
