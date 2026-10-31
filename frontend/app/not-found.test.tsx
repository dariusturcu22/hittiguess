import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import NotFound, { metadata } from "./not-found";

describe("NotFound Page", () => {
  it("exports document metadata with 404 title", () => {
    expect(metadata.title).toContain("404");
    expect(metadata.description).toBeDefined();
  });

  it("renders the 404 status, message, and navigation actions", () => {
    render(<NotFound />);

    expect(screen.getByText("404")).toBeDefined();
    expect(screen.getByRole("heading", { name: /Page Not Found/i })).toBeDefined();

    const homeLink = screen.getByRole("link", { name: "Back to Home" });
    expect(homeLink.getAttribute("href")).toBe("/");

    const loginLink = screen.getByRole("link", { name: "Sign In" });
    expect(loginLink.getAttribute("href")).toBe("/login");
  });

  it("renders the public footer", () => {
    render(<NotFound />);

    const footer = screen.getByRole("contentinfo");
    expect(footer).toBeDefined();
    expect(screen.getByRole("link", { name: /Privacy/i })).toBeDefined();
  });
});
