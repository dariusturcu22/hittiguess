"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { LogoIcon } from "@/components/logo";
import { AuthPageBackground } from "@/components/auth-page-background";

// Login and register share one card shell (logo, tab switcher, then
// whichever page's own form) so the tab switcher survives client-side
// navigation between the two routes and can animate its active pill,
// instead of remounting from scratch as a static class swap on every
// navigation. See docs/design/source/LoginDark.dc.html and
// RegisterDark.dc.html's shared .authcard/.toggle-wrap structure.
export default function CredentialsAuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const isRegisterActive = pathname === "/register";

  return (
    <section className="flex-1 flex items-center justify-center px-4 py-12 relative overflow-hidden bg-dotted">
      <AuthPageBackground
        primaryClassName="bg-[#8f4fe3]/[0.12] dark:bg-[#cba6f7]/[0.16]"
        secondaryClassName="bg-[#3772e6]/10 dark:bg-[#89b4fa]/[0.14]"
      />

      <div className="w-full max-w-[460px] rounded-[18px] border-[3px] border-border-strong bg-card px-12 pt-11 pb-12 flex flex-col items-center relative z-10 shadow-lg">
        <div className="mb-[30px]">
          <LogoIcon size="lg" />
        </div>

        <div className="relative flex w-full rounded-full border-2 border-border bg-background p-[5px] mb-[30px]">
          <div
            aria-hidden
            className={`absolute inset-y-[5px] left-[5px] w-[calc(50%-5px)] rounded-full bg-primary shadow-[3px_3px_0_rgba(76,79,105,0.3)] dark:shadow-[3px_3px_0_rgba(0,0,0,0.3)] transition-transform duration-300 ease-out ${
              isRegisterActive ? "translate-x-full" : "translate-x-0"
            }`}
          />
          <Link
            href="/login"
            className={`relative z-10 flex-1 py-3 text-center font-display text-[13px] transition-colors duration-300 ${
              isRegisterActive ? "text-muted-foreground" : "text-primary-foreground"
            }`}
          >
            Log in
          </Link>
          <Link
            href="/register"
            className={`relative z-10 flex-1 py-3 text-center font-display text-[13px] transition-colors duration-300 ${
              isRegisterActive ? "text-primary-foreground" : "text-muted-foreground"
            }`}
          >
            Create account
          </Link>
        </div>

        {children}
      </div>
    </section>
  );
}
