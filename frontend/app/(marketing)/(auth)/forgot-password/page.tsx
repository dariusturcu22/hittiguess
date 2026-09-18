"use client";

import { useState } from "react";
import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import { IconArrowLeft, IconLock } from "@tabler/icons-react";
import Link from "next/link";

import { LogoIcon } from "@/components/logo";
import { AuthPageBackground } from "@/components/auth-page-background";

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
  };

  return (
    <section className="auth-surface flex-1 flex items-center justify-center px-4 py-12 relative overflow-hidden bg-dotted">
      <AuthPageBackground
        primaryClassName="bg-[#d49032]/[0.12] dark:bg-[#f9e2af]/[0.14]"
        secondaryClassName="bg-[#8f4fe3]/10 dark:bg-[#cba6f7]/[0.14]"
      />

      <div className="w-full max-w-[460px] rounded-[18px] border-[3px] border-border-strong bg-card px-6 sm:px-12 pt-11 pb-12 flex flex-col items-center relative z-10 shadow-lg">
        <div className="mb-[30px]">
          <LogoIcon size="lg" />
        </div>

        <div className="size-[42px] rounded-full bg-primary/[0.12] dark:bg-primary/[0.16] flex items-center justify-center mb-5">
          <IconLock className="size-5 text-primary" />
        </div>

        <div className="text-center mb-7 max-w-[340px]">
          <h1
            className="font-display text-2xl text-marketing-accent"
            style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
          >
            Forgot password?
          </h1>
          <p className="text-muted-foreground text-sm mt-2.5 leading-relaxed">
            No worries. Enter the email on your account and we&apos;ll send
            you a link to reset it.
          </p>
        </div>

        <form className="w-full" onSubmit={handleSubmit}>
          <div className="space-y-7">
            <div className="space-y-[7px]">
              <Label
                htmlFor="email"
                className="text-[13px] font-semibold text-muted-foreground"
              >
                Email
              </Label>
              <Input
                type="email"
                required
                name="email"
                id="email"
                placeholder="you@example.com"
                className="h-auto px-4 py-[13px] text-sm placeholder:text-icon-muted"
              />
            </div>

            {submitted ? (
              <p className="text-sm text-muted-foreground text-center">
                Password reset isn&apos;t available yet. Contact the person
                who set up your account for help signing in.
              </p>
            ) : (
              <Button type="submit" className="auth-submit w-full">
                Send reset link
              </Button>
            )}
          </div>
        </form>

        <Link
          href="/login"
          className="mt-[22px] flex items-center gap-2 text-[13px] text-muted-foreground"
        >
          <IconArrowLeft className="size-3.5" />
          <span className="text-primary underline underline-offset-[3px]">
            Back to log in
          </span>
        </Link>
      </div>
    </section>
  );
}
