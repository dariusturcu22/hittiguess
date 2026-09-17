"use client";

import * as React from "react";
import Link from "next/link";
import { useTheme } from "next-themes";
import { Moon, Sun } from "lucide-react";
import { LogoIcon } from "@/components/logo";

function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = React.useState(false);

  React.useEffect(() => {
    setMounted(true);
  }, []);

  const isDark = mounted && resolvedTheme === "dark";

  const activeClasses = "bg-[#eff1f5] dark:bg-[#45475a] text-[#d49032] dark:text-[#f9e2af]";
  const inactiveClasses = "text-icon-muted";

  return (
    <div className="flex items-center gap-1 rounded-full bg-[#ccd0da] dark:bg-[#313244] p-1">
      <button
        type="button"
        onClick={() => setTheme("light")}
        aria-label="Light theme"
        aria-pressed={!isDark}
        className={`flex size-7 items-center justify-center rounded-full transition-colors ${
          !isDark ? activeClasses : inactiveClasses
        }`}
      >
        <Sun className="size-4" />
      </button>
      <button
        type="button"
        onClick={() => setTheme("dark")}
        aria-label="Dark theme"
        aria-pressed={isDark}
        className={`flex size-7 items-center justify-center rounded-full transition-colors ${
          isDark ? activeClasses : inactiveClasses
        }`}
      >
        <Moon className="size-4" />
      </button>
    </div>
  );
}

// The landing page is the only pre-login screen with a header: login,
// register, forgot-password, and the OAuth2 redirect screen each put their
// own logo inside their own card instead, per their own mockups, which have
// no header or nav element at all. See frontend/app/(marketing)/(auth)/layout.tsx.
export default function LandingLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <header className="flex items-center justify-between px-6 md:px-16 py-6 relative z-10">
        <Link href="/" aria-label="go home">
          <LogoIcon />
        </Link>

        <ThemeToggle />
      </header>

      <main className="flex-1 flex flex-col">{children}</main>
    </div>
  );
}
