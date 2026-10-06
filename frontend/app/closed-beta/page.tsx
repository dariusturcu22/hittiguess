import Link from "next/link";

import { LogoBars } from "@/components/logo";
import { Button } from "@/components/shadcn/button";
import { ThemeToggle } from "@/components/theme-toggle";
import { BETA_ACCESS_API_PATH, LEGACY_APP_URL } from "@/lib/beta-access";
import "./closed-beta.css";

export default async function ClosedBetaPage({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;

  return (
    <main className="closed-beta-page">
      <header className="closed-beta-header">
        <Link href="/" aria-label="hittiguess home" className="closed-beta-brand">
          <LogoBars />
          <span>hittiguess</span>
        </Link>
        <ThemeToggle />
      </header>
      <section className="closed-beta-card">
        <h1>CLOSED BETA</h1>
        <p>
          We&apos;re currently in closed beta. The new version isn&apos;t open yet. In the meantime, you can check
          out the current version here:
        </p>
        <Button asChild className="closed-beta-action">
          <a href={LEGACY_APP_URL}>Open the current version</a>
        </Button>
        <Link href="/" className="closed-beta-back">Back to home</Link>
        <form method="post" action={BETA_ACCESS_API_PATH} className="closed-beta-access">
          <label htmlFor="beta-password">Beta tester? Enter your access code</label>
          <div>
            <input id="beta-password" name="password" type="password" autoComplete="off" required />
            <Button type="submit" variant="outline">Enter</Button>
          </div>
          {error ? <p role="alert">That code isn&apos;t right.</p> : null}
        </form>
      </section>
    </main>
  );
}
