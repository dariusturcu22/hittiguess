"use client";

import { useEffect, useState } from "react";
import { useTheme } from "next-themes";

import { Button } from "@/components/shadcn/button";

const THEME_TRANSITION_DURATION_MS = 260;

type ThemeToggleProps = {
  className?: string;
};

export function ThemeToggle({ className = "" }: ThemeToggleProps) {
  const { resolvedTheme, setTheme } = useTheme();
  const [isMounted, setIsMounted] = useState(false);
  const isDarkTheme = resolvedTheme === "dark";

  useEffect(() => {
    setIsMounted(true);
  }, []);

  function changeTheme(theme: "dark" | "light") {
    if (theme === resolvedTheme) {
      return;
    }

    document.documentElement.classList.add("theme-transition");
    setTheme(theme);
    window.setTimeout(() => {
      document.documentElement.classList.remove("theme-transition");
    }, THEME_TRANSITION_DURATION_MS);
  }

  function toggleTheme() {
    changeTheme(isDarkTheme ? "light" : "dark");
  }

  return (
    <Button
      aria-checked={isMounted && isDarkTheme}
      aria-label={isDarkTheme ? "Switch to light theme" : "Switch to dark theme"}
      className={`theme-toggle ${isMounted && isDarkTheme ? "is-dark" : ""} ${className}`}
      onClick={toggleTheme}
      role="switch"
      type="button"
      variant="ghost"
    >
      <span
        aria-hidden="true"
        className="theme-toggle-thumb"
      />
    </Button>
  );
}
