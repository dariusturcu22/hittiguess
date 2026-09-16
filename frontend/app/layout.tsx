import { Bungee, Baloo_2, Space_Grotesk, Kanit } from "next/font/google";
import "./globals.css";
import Providers from "@/components/providers";

const bungee = Bungee({
  variable: "--font-bungee",
  subsets: ["latin"],
  weight: ["400"],
});

const baloo2 = Baloo_2({
  variable: "--font-baloo-2",
  subsets: ["latin"],
  weight: ["700", "800"],
});

const spaceGrotesk = Space_Grotesk({
  variable: "--font-space-grotesk",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
});

// Loaded for the printed card preview's own typography (a settled, separate
// design from story 47), not for page chrome. See SongForm/AddSongForm/
// SongReadOnlyView, which hardcode 'Kanit' directly on the card preview.
const kanit = Kanit({
  variable: "--font-kanit",
  subsets: ["latin"],
  weight: ["300", "400", "500", "600"],
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
