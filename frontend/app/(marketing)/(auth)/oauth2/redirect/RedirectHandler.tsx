"use client";

import { useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { IconLoader2 } from "@tabler/icons-react";

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
    <div className="flex-1 flex flex-col items-center justify-center px-4 py-12 relative overflow-hidden bg-dotted">
      <div className="absolute -top-44 -right-40 size-[560px] rounded-full bg-accent/15 pointer-events-none" />
      <div className="absolute -bottom-56 -left-40 size-[480px] bg-accent/10 rotate-12 pointer-events-none" />

      <div className="relative z-10 flex flex-col items-center">
        <IconLoader2 className="size-10 text-marketing-accent animate-spin" />
        <p
          className="font-display text-lg text-marketing-accent mt-5"
          style={{ textShadow: "2px 2px 0 var(--text-shadow-on-page)" }}
        >
          Signing you in&hellip;
        </p>
        <p className="text-muted-foreground text-sm mt-2.5">
          Finishing up with your account provider. This only takes a second.
        </p>
      </div>
    </div>
  );
}
