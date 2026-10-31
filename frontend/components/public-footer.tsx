import Link from "next/link";
import { LogoBars } from "@/components/logo";
import "./public-footer.css";

interface PublicFooterProps {
  className?: string;
}

export const GITHUB_REPOSITORY_URL = "https://github.com/dariusturcu22/hittiguess";
export const GITHUB_ISSUES_URL = "https://github.com/dariusturcu22/hittiguess/issues";

export function PublicFooter({ className = "" }: PublicFooterProps) {
  return (
    <footer className={`public-footer landing-footer ${className}`.trim()} role="contentinfo">
      <div className="footer-brand">
        <div>
          <LogoBars barWidthPx={4} colorClassName="bg-icon-muted" containerClassName="h-[18px]" />
          <span>hittiguess</span>
        </div>
        <p>
          No ads. No tracking. Free, always.
          <span className="desktop-only"> Built for friends, not for profit.</span>
        </p>
      </div>
      <div className="footer-columns">
        <div>
          <h3>Product</h3>
          <Link href="/#how-it-works">How it works</Link>
          <Link href="/about">About</Link>
          <Link className="desktop-only" href="/playlists">
            Playlists
          </Link>
          <Link href="/login">Sign in</Link>
        </div>
        <div>
          <h3>Legal</h3>
          <Link href="/privacy">
            <span className="desktop-only">Privacy Policy</span>
            <span className="mobile-only">Privacy</span>
          </Link>
          <Link href="/terms">
            <span className="desktop-only">Terms of Service</span>
            <span className="mobile-only">Terms</span>
          </Link>
          <Link href="/copyright">Copyright</Link>
          <Link href="/credits">
            <span className="desktop-only">Credits &amp; Licences</span>
            <span className="mobile-only">Credits</span>
          </Link>
        </div>
        <div>
          <h3>
            <span className="desktop-only">Community</span>
            <span className="mobile-only">More</span>
          </h3>
          <Link href="/contact">Contact</Link>
          <a href={GITHUB_REPOSITORY_URL} target="_blank" rel="noopener noreferrer">
            GitHub
          </a>
          <a
            className="desktop-only"
            href={GITHUB_ISSUES_URL}
            target="_blank"
            rel="noopener noreferrer"
          >
            Report an issue
          </a>
        </div>
      </div>
    </footer>
  );
}
