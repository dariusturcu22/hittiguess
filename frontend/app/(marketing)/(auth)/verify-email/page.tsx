"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";

import { LogoIcon } from "@/components/logo";
import { AuthPageBackground } from "@/components/auth-page-background";
import { ThemeToggle } from "@/components/theme-toggle";
import { useVerifyEmail } from "@/hooks/use-password-reset";

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={null}>
      <VerifyEmailContent />
    </Suspense>
  );
}

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const [verified, setVerified] = useState(false);
  const [failed, setFailed] = useState(false);
  const verifyEmail = useVerifyEmail();

  useEffect(() => {
    if (!token || verified || failed || verifyEmail.isPending) {
      return;
    }
    verifyEmail.mutate(
      { data: { token } },
      {
        onSuccess: () => setVerified(true),
        onError: () => setFailed(true),
      },
    );
  }, [token, verified, failed, verifyEmail]);

  return (
    <section className="auth-surface flex-1 flex items-center justify-center px-4 py-12 relative overflow-hidden bg-dotted">
      <AuthPageBackground
        primaryClassName="bg-[#8f4fe3]/[0.12] dark:bg-[#cba6f7]/[0.16]"
        secondaryClassName="bg-[#3772e6]/10 dark:bg-[#89b4fa]/[0.14]"
      />
      <ThemeToggle className="auth-theme-toggle top-6 right-6 z-20" />

      <div className="w-full max-w-[460px] rounded-[18px] border-[3px] border-border-strong bg-card px-6 sm:px-12 pt-11 pb-12 flex flex-col items-center relative z-10 shadow-lg">
        <Link href="/" aria-label="hittiguess home" className="mb-[30px]">
          <LogoIcon size="lg" />
        </Link>

        <div className="text-center max-w-[340px]">
          <h1
            className="font-display text-2xl text-marketing-accent"
            style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
          >
            {verified ? "Email verified" : failed ? "Link expired" : "Verifying..."}
          </h1>
          <p className="text-muted-foreground text-sm mt-2.5 leading-relaxed">
            {verified ? (
              <>Your email is confirmed. <Link href="/login" className="text-primary underline underline-offset-[3px]">Log in</Link> to start playing.</>
            ) : failed || !token ? (
              <>This link is invalid or expired. Request a new one from the login screen.</>
            ) : (
              <>Confirming your email address.</>
            )}
          </p>
        </div>
      </div>
    </section>
  );
}
