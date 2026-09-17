"use client";

import React from "react";
import Link from "next/link";
import { Button } from "@/components/shadcn/button";
import { LogoBars } from "@/components/logo";

const timelineCards = [
  { color: "#a6e3a1", artist: "RADIOHEAD", year: "1994", title: "Creep" },
  { color: "#f9e2af", artist: "SNOW PATROL", year: "2006", title: "Chasing Cars" },
];

// Positions below are read directly off docs/design/source/LandingDesktopDark.dc.html's
// "How a round works" section: a horizontal zigzag (steps 1 and 3 raised,
// steps 2 and 4 lowered) connected by one curved path drawn behind the
// icons, inside a 900x260 coordinate space. Expressing icon/label positions
// as percentages of that space, rather than literal pixels, lets the same
// layout scale to a container narrower than the mockup's own 1440px canvas
// without distorting the connecting curve, since the curve's viewBox shares
// the same aspect ratio as the container.
const ROUND_ZIGZAG_VIEWBOX_WIDTH = 900;
const ROUND_ZIGZAG_VIEWBOX_HEIGHT = 260;
const ROUND_ZIGZAG_CONNECTOR_PATH =
  "M115,55 Q226,-10 338,105 Q450,170 562,55 Q673,-10 785,105";

type RoundStep = {
  key: string;
  darkColor: string;
  lightColor: string;
  title: string;
  body: string;
  iconLeftPx: number;
  iconTopPx: number;
  labelTopPx: number;
  icon: React.ReactNode;
  stroke?: boolean;
};

const ROUND_STEPS: RoundStep[] = [
  {
    key: "dj-hits-play",
    darkColor: "#cba6f7",
    lightColor: "#8f4fe3",
    title: "DJ hits play",
    body: "Real YouTube, nothing hidden.",
    iconLeftPx: 115,
    iconTopPx: 55,
    labelTopPx: 122,
    icon: <path d="M8 5v14l11-7z" />,
  },
  {
    key: "you-place-it",
    darkColor: "#fab387",
    lightColor: "#ed6f27",
    title: "You place it",
    body: "On your own timeline.",
    iconLeftPx: 338,
    iconTopPx: 105,
    labelTopPx: 172,
    icon: (
      <>
        <path d="M12 21c-4.2-4.6-7-8.4-7-12a7 7 0 0 1 14 0c0 3.6-2.8 7.4-7 12z" />
        <circle cx="12" cy="9" r="2.4" />
      </>
    ),
    stroke: true,
  },
  {
    key: "others-bet",
    darkColor: "#a6e3a1",
    lightColor: "#499f36",
    title: "Others bet",
    body: "Wrong guess, stolen card.",
    iconLeftPx: 562,
    iconTopPx: 55,
    labelTopPx: 122,
    icon: (
      <>
        <circle cx="12" cy="12" r="9" />
        <circle cx="12" cy="12" r="4" />
        <line x1="12" y1="2.5" x2="12" y2="5.5" />
        <line x1="12" y1="18.5" x2="12" y2="21.5" />
        <line x1="2.5" y1="12" x2="5.5" y2="12" />
        <line x1="18.5" y1="12" x2="21.5" y2="12" />
      </>
    ),
    stroke: true,
  },
  {
    key: "reveal",
    darkColor: "#f9e2af",
    lightColor: "#d49032",
    title: "Reveal",
    body: "Artist, title, year, truth.",
    iconLeftPx: 785,
    iconTopPx: 105,
    labelTopPx: 172,
    icon: (
      <>
        <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z" />
        <circle cx="12" cy="12" r="3" />
      </>
    ),
    stroke: true,
  },
];

// Mobile's own zigzag, from LandingMobileDark.dc.html, is a genuinely
// different composition (icon and label sit side by side, staggered down
// the page) rather than a smaller version of the desktop layout, so it gets
// its own coordinate space and positions instead of reusing the desktop
// ones at a smaller scale.
const ROUND_ZIGZAG_MOBILE_VIEWBOX_WIDTH = 340;
const ROUND_ZIGZAG_MOBILE_VIEWBOX_HEIGHT = 550;
const ROUND_ZIGZAG_MOBILE_CONNECTOR_PATH =
  "M70,50 Q42,146 170,190 Q218,254 70,330 Q42,426 170,470";
const ROUND_STEPS_MOBILE_POSITIONS = [
  { iconLeftPx: 70, iconTopPx: 50, labelLeftPx: 115 },
  { iconLeftPx: 170, iconTopPx: 190, labelLeftPx: 215 },
  { iconLeftPx: 70, iconTopPx: 330, labelLeftPx: 115 },
  { iconLeftPx: 170, iconTopPx: 470, labelLeftPx: 215 },
];

// Each highlight section's own decorative shapes, positioned per
// LandingDesktopDark/Light.dc.html rather than derived from a single
// left/right formula: the mockup's three sections don't alternate
// symmetrically (two justify their card one way, the third centers it), and
// a shared formula previously placed each section's blob on the opposite
// side from its own text card, landing it in empty space instead of behind
// the content it's meant to bleed into.
type LandingDecorativeShape = {
  top?: string;
  right?: string;
  bottom?: string;
  left?: string;
  size: string;
  rotate?: string;
  darkColor: string;
  lightColor: string;
};

type LandingHighlight = {
  key: string;
  heading: string;
  body: string;
  justifyClassName: string;
  textAlignClassName: string;
  primaryShape: LandingDecorativeShape;
  secondaryShape: LandingDecorativeShape;
};

const HIGHLIGHTS: LandingHighlight[] = [
  {
    key: "your-music-your-rules",
    heading: "YOUR MUSIC. YOUR RULES.",
    body: "Build your own playlist. Import one. Play someone else's, or let us generate one for you.",
    justifyClassName: "md:justify-end",
    textAlignClassName: "text-left md:text-right",
    primaryShape: {
      top: "0px",
      right: "-100px",
      size: "480px",
      darkColor: "#89b4fa",
      lightColor: "#3772e6",
    },
    secondaryShape: {
      bottom: "-10px",
      left: "60px",
      size: "300px",
      rotate: "-15deg",
      darkColor: "#313244",
      lightColor: "#ccd0da",
    },
  },
  {
    key: "guess-it-bet-it",
    heading: "GUESS IT. BET IT.",
    body: "Name the artist and title for a token. Save it to bet someone else got their placement wrong, and steal the card if you're right.",
    justifyClassName: "md:justify-start",
    textAlignClassName: "text-left",
    primaryShape: {
      top: "-160px",
      left: "-140px",
      size: "460px",
      darkColor: "#f5c2e7",
      lightColor: "#e387cb",
    },
    secondaryShape: {
      bottom: "-120px",
      right: "-140px",
      size: "320px",
      rotate: "20deg",
      darkColor: "#fab387",
      lightColor: "#ed6f27",
    },
  },
  {
    key: "play-together-trust-built-in",
    heading: "PLAY TOGETHER. TRUST BUILT IN.",
    body: "Voice and text chat built in, no separate app needed. Official APIs only, GDPR-minded, no ads, no tracking, ever.",
    justifyClassName: "md:justify-center",
    textAlignClassName: "text-center",
    primaryShape: {
      top: "-140px",
      left: "520px",
      size: "400px",
      darkColor: "#a6e3a1",
      lightColor: "#499f36",
    },
    secondaryShape: {
      bottom: "-170px",
      left: "-100px",
      size: "340px",
      rotate: "-12deg",
      darkColor: "#89b4fa",
      lightColor: "#3772e6",
    },
  },
];

function TimelineCard({
  color,
  artist,
  year,
  title,
  rotate,
}: {
  color: string;
  artist?: string;
  year: string;
  title?: string;
  rotate: number;
}) {
  return (
    <div
      className="w-28 h-28 sm:w-36 sm:h-36 rounded-2xl border-[5px] flex flex-col items-center justify-center gap-1 p-3 text-center shrink-0"
      style={
        {
          background: color,
          borderColor: "var(--background)",
          color: "var(--background)",
          boxShadow: "6px 6px 0 var(--shadow-color)",
          transform: `rotate(${rotate}deg)`,
        } as React.CSSProperties
      }
    >
      {artist && (
        <div className="text-[9px] sm:text-[10px] font-bold">{artist}</div>
      )}
      <div className="font-display text-2xl sm:text-4xl leading-none">
        {year}
      </div>
      {title && (
        <div className="text-[9px] sm:text-[10px] font-medium opacity-80">
          {title}
        </div>
      )}
    </div>
  );
}

export default function LandingPage() {
  return (
    <div className="flex flex-col bg-background">
      <section className="relative overflow-hidden bg-dotted px-6 md:px-16 pt-4 pb-16 md:pb-24">
        <div className="absolute -top-[60px] -right-[35px] size-[170px] md:-top-40 md:-right-40 md:size-[560px] rounded-full bg-accent pointer-events-none" />

        <div className="relative z-10 grid md:grid-cols-2 gap-10 items-center max-w-6xl mx-auto">
          <div className="flex flex-col gap-6">
            <h1
              className="font-display text-4xl sm:text-5xl md:text-6xl leading-[1.05] text-marketing-accent"
              style={{ textShadow: "4px 4px 0 var(--text-shadow-on-page)" }}
            >
              PLACE IT.
              <br />
              GUESS IT.
              <br />
              WIN IT.
            </h1>
            <p className="text-muted-foreground text-base md:text-lg leading-relaxed max-w-md">
              Hear a song, guess the year, place it on your own timeline.
              Bring your own playlist or import one, either way it just
              works.
            </p>
            <div className="flex flex-col sm:flex-row sm:items-center gap-4 mt-1">
              <Button asChild size="lg">
                <Link href="/register">Create free account</Link>
              </Button>
              <span className="font-display text-xs text-muted-foreground">
                or{" "}
                <Link href="/login" className="text-marketing-accent underline underline-offset-4">
                  sign in
                </Link>
              </span>
            </div>
          </div>

          <div className="hidden md:flex items-end justify-end gap-3">
            <TimelineCard
              color={timelineCards[0].color}
              artist={timelineCards[0].artist}
              year={timelineCards[0].year}
              title={timelineCards[0].title}
              rotate={-8}
            />
            <div
              className="w-28 h-28 sm:w-36 sm:h-36 rounded-2xl border-[5px] border-border flex items-center justify-center mt-12"
              style={{
                background: "var(--card)",
                color: "var(--border)",
                boxShadow: "6px 6px 0 var(--shadow-color)",
                transform: "rotate(4deg)",
              }}
            >
              <span className="font-display text-4xl">?</span>
            </div>
            <TimelineCard
              color={timelineCards[1].color}
              artist={timelineCards[1].artist}
              year={timelineCards[1].year}
              title={timelineCards[1].title}
              rotate={-4}
            />
          </div>
        </div>
      </section>

      <section className="bg-dotted px-6 md:px-16 py-16 md:py-24">
        <h2
          className="font-display text-2xl md:text-3xl text-marketing-accent text-center"
          style={{ textShadow: "3px 3px 0 var(--text-shadow-on-page)" }}
        >
          How a round works
        </h2>

        {/* Desktop: horizontal zigzag, positions expressed as a percentage
            of the mockup's 900x260 coordinate space so the connecting curve
            keeps its shape at any container width. */}
        <div
          className="hidden md:block relative mx-auto mt-12 w-full max-w-[900px]"
          style={{ aspectRatio: `${ROUND_ZIGZAG_VIEWBOX_WIDTH} / ${ROUND_ZIGZAG_VIEWBOX_HEIGHT}` }}
        >
          <svg
            className="absolute inset-0 size-full"
            viewBox={`0 0 ${ROUND_ZIGZAG_VIEWBOX_WIDTH} ${ROUND_ZIGZAG_VIEWBOX_HEIGHT}`}
            preserveAspectRatio="none"
          >
            <path
              d={ROUND_ZIGZAG_CONNECTOR_PATH}
              fill="none"
              stroke="var(--icon-muted)"
              strokeWidth={5}
              strokeLinecap="round"
            />
          </svg>

          {ROUND_STEPS.map((step) => (
            <React.Fragment key={step.key}>
              <div
                className="absolute -translate-x-1/2 -translate-y-1/2 size-[84px] rounded-full border-[5px] border-border-strong flex items-center justify-center shadow-[4px_4px_0_rgba(76,79,105,0.35)] dark:shadow-[4px_4px_0_rgba(0,0,0,0.4)] bg-[var(--step-bg-light)] dark:bg-[var(--step-bg-dark)]"
                style={
                  {
                    left: `${(step.iconLeftPx / ROUND_ZIGZAG_VIEWBOX_WIDTH) * 100}%`,
                    top: `${(step.iconTopPx / ROUND_ZIGZAG_VIEWBOX_HEIGHT) * 100}%`,
                    "--step-bg-light": step.lightColor,
                    "--step-bg-dark": step.darkColor,
                  } as React.CSSProperties
                }
              >
                <svg
                  width="34"
                  height="34"
                  viewBox="0 0 24 24"
                  className="text-background dark:text-border-strong"
                  fill={step.stroke ? "none" : "currentColor"}
                  stroke={step.stroke ? "currentColor" : "none"}
                  strokeWidth={step.stroke ? 2.2 : 0}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  {step.icon}
                </svg>
              </div>
              <div
                className="absolute -translate-x-1/2 w-[190px] text-center"
                style={{
                  left: `${(step.iconLeftPx / ROUND_ZIGZAG_VIEWBOX_WIDTH) * 100}%`,
                  top: `${(step.labelTopPx / ROUND_ZIGZAG_VIEWBOX_HEIGHT) * 100}%`,
                }}
              >
                <div className="font-display text-sm text-marketing-accent">
                  {step.title}
                </div>
                <div className="text-muted-foreground text-sm mt-1.5">
                  {step.body}
                </div>
              </div>
            </React.Fragment>
          ))}
        </div>

        {/* Mobile: vertical zigzag, icon and label side by side, per
            LandingMobileDark/Light.dc.html's own composition. */}
        <div
          className="md:hidden relative mx-auto mt-10 w-full max-w-[340px]"
          style={{
            aspectRatio: `${ROUND_ZIGZAG_MOBILE_VIEWBOX_WIDTH} / ${ROUND_ZIGZAG_MOBILE_VIEWBOX_HEIGHT}`,
          }}
        >
          <svg
            className="absolute inset-0 size-full"
            viewBox={`0 0 ${ROUND_ZIGZAG_MOBILE_VIEWBOX_WIDTH} ${ROUND_ZIGZAG_MOBILE_VIEWBOX_HEIGHT}`}
            preserveAspectRatio="none"
          >
            <path
              d={ROUND_ZIGZAG_MOBILE_CONNECTOR_PATH}
              fill="none"
              stroke="var(--icon-muted)"
              strokeWidth={4}
              strokeLinecap="round"
            />
          </svg>

          {ROUND_STEPS.map((step, index) => {
            const position = ROUND_STEPS_MOBILE_POSITIONS[index];
            return (
              <React.Fragment key={step.key}>
                <div
                  className="absolute -translate-x-1/2 -translate-y-1/2 size-16 rounded-full border-4 border-border-strong flex items-center justify-center shadow-[3px_3px_0_rgba(76,79,105,0.35)] dark:shadow-[3px_3px_0_rgba(0,0,0,0.4)] bg-[var(--step-bg-light)] dark:bg-[var(--step-bg-dark)]"
                  style={
                    {
                      left: `${(position.iconLeftPx / ROUND_ZIGZAG_MOBILE_VIEWBOX_WIDTH) * 100}%`,
                      top: `${(position.iconTopPx / ROUND_ZIGZAG_MOBILE_VIEWBOX_HEIGHT) * 100}%`,
                      "--step-bg-light": step.lightColor,
                      "--step-bg-dark": step.darkColor,
                    } as React.CSSProperties
                  }
                >
                  <svg
                    width="28"
                    height="28"
                    viewBox="0 0 24 24"
                    className="text-background dark:text-border-strong"
                    fill={step.stroke ? "none" : "currentColor"}
                    stroke={step.stroke ? "currentColor" : "none"}
                    strokeWidth={step.stroke ? 2.2 : 0}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    {step.icon}
                  </svg>
                </div>
                <div
                  className="absolute -translate-y-1/2 w-[110px]"
                  style={{
                    left: `${(position.labelLeftPx / ROUND_ZIGZAG_MOBILE_VIEWBOX_WIDTH) * 100}%`,
                    top: `${(position.iconTopPx / ROUND_ZIGZAG_MOBILE_VIEWBOX_HEIGHT) * 100}%`,
                  }}
                >
                  <div className="font-display text-sm text-marketing-accent">
                    {step.title}
                  </div>
                  <div className="text-muted-foreground text-xs mt-1">
                    {step.body}
                  </div>
                </div>
              </React.Fragment>
            );
          })}
        </div>
      </section>

      {HIGHLIGHTS.map((highlight) => (
        <section
          key={highlight.key}
          className={`relative overflow-hidden bg-dotted px-6 md:px-16 py-16 flex justify-center ${highlight.justifyClassName}`}
        >
          <div
            className="absolute rounded-full pointer-events-none bg-[var(--highlight-primary-light)] dark:bg-[var(--highlight-primary-dark)]"
            style={
              {
                top: highlight.primaryShape.top,
                right: highlight.primaryShape.right,
                left: highlight.primaryShape.left,
                width: highlight.primaryShape.size,
                height: highlight.primaryShape.size,
                "--highlight-primary-light": highlight.primaryShape.lightColor,
                "--highlight-primary-dark": highlight.primaryShape.darkColor,
              } as React.CSSProperties
            }
          />
          <div
            className="absolute opacity-20 dark:opacity-25 pointer-events-none bg-[var(--highlight-secondary-light)] dark:bg-[var(--highlight-secondary-dark)]"
            style={
              {
                bottom: highlight.secondaryShape.bottom,
                left: highlight.secondaryShape.left,
                right: highlight.secondaryShape.right,
                width: highlight.secondaryShape.size,
                height: highlight.secondaryShape.size,
                transform: `rotate(${highlight.secondaryShape.rotate})`,
                "--highlight-secondary-light": highlight.secondaryShape.lightColor,
                "--highlight-secondary-dark": highlight.secondaryShape.darkColor,
              } as React.CSSProperties
            }
          />
          <div
            className={`relative z-10 max-w-md rounded-3xl px-8 py-9 bg-card/80 ${highlight.textAlignClassName}`}
          >
            <h2 className="font-display text-2xl md:text-3xl leading-tight text-marketing-accent">
              {highlight.heading}
            </h2>
            <p className="text-foreground/90 mt-4 leading-relaxed">
              {highlight.body}
            </p>
          </div>
        </section>
      ))}

      <section className="relative overflow-hidden bg-dotted px-6 md:px-16 py-20 flex justify-center">
        <div className="absolute -top-32 left-1/4 size-72 rounded-full bg-accent/20 pointer-events-none" />
        <div className="absolute -bottom-32 right-1/4 size-80 bg-primary/20 rotate-12 pointer-events-none" />
        <div
          className="relative z-10 bg-card border-[3px] border-border-strong rounded-3xl px-12 py-12 flex flex-col items-center text-center gap-4 -rotate-1"
          style={{ boxShadow: "8px 8px 0 var(--shadow-color)" }}
        >
          <h2
            className="font-display text-2xl md:text-3xl text-marketing-accent"
            style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
          >
            Ready?
          </h2>
          <p className="text-muted-foreground text-sm max-w-sm">
            Create your account and start building your custom playlist
            today.
          </p>
          <Button asChild size="lg" className="mt-1">
            <Link href="/register">Create free account</Link>
          </Button>
        </div>
      </section>

      <footer className="px-6 md:px-16 py-12 border-t-2 border-border flex flex-wrap gap-12">
        <div className="flex flex-col gap-3 max-w-[280px]">
          <div className="flex items-center gap-2.5">
            <LogoBars
              barWidthPx={4}
              colorClassName="bg-icon-muted"
              containerClassName="h-[18px]"
            />
            <span className="font-wordmark font-extrabold text-[17px] text-[#6c6f85] dark:text-[#a6adc8]">
              hittiguess
            </span>
          </div>
          <p className="text-muted-foreground text-xs leading-relaxed">
            No ads. No tracking. Free, always. Built for friends, not for
            profit.
          </p>
        </div>

        <div>
          <div className="text-[11px] tracking-widest uppercase text-muted-foreground font-semibold mb-3">
            Product
          </div>
          <div className="flex flex-col gap-2.5 text-sm text-muted-foreground">
            <span>How it works</span>
            <Link href="/playlists" className="hover:text-marketing-accent">
              Playlists
            </Link>
            <Link href="/login" className="hover:text-marketing-accent">
              Sign in
            </Link>
          </div>
        </div>

        <div>
          <div className="text-[11px] tracking-widest uppercase text-muted-foreground font-semibold mb-3">
            Legal
          </div>
          <div className="flex flex-col gap-2.5 text-sm text-muted-foreground">
            <span>Privacy Policy</span>
            <span>Terms of Service</span>
            <span>Data &amp; GDPR</span>
          </div>
        </div>

        <div>
          <div className="text-[11px] tracking-widest uppercase text-muted-foreground font-semibold mb-3">
            Community
          </div>
          <div className="flex flex-col gap-2.5 text-sm text-muted-foreground">
            <span>GitHub</span>
            <span>Report an issue</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
