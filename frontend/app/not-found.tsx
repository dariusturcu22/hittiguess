import Link from "next/link";
import { LogoBars } from "@/components/logo";
import { ThemeToggle } from "@/components/theme-toggle";
import { PublicFooter } from "@/components/public-footer";

const LOGO_BAR_WIDTH_PIXELS = 4;

export const metadata = {
  title: "404 - Page Not Found | hittiguess",
  description: "The page you are looking for does not exist or has been moved.",
};

export default function NotFound() {
  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      <header className="border-b border-border bg-card/60 backdrop-blur-sm sticky top-0 z-30">
        <div className="max-w-5xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link href="/" aria-label="hittiguess home" className="flex items-center gap-2.5">
            <LogoBars
              barWidthPx={LOGO_BAR_WIDTH_PIXELS}
              colorClassName="bg-primary"
              containerClassName="h-[18px]"
            />
            <span className="font-display font-extrabold text-lg text-foreground">hittiguess</span>
          </Link>
          <div className="flex items-center gap-4">
            <Link
              href="/"
              className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
            >
              Home
            </Link>
            <ThemeToggle />
          </div>
        </div>
      </header>

      <main className="max-w-xl mx-auto px-6 py-20 flex-1 flex flex-col items-center justify-center text-center">
        <div className="w-full p-8 sm:p-10 rounded-3xl border border-border bg-card shadow-sm space-y-6">
          <div className="space-y-2">
            <span className="font-display text-5xl sm:text-6xl font-extrabold text-primary block">
              404
            </span>
            <h1 className="font-display text-2xl sm:text-3xl font-bold text-foreground">
              Page Not Found
            </h1>
          </div>
          <p className="text-muted-foreground text-sm sm:text-base leading-relaxed">
            The page you are looking for does not exist, has been removed, or is temporarily
            unavailable.
          </p>
          <div className="pt-2 flex flex-col sm:flex-row items-center justify-center gap-3">
            <Link
              href="/"
              className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-2.5 rounded-xl text-sm font-semibold bg-primary text-primary-foreground hover:opacity-90 transition-opacity"
            >
              Back to Home
            </Link>
            <Link
              href="/login"
              className="w-full sm:w-auto inline-flex items-center justify-center px-6 py-2.5 rounded-xl text-sm font-semibold border border-border bg-background text-foreground hover:bg-muted transition-colors"
            >
              Sign In
            </Link>
          </div>
        </div>
      </main>

      <PublicFooter />
    </div>
  );
}
