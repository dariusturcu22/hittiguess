import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PublicFooter, GITHUB_REPOSITORY_URL, GITHUB_ISSUES_URL } from "./public-footer";

describe("PublicFooter", () => {
  it("renders the brand logo and message", () => {
    render(<PublicFooter />);

    expect(screen.getByText("hittiguess")).toBeInTheDocument();
    expect(screen.getByText(/No ads\. No tracking\. Free, always\./)).toBeInTheDocument();
  });

  it("renders all required public routes with valid links", () => {
    render(<PublicFooter />);

    const requiredInternalLinks: Record<string, string> = {
      "How it works": "/#how-it-works",
      About: "/about",
      Playlists: "/playlists",
      "Sign in": "/login",
      "Privacy Policy": "/privacy",
      "Terms of Service": "/terms",
      Copyright: "/copyright",
      "Credits & Licences": "/credits",
      Contact: "/contact",
    };

    for (const [name, expectedHref] of Object.entries(requiredInternalLinks)) {
      const linkElement = screen.getByRole("link", { name: new RegExp(name, "i") });
      expect(linkElement).toHaveAttribute("href", expectedHref);
    }
  });

  it("renders external community links with secure target and rel attributes", () => {
    render(<PublicFooter />);

    const githubLink = screen.getByRole("link", { name: /^GitHub$/i });
    expect(githubLink).toHaveAttribute("href", GITHUB_REPOSITORY_URL);
    expect(githubLink).toHaveAttribute("target", "_blank");
    expect(githubLink).toHaveAttribute("rel", "noopener noreferrer");

    const issuesLink = screen.getByRole("link", { name: /Report an issue/i });
    expect(issuesLink).toHaveAttribute("href", GITHUB_ISSUES_URL);
    expect(issuesLink).toHaveAttribute("target", "_blank");
    expect(issuesLink).toHaveAttribute("rel", "noopener noreferrer");
  });
});
