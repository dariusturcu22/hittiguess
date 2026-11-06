import Link from "next/link";
import { LogoBars } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";
import { PublicFooter } from "@/components/public-footer";

export const metadata = {
  title: "Credits & Licences | hittiguess",
  description:
    "Attribution and licences for metadata providers, fonts, and open-source software used in hittiguess.",
};

const DATA_SOURCES = [
  {
    name: "MusicBrainz",
    operator: "MetaBrainz Foundation",
    website: "https://musicbrainz.org",
    licence: "CC0 1.0 Universal / CC BY-NC-SA 3.0",
    description:
      "MusicBrainz is an open music encyclopedia that collects music metadata. Core database fields (artists, recordings, release groups, and earliest release dates) are dedicated to the public domain under Creative Commons Zero 1.0 (CC0). Supplementary content, including annotations and user ratings, is licensed under Creative Commons Attribution-NonCommercial-ShareAlike 3.0.",
  },
  {
    name: "Discogs",
    operator: "Zink Media, LLC",
    website: "https://www.discogs.com",
    licence: "Discogs API Terms of Use",
    description:
      "Data provided by Discogs. Discogs provides a user-built database of music recordings, catalog numbers, and physical release discographies accessed via the official Discogs API.",
  },
  {
    name: "Wikidata",
    operator: "Wikimedia Foundation",
    website: "https://www.wikidata.org",
    licence: "CC0 1.0 Universal Public Domain Dedication",
    description:
      "Wikidata is a collaborative, multilingual structured knowledge base. All structured data in the main and property namespaces is available worldwide under the Creative Commons Zero 1.0 Public Domain Dedication.",
  },
  {
    name: "Wikipedia",
    operator: "Wikimedia Foundation",
    website: "https://www.wikipedia.org",
    licence: "CC BY-SA 4.0 / GFDL",
    description:
      "Wikipedia is a free encyclopedia written by volunteer contributors. When structured databases disagree on an original release year, Wikipedia article prose for the track or album is consulted. Text is licensed under Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0) and the GNU Free Documentation License (GFDL). Specific articles consulted are attributed in song reconciliation records.",
  },
  {
    name: "YouTube",
    operator: "Google LLC",
    website: "https://www.youtube.com",
    licence: "YouTube Terms of Service / Google Privacy Policy",
    description:
      "Song audio playback, video durations, and playlist expansions are retrieved through official YouTube API Services. Audio playback occurs exclusively on the official YouTube website or in official YouTube mobile applications via external link-outs. Video content, audio, channel names, and thumbnails remain the intellectual property of their respective creators and copyright holders.",
  },
];

const FONTS = [
  {
    name: "Bungee",
    designer: "David Jonathan Ross",
    licence: "SIL Open Font License 1.1",
    purpose: "Display titles and hero headings",
  },
  {
    name: "Baloo 2",
    designer: "Ek Type",
    licence: "SIL Open Font License 1.1",
    purpose: "Brand logo and playful accents",
  },
  {
    name: "Space Grotesk",
    designer: "Florian Karsten",
    licence: "SIL Open Font License 1.1",
    purpose: "Primary user interface typography and body prose",
  },
  {
    name: "Kanit",
    designer: "Cadson Demak",
    licence: "SIL Open Font License 1.1",
    purpose: "Timeline card preview typography",
  },
];

const CLIENT_LIBRARIES = [
  { name: "Next.js", licence: "MIT", purpose: "React framework and server routing" },
  { name: "React", licence: "MIT", purpose: "Declarative component rendering" },
  { name: "Tailwind CSS", licence: "MIT", purpose: "Design tokens and utility styling" },
  { name: "Radix UI", licence: "MIT", purpose: "Accessible primitive interface components" },
  { name: "Lucide React", licence: "ISC", purpose: "User interface icons" },
  { name: "@sentry/nextjs", licence: "MIT", purpose: "Client error monitoring and diagnostics" },
  { name: "@stomp/stompjs", licence: "Apache 2.0", purpose: "WebSocket STOMP communication" },
  {
    name: "@sapphi-red/web-noise-suppressor",
    licence: "MIT / BSD 3-Clause",
    purpose: "RNNoise neural network noise suppression worklet",
  },
];

export default function CreditsPage() {
  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col justify-between">
      <header className="border-b border-border bg-card/60 backdrop-blur-sm sticky top-0 z-30">
        <div className="max-w-5xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link href="/" aria-label="hittiguess home" className="flex items-center gap-2.5">
            <LogoBars barWidthPx={4} colorClassName="bg-primary" containerClassName="h-[18px]" />
            <span className="font-display font-extrabold text-lg text-foreground">hittiguess</span>
          </Link>
          <div className="flex items-center gap-4">
            <Link
              href="/"
              className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
            >
              Home
            </Link>
            <ThemeToggle />
          </div>
        </div>
      </header>

      <main className="max-w-4xl mx-auto px-6 py-12 flex-1 w-full">
        <div className="mb-10">
          <h1 className="font-display text-3xl sm:text-4xl font-extrabold text-primary mb-3">
            Credits &amp; Licences
          </h1>
          <p className="text-muted-foreground leading-relaxed text-base">
            hittiguess is free, open source, and operated without advertisements or tracking. It
            depends on public music databases, open data platforms, open font collections, and
            open-source client software. Every data provider and library is attributed below in
            compliance with its respective terms and licence.
          </p>
        </div>

        <section className="mb-12">
          <h2 className="font-display text-xl sm:text-2xl font-bold mb-6 text-foreground border-b border-border pb-2">
            Music &amp; Metadata Sources
          </h2>
          <div className="space-y-6">
            {DATA_SOURCES.map((source) => (
              <div
                key={source.name}
                className="p-6 rounded-2xl border border-border bg-card shadow-sm space-y-2.5"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="font-display text-lg font-bold text-foreground">
                    <a
                      href={source.website}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="hover:underline text-primary"
                    >
                      {source.name}
                    </a>
                    <span className="text-xs font-normal text-muted-foreground ml-2">
                      ({source.operator})
                    </span>
                  </h3>
                  <span className="text-xs font-medium px-2.5 py-1 rounded-full bg-muted text-muted-foreground">
                    {source.licence}
                  </span>
                </div>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  {source.description}
                </p>
              </div>
            ))}
          </div>
        </section>

        <section className="mb-12">
          <h2 className="font-display text-xl sm:text-2xl font-bold mb-6 text-foreground border-b border-border pb-2">
            Typography &amp; Fonts
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {FONTS.map((font) => (
              <div
                key={font.name}
                className="p-5 rounded-2xl border border-border bg-card shadow-sm space-y-1.5"
              >
                <div className="flex items-center justify-between">
                  <h3 className="font-display text-base font-bold text-foreground">{font.name}</h3>
                  <span className="text-xs text-muted-foreground font-mono">{font.licence}</span>
                </div>
                <p className="text-xs text-muted-foreground">Designed by {font.designer}</p>
                <p className="text-xs text-muted-foreground">{font.purpose}</p>
              </div>
            ))}
          </div>
          <p className="text-xs text-muted-foreground mt-3">
            All fonts are licensed under the SIL Open Font License 1.1, allowing free use, study,
            modification, and redistribution.
          </p>
        </section>

        <section className="mb-8">
          <h2 className="font-display text-xl sm:text-2xl font-bold mb-6 text-foreground border-b border-border pb-2">
            Client Libraries &amp; Open Source Software
          </h2>
          <div className="rounded-2xl border border-border bg-card overflow-hidden shadow-sm">
            <div className="divide-y divide-border">
              {CLIENT_LIBRARIES.map((library) => (
                <div
                  key={library.name}
                  className="px-6 py-3.5 flex flex-wrap items-center justify-between gap-2 text-sm"
                >
                  <div>
                    <span className="font-semibold text-foreground">{library.name}</span>
                    <span className="text-xs text-muted-foreground ml-3">{library.purpose}</span>
                  </div>
                  <span className="text-xs font-mono text-muted-foreground bg-muted px-2 py-0.5 rounded">
                    {library.licence}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </section>
      </main>

      <PublicFooter />
    </div>
  );
}
