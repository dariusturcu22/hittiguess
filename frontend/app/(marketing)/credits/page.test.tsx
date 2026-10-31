import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import CreditsPage from "./page";

describe("CreditsPage", () => {
  it("renders the primary title and purpose description", () => {
    render(<CreditsPage />);

    expect(screen.getByRole("heading", { level: 1, name: "Credits & Licences" })).toBeInTheDocument();
    expect(screen.getByText(/hittiguess is free, open source, and operated without advertisements/i)).toBeInTheDocument();
  });

  it("attributes each external music data source with its licence and notice", () => {
    render(<CreditsPage />);

    expect(screen.getByRole("link", { name: "MusicBrainz" })).toHaveAttribute("href", "https://musicbrainz.org");
    expect(screen.getByText(/MetaBrainz Foundation/i)).toBeInTheDocument();
    expect(screen.getByText(/CC0 1.0 Universal \/ CC BY-NC-SA 3.0/i)).toBeInTheDocument();

    expect(screen.getByRole("link", { name: "Discogs" })).toHaveAttribute("href", "https://www.discogs.com");
    expect(screen.getByText(/Data provided by Discogs/i)).toBeInTheDocument();

    expect(screen.getByRole("link", { name: "Wikidata" })).toHaveAttribute("href", "https://www.wikidata.org");
    expect(screen.getByText(/CC0 1.0 Universal Public Domain Dedication/i)).toBeInTheDocument();

    expect(screen.getByRole("link", { name: "Wikipedia" })).toHaveAttribute("href", "https://www.wikipedia.org");
    expect(screen.getByText(/CC BY-SA 4.0 \/ GFDL/i)).toBeInTheDocument();

    expect(screen.getByRole("link", { name: "YouTube" })).toHaveAttribute("href", "https://www.youtube.com");
    expect(screen.getByText(/YouTube Terms of Service \/ Google Privacy Policy/i)).toBeInTheDocument();
  });

  it("displays font licences and open-source browser libraries", () => {
    render(<CreditsPage />);

    expect(screen.getByText("Bungee")).toBeInTheDocument();
    expect(screen.getByText("Baloo 2")).toBeInTheDocument();
    expect(screen.getByText("Space Grotesk")).toBeInTheDocument();
    expect(screen.getByText("Kanit")).toBeInTheDocument();

    expect(screen.getByText("Next.js")).toBeInTheDocument();
    expect(screen.getByText("React")).toBeInTheDocument();
    expect(screen.getByText("Tailwind CSS")).toBeInTheDocument();
    expect(screen.getByText("@sentry/nextjs")).toBeInTheDocument();
    expect(screen.getByText("@stomp/stompjs")).toBeInTheDocument();
    expect(screen.getByText("@sapphi-red/web-noise-suppressor")).toBeInTheDocument();
  });

  it("renders the shared public footer with links to public routes", () => {
    render(<CreditsPage />);

    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Privacy Policy/i })).toHaveAttribute("href", "/privacy");
    expect(screen.getByRole("link", { name: /Terms of Service/i })).toHaveAttribute("href", "/terms");
  });
});
