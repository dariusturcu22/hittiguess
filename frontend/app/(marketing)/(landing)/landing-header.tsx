"use client";

import Link from "next/link";
import { useTheme } from "next-themes";
import { Moon, Sun } from "lucide-react";
import { Button } from "@/components/shadcn/button";
import { LogoBars } from "@/components/logo";

export function LandingHeader() {
  const { setTheme } = useTheme();
  return <header className="landing-header">
    <Link href="/" aria-label="hittiguess home" className="landing-brand"><LogoBars /><span>hittiguess</span></Link>
    <div className="landing-theme" aria-label="Color theme">
      <Button variant="ghost" size="icon" onClick={() => setTheme("light")} aria-label="Light theme" className="theme-light"><Sun /></Button>
      <Button variant="ghost" size="icon" onClick={() => setTheme("dark")} aria-label="Dark theme" className="theme-dark"><Moon /></Button>
    </div>
  </header>;
}
