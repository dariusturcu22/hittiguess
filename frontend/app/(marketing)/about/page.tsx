import Link from "next/link";
import { LogoBars } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";
import { PublicFooter, GITHUB_REPOSITORY_URL } from "@/components/public-footer";

export const metadata = {
  title: "About",
  description:
    "Learn about hittiguess, how rounds work, our open-source philosophy, and who builds the game.",
  alternates: {
    canonical: "/about",
  },
};

const LOGO_BAR_WIDTH_PIXELS = 4;

const ROUND_RULES_SENTENCES = [
  "The designated DJ plays the mystery song on YouTube without revealing the title or artist to the group.",
  "The active player listens to the track and places the card on their timeline where they believe the release year belongs.",
  "Other players can wager a token on where they think the track belongs before the year is revealed.",
  "The true release year is revealed, correct timeline placements and token bets score points, and the turn passes to the next player.",
];

const CORE_VALUES = [
  {
    title: "100% Free Forever",
    description:
      "There are no paywalls, subscription fees, premium tiers, or microtransactions. The full game is accessible to all players without cost.",
  },
  {
    title: "Fully Open Source",
    description:
      "The source code for both services and the web client is publicly available under the MIT licence. Anyone can inspect how the game works or self-host it.",
  },
  {
    title: "No Ads or Trackers",
    description:
      "We operate zero commercial advertisements, zero analytics trackers, and zero third-party profiling cookies. Browser storage is strictly limited to essential sessions and preferences.",
  },
];

export default function AboutPage() {
  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <header className="border-b border-border bg-card/60 backdrop-blur-sm sticky top-0 z-30">
        <div className="max-w-5xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link href="/" aria-label="hittiguess home" className="flex items-center gap-2.5">
            <LogoBars
              barWidthPx={LOGO_BAR_WIDTH_PIXELS}
              colorClassName="bg-primary"
              containerClassName="h-[18px]"
            />
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

      <main className="max-w-4xl mx-auto px-6 py-12 flex-1 w-full space-y-12">
        <section className="space-y-4">
          <h1 className="font-display text-3xl sm:text-4xl font-extrabold text-primary">
            About hittiguess
          </h1>
          <p className="text-lg text-foreground font-medium leading-relaxed">
            hittiguess is a multiplayer music guessing game inspired by Hitster. Players listen to
            songs spanning iconic hits, niche gems, and forgotten classics, estimate the release
            year, and build a personal chronological timeline.
          </p>
          <p className="text-muted-foreground leading-relaxed">
            The game is crafted specifically for friend groups. Whether gathered around a table in
            the same room or hanging out remotely over integrated voice and text chat, hittiguess
            delivers a cooperative and competitive musical trivia experience without cumbersome setup.
          </p>
        </section>

        <section className="space-y-6">
          <div className="border-b border-border pb-3">
            <h2 className="font-display text-2xl font-bold text-foreground">
              How a Round Works
            </h2>
            <p className="text-sm text-muted-foreground mt-1">
              Gameplay is straightforward and quick to learn.
            </p>
          </div>
          <div className="grid grid-cols-1 gap-4">
            {ROUND_RULES_SENTENCES.map((sentence, index) => {
              const stepNumber = index + 1;
              return (
                <div
                  key={stepNumber}
                  className="p-5 rounded-2xl border border-border bg-card shadow-sm flex items-start gap-4"
                >
                  <span className="flex-shrink-0 w-8 h-8 rounded-full bg-primary/10 text-primary font-display font-bold flex items-center justify-center text-sm">
                    {stepNumber}
                  </span>
                  <p className="text-foreground leading-relaxed pt-1">{sentence}</p>
                </div>
              );
            })}
          </div>
        </section>

        <section className="space-y-6">
          <div className="border-b border-border pb-3">
            <h2 className="font-display text-2xl font-bold text-foreground">
              Free, Open Source, and Private
            </h2>
            <p className="text-sm text-muted-foreground mt-1">
              Built as a public utility and party game, not a data-harvesting business.
            </p>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {CORE_VALUES.map((value) => (
              <div
                key={value.title}
                className="p-6 rounded-2xl border border-border bg-card shadow-sm space-y-2.5"
              >
                <h3 className="font-display text-base font-bold text-foreground">{value.title}</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  {value.description}
                </p>
              </div>
            ))}
          </div>
        </section>

        <section className="space-y-6">
          <div className="border-b border-border pb-3">
            <h2 className="font-display text-2xl font-bold text-foreground">
              Who Builds hittiguess
            </h2>
          </div>
          <div className="p-6 rounded-2xl border border-border bg-card shadow-sm space-y-4">
            <p className="text-foreground leading-relaxed">
              hittiguess is developed by Darius Turcu and open-source contributors on GitHub. It
              started as a passion project to bring the tactile fun of music timeline board games
              into a polished digital experience that friends can play anywhere.
            </p>
            <p className="text-muted-foreground leading-relaxed text-sm">
              The project is completely transparent and community-driven. You can explore the source
              code, review the technical documentation, report issues, or suggest new song playlists
              on GitHub.
            </p>
            <div className="pt-2 flex flex-wrap gap-4">
              <a
                href={GITHUB_REPOSITORY_URL}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center justify-center px-4 py-2 rounded-xl text-sm font-semibold bg-primary text-primary-foreground hover:opacity-90 transition-opacity"
              >
                View on GitHub
              </a>
              <Link
                href="/credits"
                className="inline-flex items-center justify-center px-4 py-2 rounded-xl text-sm font-semibold border border-border bg-background text-foreground hover:bg-muted transition-colors"
              >
                Credits &amp; Licences
              </Link>
            </div>
          </div>
        </section>
      </main>

      <PublicFooter />
    </div>
  );
}
