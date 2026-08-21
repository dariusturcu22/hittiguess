"use client";

import React from "react";
import Link from "next/link";

const cards = [
  {
    gradient: ["#cba6f7", "#89b4fa"],
    year: "1994",
    title: "Creep",
    artist: "Radiohead",
  },
  {
    gradient: ["#fab387", "#f38ba8"],
    year: "2006",
    title: "Chasing Cars",
    artist: "Snow Patrol",
  },
  {
    gradient: ["#a6e3a1", "#74c7ec"],
    year: "1982",
    title: "Africa",
    artist: "Toto",
  },
  {
    gradient: ["#f9e2af", "#fab387"],
    year: "2015",
    title: "Uptown Funk",
    artist: "Bruno Mars",
  },
  {
    gradient: ["#f5c2e7", "#cba6f7"],
    year: "1999",
    title: "...Baby One More Time",
    artist: "Britney Spears",
  },
];

function MusicCard({
  gradient,
  year,
  title,
  artist,
  style,
}: {
  gradient: string[];
  year: string;
  title: string;
  artist: string;
  style?: React.CSSProperties;
}) {
  return (
    <div
      className="absolute rounded-2xl shadow-2xl flex flex-col items-center justify-between p-4 text-white overflow-hidden select-none"
      style={{
        width: 160,
        height: 160,
        background: `linear-gradient(135deg, ${gradient[0]}, ${gradient[1]})`,
        fontFamily: "var(--font-kanit), 'Kanit', sans-serif",
        ...style,
      }}
    >
      <div className="text-center font-normal leading-tight text-xs opacity-90">
        {artist}
      </div>
      <div className="font-semibold tracking-tighter text-5xl">{year}</div>
      <div className="text-center italic font-light leading-tight text-xs opacity-90">
        {title}
      </div>
    </div>
  );
}

const features = [
  {
    icon: "🎵",
    title: "Paste a YouTube link",
    desc: "Drop any YouTube music video URL into the app.",
  },
  {
    icon: "🤖",
    title: "AI extracts the metadata",
    desc: "MusicBrainz, Genius, and LLM synthesis identify artist, title, and release year.",
  },
  {
    icon: "🎴",
    title: "Get a beautiful card",
    desc: "A gradient card is generated and added to your playlist, ready to print.",
  },
  {
    icon: "🖨️",
    title: "Print & play",
    desc: "Export as PDF, print double-sided, and play Hitster with your custom songs.",
  },
];

export default function LandingPage() {
  const cardPositions = [
    { top: "5%", left: "3%", rotate: "-14deg", animDuration: "4.2s" },
    { top: "60%", left: "1%", rotate: "7deg", animDuration: "5.1s" },
    { top: "10%", right: "3%", rotate: "11deg", animDuration: "4.7s" },
    { top: "62%", right: "5%", rotate: "-9deg", animDuration: "3.9s" },
  ];

  return (
    <>
      <style>{`
        @keyframes floatCard {
          0% { transform: translateY(0px) rotate(var(--card-rotate)); }
          100% { transform: translateY(-16px) rotate(var(--card-rotate)); }
        }
        @keyframes fadeUp {
          from { opacity: 0; transform: translateY(24px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .float-card {
          animation: floatCard var(--card-dur, 4s) ease-in-out infinite alternate;
        }
        .fade-up {
          opacity: 0;
          animation: fadeUp 0.7s ease forwards;
        }
        .fade-up-1 { animation-delay: 0.1s; }
        .fade-up-2 { animation-delay: 0.25s; }
        .fade-up-3 { animation-delay: 0.4s; }
        .fade-up-4 { animation-delay: 0.55s; }
      `}</style>

      <div className="min-h-screen bg-background text-foreground flex flex-col">
        <section className="flex-1 flex flex-col items-center justify-center text-center px-6 py-20 relative overflow-hidden min-h-[80vh]">
          <div className="absolute inset-0 pointer-events-none hidden lg:block">
            {cards.slice(0, 4).map((card, i) => (
              <MusicCard
                key={i}
                {...card}
                style={
                  {
                    ...cardPositions[i],
                    "--card-rotate": cardPositions[i].rotate,
                    "--card-dur": cardPositions[i].animDuration,
                    transform: `rotate(${cardPositions[i].rotate})`,
                  } as unknown as React.CSSProperties
                }
              />
            ))}
          </div>

          <div
            className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full blur-3xl pointer-events-none"
            style={{
              width: 600,
              height: 400,
              background:
                "radial-gradient(ellipse, color-mix(in oklch, var(--color-primary) 12%, transparent), transparent 70%)",
            }}
          />

          <div className="relative z-10 max-w-2xl mx-auto flex flex-col items-center gap-6">
            <h1
              className="fade-up fade-up-1 text-5xl md:text-7xl font-bold tracking-tight leading-none"
              style={{ fontFamily: "var(--font-kanit), 'Kanit', sans-serif" }}
            >
              Turn songs into
              <br />
              <span style={{ color: "var(--color-primary)" }}>game cards.</span>
            </h1>

            <p className="fade-up fade-up-2 text-muted-foreground text-lg max-w-md leading-relaxed">
              Paste a YouTube link. AI identifies the song. Get a beautiful
              printable card for your Hitster game nights.
            </p>

            <div className="fade-up fade-up-3 flex flex-col sm:flex-row gap-3 mt-2">
              <Link
                href="/register"
                className="px-6 py-3 rounded-md font-semibold text-sm transition-all hover:opacity-90 active:scale-95"
                style={{
                  background: "var(--color-primary)",
                  color: "var(--color-primary-foreground)",
                }}
              >
                Start building your deck →
              </Link>
              <Link
                href="/login"
                className="px-6 py-3 rounded-md font-medium text-sm border border-border hover:bg-muted transition-colors"
              >
                Sign in
              </Link>
            </div>
          </div>
        </section>

        <section className="px-6 py-20 border-t border-border">
          <div className="max-w-4xl mx-auto">
            <h2
              className="text-3xl font-bold text-center mb-12"
              style={{ fontFamily: "var(--font-kanit), 'Kanit', sans-serif" }}
            >
              How it works
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
              {features.map((f, i) => (
                <div
                  key={i}
                  className="rounded-xl p-6 border border-border bg-card flex flex-col gap-3 hover:border-primary/50 transition-colors"
                >
                  <div className="text-3xl">{f.icon}</div>
                  <div className="font-semibold text-sm">{f.title}</div>
                  <div className="text-muted-foreground text-sm leading-relaxed">
                    {f.desc}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="px-6 py-20 border-t border-border">
          <div className="max-w-4xl mx-auto text-center">
            <h2
              className="text-3xl font-bold mb-4"
              style={{ fontFamily: "var(--font-kanit), 'Kanit', sans-serif" }}
            >
              Beautiful cards, every time
            </h2>
            <p className="text-muted-foreground mb-12 text-sm">
              Each card gets a unique AI-generated gradient based on the
              song&apos;s mood.
            </p>
            <div className="flex flex-wrap justify-center gap-6">
              {cards.map((card, i) => (
                <div
                  key={i}
                  className="rounded-2xl shadow-xl flex flex-col items-center justify-between p-4 text-white transition-transform hover:-translate-y-1"
                  style={{
                    width: 140,
                    height: 140,
                    background: `linear-gradient(135deg, ${card.gradient[0]}, ${card.gradient[1]})`,
                    fontFamily: "var(--font-kanit), 'Kanit', sans-serif",
                    transform: `rotate(${[-3, 2, -1, 3, -2][i]}deg)`,
                  }}
                >
                  <div className="text-center font-normal leading-tight text-xs opacity-90">
                    {card.artist}
                  </div>
                  <div className="font-semibold tracking-tighter text-4xl">
                    {card.year}
                  </div>
                  <div className="text-center italic font-light leading-tight text-xs opacity-90">
                    {card.title}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="px-6 py-20 border-t border-border">
          <div className="max-w-2xl mx-auto rounded-2xl p-12 text-center border border-border bg-card relative overflow-hidden">
            <div
              className="absolute inset-0 pointer-events-none rounded-2xl"
              style={{
                background:
                  "radial-gradient(ellipse at 50% 0%, color-mix(in oklch, var(--color-primary) 10%, transparent), transparent 70%)",
              }}
            />
            <h2
              className="relative text-3xl font-bold mb-3"
              style={{ fontFamily: "var(--font-kanit), 'Kanit', sans-serif" }}
            >
              Ready to play?
            </h2>
            <p className="relative text-muted-foreground mb-6 text-sm">
              Create your account and start building your custom Hitster deck
              today.
            </p>
            <Link
              href="/register"
              className="relative inline-flex px-8 py-3 rounded-md font-semibold text-sm transition-all hover:opacity-90 active:scale-95"
              style={{
                background: "var(--color-primary)",
                color: "var(--color-primary-foreground)",
              }}
            >
              Create free account
            </Link>
          </div>
        </section>
      </div>
    </>
  );
}
