import Link from "next/link";
import { Button } from "@/components/shadcn/button";
import { LogoBars } from "@/components/logo";
import { LandingHeader } from "./landing-header";
import "./landing.css";

const ROUND_STEPS = [
  { key: "play", title: "DJ hits play", body: "Real YouTube, nothing hidden.", icon: <path d="M8 5v14l11-7z" /> },
  { key: "place", title: "You place it", body: "On your own timeline.", icon: <><path d="M12 21c-4.2-4.6-7-8.4-7-12a7 7 0 0 1 14 0c0 3.6-2.8 7.4-7 12z" /><circle cx="12" cy="9" r="2.4" /></> },
  { key: "bet", title: "Others bet", body: "Wrong guess, stolen card.", icon: <><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="4" /><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3" /></> },
  { key: "reveal", title: "Reveal", body: "Artist, title, year, truth.", icon: <><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z" /><circle cx="12" cy="12" r="3" /></> },
];

function StartAction() {
  return <Button asChild className="landing-action"><Link href="/register">Start a session</Link></Button>;
}
export default function LandingPage() {
  return (
    <div className="landing-page">
      <section className="landing-hero landing-dots">
        <div className="hero-circle" aria-hidden="true" />
        <LandingHeader />
        <div className="hero-copy">
          <h1>PLACE IT.<br />GUESS IT.<br />WIN IT.</h1>
          <p>Hear a song, guess the year, place it on your own timeline. Bring your own playlist or import one, either way it just works.</p>
          <div className="hero-actions"><StartAction /><span>or <Link href="/login">sign in</Link></span></div>
        </div>
        <div className="hero-timeline" aria-label="A music timeline with a missing year">
          <div className="timeline-card timeline-first"><div className="artist">RADIOHEAD</div><div className="year">1994</div><div className="title">Creep</div></div>
          <div className="timeline-card timeline-unknown"><div className="year">?</div></div>
          <div className="timeline-card timeline-last"><div className="artist">SNOW PATROL</div><div className="year">2006</div><div className="title">Chasing Cars</div></div>
        </div>
      </section>
      <section id="how-it-works" className="landing-round landing-dots">
        <div className="round-circle" aria-hidden="true" /><div className="round-square" aria-hidden="true" />
        <h2 className="section-title">How a round works</h2>
        <div className="round-steps">
          <svg className="round-path desktop-path" viewBox="0 0 900 260" aria-hidden="true"><path d="M115,55 Q226,-10 338,105 Q450,170 562,55 Q673,-10 785,105" /></svg>
          <svg className="round-path mobile-path" viewBox="0 0 340 550" aria-hidden="true"><path d="M70,50 Q42,146 170,190 Q218,254 70,330 Q42,426 170,470" /></svg>
          {ROUND_STEPS.map((step) => <div className={`round-step step-${step.key}`} key={step.key}>
            <div className="step-icon"><svg viewBox="0 0 24 24" aria-hidden="true">{step.icon}</svg></div>
            <div className="step-label"><h3>{step.title}</h3><p>{step.body}</p></div>
          </div>)}
        </div>
      </section>
      <section className="landing-highlight highlight-music landing-dots">
        <div className="highlight-circle" aria-hidden="true" /><div className="highlight-square" aria-hidden="true" />
        <div className="highlight-copy"><h2>YOUR MUSIC.<br />YOUR RULES.</h2><p>Build your <u>own playlist</u>. Import one. Play someone else&apos;s, or let us generate one for you.</p></div>
      </section>
      <section className="landing-highlight highlight-betting landing-dots">
        <div className="highlight-circle" aria-hidden="true" /><div className="highlight-square" aria-hidden="true" />
        <div className="highlight-copy"><h2>GUESS IT.<br />BET IT.</h2><p>Name the artist and title for a token. Save it to bet someone<span className="desktop-only"> else</span> got their placement wrong, and <u>steal the card</u> if you&apos;re right.</p></div>
      </section>
      <section className="landing-highlight highlight-together landing-dots">
        <div className="highlight-circle" aria-hidden="true" /><div className="highlight-square" aria-hidden="true" />
        <div className="highlight-copy"><h2>PLAY TOGETHER.<br />TRUST BUILT IN.</h2><p>Voice and text chat built in, no separate app needed. Official APIs only, GDPR-minded, <u>no ads, no tracking</u>, ever.</p></div>
      </section>
      <section className="landing-ready landing-dots">
        <div className="ready-circle" aria-hidden="true" /><div className="ready-square" aria-hidden="true" />
        <div className="ready-card"><h2 className="section-title">Ready?</h2><p>Start a session and find out who actually knows their music.</p><StartAction /></div>
      </section>
      <footer className="landing-footer">
        <div className="footer-brand"><div><LogoBars barWidthPx={4} colorClassName="bg-icon-muted" containerClassName="h-[18px]" /><span>hittiguess</span></div><p>No ads. No tracking. Free, always.<span className="desktop-only"> Built for friends, not for profit.</span></p></div>
        <div className="footer-columns">
          <div><h3>Product</h3><Link href="#how-it-works">How it works</Link><Link className="desktop-only" href="/playlists">Playlists</Link><Link href="/login">Sign in</Link></div>
          <div><h3>Legal</h3><span>Privacy Policy</span><span className="desktop-only">Terms of Service</span><span className="mobile-only">Terms</span><span className="desktop-only">Data &amp; GDPR</span><span className="mobile-only">GDPR</span></div>
          <div><h3><span className="desktop-only">Community</span><span className="mobile-only">More</span></h3><span>GitHub</span><span className="desktop-only">Report an issue</span></div>
        </div>
      </footer>
    </div>
  );
}
