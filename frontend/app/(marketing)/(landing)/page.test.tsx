import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import LandingPage from "./page";

vi.mock("./landing-auth-redirect", () => ({
  LandingAuthRedirect: () => <div data-testid="landing-auth-redirect" />,
}));

vi.mock("./landing-header", () => ({
  LandingHeader: () => <header />,
}));

describe("LandingPage", () => {
  it("renders the signed-in redirect for every visitor", () => {
    render(<LandingPage />);

    expect(screen.getByTestId("landing-auth-redirect")).toBeTruthy();
  });

  it("leads the calls to action to the real register and login routes", () => {
    render(<LandingPage />);

    const registerLinks = screen.getAllByRole("link", { name: "Start a session" });
    expect(registerLinks.length).toBeGreaterThan(0);
    registerLinks.forEach((link) => expect(link.getAttribute("href")).toBe("/register"));
    screen.getAllByRole("link", { name: /sign in/i }).forEach((link) => expect(link.getAttribute("href")).toBe("/login"));
  });

  it("renders both desktop and mobile structure variants for responsive viewports", () => {
    const { container } = render(<LandingPage />);

    expect(container.querySelector(".desktop-path")).toBeTruthy();
    expect(container.querySelector(".mobile-path")).toBeTruthy();

    const mobileOnlyElements = container.querySelectorAll(".mobile-only");
    expect(mobileOnlyElements.length).toBeGreaterThan(0);

    const desktopOnlyElements = container.querySelectorAll(".desktop-only");
    expect(desktopOnlyElements.length).toBeGreaterThan(0);

    expect(screen.getByText("How a round works")).toBeVisible();
    expect(screen.getByText(/YOUR MUSIC/)).toBeVisible();
    expect(screen.getByText(/BET IT/)).toBeVisible();
    expect(screen.getByText(/PLAY TOGETHER/)).toBeVisible();
  });
});
