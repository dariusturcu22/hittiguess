"use client";

import { useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { LogoIcon } from "@/components/logo";
import { AuthPageBackground } from "@/components/auth-page-background";

export default function OAuth2RedirectHandler() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    const error = searchParams.get("error");

    if (error) {
      router.push("/login?error=" + encodeURIComponent(error));
    } else {
      router.push("/playlists");
    }
  }, [router, searchParams]);

  return (
    <section className="flex-1 flex items-center justify-center px-4 py-12 relative overflow-hidden bg-dotted">
      <AuthPageBackground
        primaryClassName="bg-[#e387cb]/[0.12] dark:bg-[#f5c2e7]/[0.14]"
        secondaryClassName="bg-[#8f4fe3]/10 dark:bg-[#cba6f7]/[0.14]"
      />

      <div className="relative z-10 flex flex-col items-center">
        <div className="mb-11">
          <LogoIcon size="lg" />
        </div>

        <svg
          className="animate-spin text-[#e387cb] dark:text-[#f5c2e7]"
          width="40"
          height="40"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        >
          <circle cx="12" cy="12" r="9" strokeDasharray="34 20" />
        </svg>

        <p
          className="font-display text-lg text-[#e387cb] dark:text-[#f5c2e7] mt-[22px]"
          style={{ textShadow: "2px 2px 0 var(--text-shadow-on-page)" }}
        >
          Signing you in&hellip;
        </p>
        <p className="text-muted-foreground text-sm mt-2.5">
          Finishing up with your account provider. This only takes a second.
        </p>
      </div>
    </section>
  );
}
