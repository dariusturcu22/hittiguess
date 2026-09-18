"use client";

import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";
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

  return (
    <div className={`theme-toggle ${className}`} aria-label="Color theme" role="group">
      <span
        aria-hidden="true"
        className={`theme-toggle-thumb ${isMounted && isDarkTheme ? "translate-x-full" : "translate-x-0"}`}
      />
      <Button
        aria-label="Light theme"
        aria-pressed={isMounted && !isDarkTheme}
        className="theme-toggle-option"
        onClick={() => changeTheme("light")}
        size="icon"
        variant="ghost"
      >
        <Sun />
      </Button>
      <Button
        aria-label="Dark theme"
        aria-pressed={isMounted && isDarkTheme}
        className="theme-toggle-option"
        onClick={() => changeTheme("dark")}
        size="icon"
        variant="ghost"
      >
        <Moon />
      </Button>
    </div>
  );
}
