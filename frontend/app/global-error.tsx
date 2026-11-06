"use client";

import { useEffect } from "react";
import * as Sentry from "@sentry/nextjs";
import "./globals.css";

interface GlobalErrorProps {
  error: Error & { digest?: string };
  reset: () => void;
}

export default function GlobalError({ error, reset }: GlobalErrorProps) {
  useEffect(() => {
    Sentry.captureException(error);
  }, [error]);

  return (
    <html lang="en">
      <body className="min-h-screen bg-background text-foreground flex flex-col items-center justify-center p-6 antialiased">
        <main className="max-w-md w-full p-8 sm:p-10 rounded-3xl border border-border bg-card shadow-sm text-center space-y-6">
          <div className="space-y-2">
            <span className="font-display text-4xl sm:text-5xl font-extrabold text-destructive block">
              Application Error
            </span>
            <h1 className="font-display text-2xl font-bold text-foreground">
              Something went wrong
            </h1>
          </div>
          <p className="text-muted-foreground text-sm leading-relaxed">
            A critical system error occurred. Our monitoring system has been notified automatically.
          </p>
          {error?.digest && (
            <p className="text-xs font-mono text-muted-foreground bg-muted py-1.5 px-3 rounded-lg inline-block">
              Error reference: {error.digest}
            </p>
          )}
          <div className="pt-2 flex flex-col sm:flex-row items-center justify-center gap-3">
            <button
              type="button"
              onClick={reset}
              className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-2.5 rounded-xl text-sm font-semibold bg-primary text-primary-foreground hover:opacity-90 transition-opacity cursor-pointer"
            >
              Try Again
            </button>
            {/* global-error replaces the root layout, so the app router is unavailable: a plain anchor does a full navigation. */}
            {/* eslint-disable-next-line @next/next/no-html-link-for-pages */}
            <a
              href="/"
              className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-2.5 rounded-xl text-sm font-semibold border border-border bg-background text-foreground hover:bg-muted transition-colors"
            >
              Home
            </a>
            <a
              href="/login"
              className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-2.5 rounded-xl text-sm font-semibold border border-border bg-background text-foreground hover:bg-muted transition-colors"
            >
              Sign In
            </a>
          </div>
        </main>
      </body>
    </html>
  );
}
