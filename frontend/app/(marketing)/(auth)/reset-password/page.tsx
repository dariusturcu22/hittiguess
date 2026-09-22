"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";

import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import { IconArrowLeft, IconLock } from "@tabler/icons-react";

import { LogoIcon } from "@/components/logo";
import { AuthPageBackground } from "@/components/auth-page-background";
import { ThemeToggle } from "@/components/theme-toggle";
import { useConfirmPasswordReset } from "@/hooks/generated/authentication-management/authentication-management";

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={null}>
      <ResetPasswordForm />
    </Suspense>
  );
}

function ResetPasswordForm() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const [newPassword, setNewPassword] = useState("");
  const [confirmedPassword, setConfirmedPassword] = useState("");
  const [done, setDone] = useState(false);
  const [formError, setFormError] = useState("");
  const confirmReset = useConfirmPasswordReset();

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (newPassword !== confirmedPassword) {
      setFormError("The passwords do not match.");
      return;
    }
    setFormError("");
    confirmReset.mutate(
      { data: { token, newPassword } },
      {
        onSuccess: () => setDone(true),
        onError: () => setFormError("This link is invalid or expired. Request a new one."),
      },
    );
  }

  return (
    <section className="auth-surface flex-1 flex items-center justify-center px-4 py-12 relative overflow-hidden bg-dotted">
      <AuthPageBackground
        primaryClassName="bg-[#d49032]/[0.12] dark:bg-[#f9e2af]/[0.14]"
        secondaryClassName="bg-[#8f4fe3]/10 dark:bg-[#cba6f7]/[0.14]"
      />
      <ThemeToggle className="auth-theme-toggle top-6 right-6 z-20" />

      <div className="w-full max-w-[460px] rounded-[18px] border-[3px] border-border-strong bg-card px-6 sm:px-12 pt-11 pb-12 flex flex-col items-center relative z-10 shadow-lg">
        <Link href="/" aria-label="hittiguess home" className="mb-[30px]">
          <LogoIcon size="lg" />
        </Link>

        <div className="size-[42px] rounded-full bg-primary/[0.12] dark:bg-primary/[0.16] flex items-center justify-center mb-5">
          <IconLock className="size-5 text-primary" />
        </div>

        <div className="text-center mb-7 max-w-[340px]">
          <h1
            className="font-display text-2xl text-marketing-accent"
            style={{ textShadow: "3px 3px 0 var(--text-shadow-on-card)" }}
          >
            Set a new password
          </h1>
        </div>

        {done ? (
          <p className="text-sm text-muted-foreground text-center">
            Password updated. <Link href="/login" className="text-primary underline underline-offset-[3px]">Log in</Link> with the new one.
          </p>
        ) : (
          <form className="w-full" onSubmit={handleSubmit}>
            <div className="space-y-7">
              <div className="space-y-[7px]">
                <Label htmlFor="new-password" className="text-[13px] font-semibold text-muted-foreground">
                  New password
                </Label>
                <Input
                  type="password"
                  required
                  minLength={6}
                  autoComplete="new-password"
                  id="new-password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                  placeholder="••••••••••"
                  className="h-auto px-4 py-[13px] text-sm placeholder:text-icon-muted"
                />
              </div>
              <div className="space-y-[7px]">
                <Label htmlFor="confirm-password" className="text-[13px] font-semibold text-muted-foreground">
                  Confirm password
                </Label>
                <Input
                  type="password"
                  required
                  minLength={6}
                  autoComplete="new-password"
                  id="confirm-password"
                  value={confirmedPassword}
                  onChange={(event) => setConfirmedPassword(event.target.value)}
                  placeholder="••••••••••"
                  className="h-auto px-4 py-[13px] text-sm placeholder:text-icon-muted"
                />
              </div>

              <Button type="submit" className="auth-submit w-full" disabled={!token || confirmReset.isPending}>
                {confirmReset.isPending ? "Saving..." : "Save new password"}
              </Button>
              {formError ? (
                <p role="alert" className="text-sm text-destructive text-center">{formError}</p>
              ) : null}
            </div>
          </form>
        )}

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
