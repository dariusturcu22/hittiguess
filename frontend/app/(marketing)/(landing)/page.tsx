"use client";

import React from "react";
import Link from "next/link";
import { Button } from "@/components/shadcn/button";

const timelineCards = [
  { color: "#a6e3a1", artist: "RADIOHEAD", year: "1994", title: "Creep" },
  { color: "#f9e2af", artist: "SNOW PATROL", year: "2006", title: "Chasing Cars" },
];

const steps = [
  {
    color: "#cba6f7",
    light: "#8f4fe3",
    title: "DJ hits play",
    body: "Real YouTube, nothing hidden.",
    icon: (
      <path d="M8 5v14l11-7z" />
    ),
  },
  {
    color: "#fab387",
    light: "#ed6f27",
    title: "You place it",
    body: "On your own timeline.",
    icon: (
      <path d="M12 21c-4.2-4.6-7-8.4-7-12a7 7 0 0 1 14 0c0 3.6-2.8 7.4-7 12z" />
    ),
    circle: true,
  },
  {
    color: "#a6e3a1",
    light: "#499f36",
    title: "Others bet",
    body: "Wrong guess, stolen card.",
    icon: (
      <>
        <circle cx="12" cy="12" r="9" />
        <circle cx="12" cy="12" r="4" />
      </>
    ),
    stroke: true,
  },
  {
    color: "#f9e2af",
    light: "#df8e1d",
    title: "Reveal",
    body: "Artist, title, year, truth.",
    icon: (
      <>
        <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z" />
        <circle cx="12" cy="12" r="3" />
      </>
    ),
    stroke: true,
  },
];

const highlights = [
  {
    heading: "YOUR MUSIC. YOUR RULES.",
    body: "Build your own playlist. Import one. Play someone else's, or let us generate one for you.",
    blob: "bg-accent",
  },
  {
    heading: "GUESS IT. BET IT.",
    body: "Name the artist and title for a token. Save it to bet someone else got their placement wrong, and steal the card if you're right.",
    blob: "bg-warning",
  },
  {
    heading: "PLAY TOGETHER. TRUST BUILT IN.",
    body: "Voice and text chat built in, no separate app needed. Official APIs only, GDPR-minded, no ads, no tracking, ever.",
    blob: "bg-primary",
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
        <div className="absolute -top-40 -right-40 size-[420px] md:size-[560px] rounded-full bg-accent pointer-events-none" />

        <div className="relative z-10 grid md:grid-cols-2 gap-10 items-center max-w-6xl mx-auto">
          <div className="flex flex-col gap-6">
            <h1
              className="font-display text-4xl sm:text-5xl md:text-6xl leading-[1.05] text-accent"
              style={{ textShadow: "4px 4px 0 var(--card)" }}
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
                <Link href="/login" className="text-accent underline underline-offset-4">
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
          className="font-display text-2xl md:text-3xl text-accent text-center"
          style={{ textShadow: "3px 3px 0 var(--card)" }}
        >
          How a round works
        </h2>
        <div className="max-w-5xl mx-auto mt-12 grid grid-cols-2 md:grid-cols-4 gap-8">
          {steps.map((step) => (
            <div key={step.title} className="flex flex-col items-center gap-4 text-center">
              <div
                className="size-16 md:size-20 rounded-full border-[3px] border-border-strong flex items-center justify-center"
                style={{ background: step.color, boxShadow: "4px 4px 0 var(--shadow-color)" }}
              >
                <svg
                  width="28"
                  height="28"
                  viewBox="0 0 24 24"
                  fill={step.stroke ? "none" : "var(--border-strong)"}
                  stroke={step.stroke ? "var(--border-strong)" : "none"}
                  strokeWidth={step.stroke ? 2.2 : 0}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  {step.icon}
                </svg>
              </div>
              <div>
                <div className="font-display text-sm text-accent">
                  {step.title}
                </div>
                <div className="text-muted-foreground text-sm mt-1.5">
                  {step.body}
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {highlights.map((highlight, index) => (
        <section
          key={highlight.heading}
          className={`relative overflow-hidden bg-dotted px-6 md:px-16 py-16 flex ${
            index % 2 === 0 ? "md:justify-end" : "md:justify-start"
          } justify-center`}
        >
          <div
            className={`absolute -top-32 size-72 md:size-96 rounded-full opacity-20 pointer-events-none ${highlight.blob} ${
              index % 2 === 0 ? "-left-24" : "-right-24"
            }`}
          />
          <div
            className={`relative z-10 max-w-md rounded-3xl px-8 py-9 bg-card/80 ${
              index % 2 === 0 ? "text-left" : "md:text-right text-left"
            }`}
          >
            <h2 className="font-display text-2xl md:text-3xl leading-tight text-accent">
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
            className="font-display text-2xl md:text-3xl text-accent"
            style={{ textShadow: "3px 3px 0 var(--background)" }}
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
            <div className="flex items-end gap-[2px] h-[18px]">
              <span className="w-1 h-2.5 rounded-full bg-muted-foreground" />
              <span className="w-1 h-4 rounded-full bg-muted-foreground" />
              <span className="w-1 h-3 rounded-full bg-muted-foreground" />
              <span className="w-1 h-[18px] rounded-full bg-muted-foreground" />
            </div>
            <span className="font-wordmark font-extrabold text-sm text-muted-foreground">
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
            <Link href="/playlists" className="hover:text-accent">
              Playlists
            </Link>
            <Link href="/login" className="hover:text-accent">
              Sign in
            </Link>
          </div>
        </div>

      </footer>
    </div>
  );
}
