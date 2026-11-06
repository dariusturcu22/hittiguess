import { render, screen, within } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import AboutPage, { metadata } from "./page";

describe("AboutPage", () => {
  it("exports correct document metadata", () => {
    expect(metadata.title).toBe("About");
    expect(metadata.alternates.canonical).toBe("/about");
    expect(metadata.description).toContain("Learn about hittiguess");
  });

  it("renders what hittiguess is", () => {
    render(<AboutPage />);

    expect(screen.getByRole("heading", { level: 1, name: /About hittiguess/i })).toBeDefined();
    expect(
      screen.getByText(/multiplayer music guessing game inspired by Hitster/i)
    ).toBeDefined();
  });

  it("renders how a round works across four sentences", () => {
    render(<AboutPage />);

    expect(
      screen.getByText(
        /The designated DJ plays the mystery song on YouTube without revealing the title or artist to the group\./i
      )
    ).toBeDefined();
    expect(
      screen.getByText(
        /The active player listens to the track and places the card on their timeline where they believe the release year belongs\./i
      )
    ).toBeDefined();
    expect(
      screen.getByText(
        /Other players can wager a token on where they think the track belongs before the year is revealed\./i
      )
    ).toBeDefined();
    expect(
      screen.getByText(
        /The true release year is revealed, correct timeline placements and token bets score points, and the turn passes to the next player\./i
      )
    ).toBeDefined();
  });

  it("renders statements that it is free, open source, and has no ads or trackers", () => {
    render(<AboutPage />);

    expect(screen.getByText(/100% Free Forever/i)).toBeDefined();
    expect(screen.getByText(/Fully Open Source/i)).toBeDefined();
    expect(screen.getByText(/No Ads or Trackers/i)).toBeDefined();
  });

  it("renders who builds hittiguess with GitHub link and author credit", () => {
    render(<AboutPage />);

    expect(screen.getByText(/Darius Turcu/i)).toBeDefined();
    const githubLink = screen.getByRole("link", { name: "View on GitHub" });
    expect(githubLink.getAttribute("href")).toBe("https://github.com/dariusturcu22/hittiguess");
  });

  it("renders the public footer with expected public links", () => {
    render(<AboutPage />);

    const footer = screen.getByRole("contentinfo");
    expect(footer).toBeDefined();
    expect(within(footer).getByRole("link", { name: /Privacy/i })).toBeDefined();
    expect(within(footer).getByRole("link", { name: /Terms/i })).toBeDefined();
    expect(within(footer).getByRole("link", { name: /Credits/i })).toBeDefined();
    expect(within(footer).getByRole("link", { name: /^About$/i })).toBeDefined();
  });
});
