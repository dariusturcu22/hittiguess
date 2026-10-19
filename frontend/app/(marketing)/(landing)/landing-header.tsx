"use client";

import Link from "next/link";
import { LogoBars } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";

export function LandingHeader() {
  return <header className="landing-header">
    <Link href="/" aria-label="hittiguess home" className="landing-brand"><LogoBars /><span>hittiguess</span></Link>
    <ThemeToggle />
  </header>;
}
