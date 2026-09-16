"use client";

import { useState } from "react";
import { Button } from "@/components/shadcn/button";
import { Input } from "@/components/shadcn/input";
import { Label } from "@/components/shadcn/label";
import { Card } from "@/components/shadcn/card";
import { IconArrowLeft, IconLock } from "@tabler/icons-react";
import Link from "next/link";

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
  };

  return (
    <section className="flex-1 flex items-center justify-center px-4 py-12 relative overflow-hidden bg-dotted">
      <div className="absolute -top-44 -right-40 size-[560px] rounded-full bg-warning/15 pointer-events-none" />
      <div className="absolute -bottom-56 -left-40 size-[480px] bg-accent/15 rotate-12 pointer-events-none" />

      <Card className="w-full max-w-[460px] rounded-lg px-8 sm:px-12 pt-11 pb-12 items-center relative z-10">
        <div className="size-11 rounded-full bg-primary/15 flex items-center justify-center mb-1">
          <IconLock className="size-5 text-primary" />
        </div>

        <div className="text-center mt-4 max-w-[340px]">
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

        <form className="w-full mt-6" onSubmit={handleSubmit}>
          <div className="space-y-5">
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                type="email"
                required
                name="email"
                id="email"
                placeholder="you@example.com"
              />
            </div>

            {submitted ? (
              <p className="text-sm text-muted-foreground text-center">
                Password reset isn&apos;t available yet. Contact the person
                who set up your account for help signing in.
              </p>
            ) : (
              <Button type="submit" className="w-full">
                Send reset link
              </Button>
            )}
          </div>
        </form>

        <Button asChild variant="link" className="mt-5 gap-1.5 font-sans">
          <Link href="/login">
            <IconArrowLeft className="size-3.5" />
            Back to log in
          </Link>
        </Button>
      </Card>
    </section>
  );
}
