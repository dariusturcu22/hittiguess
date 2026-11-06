import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as Sentry from "@sentry/nextjs";
import GlobalError from "./global-error";

vi.mock("@sentry/nextjs", () => ({
  captureException: vi.fn(),
  init: vi.fn(),
  addBreadcrumb: vi.fn(),
}));

describe("GlobalError", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders critical error heading and navigation actions", () => {
    const error = new Error("Root crash");
    (error as Error & { digest?: string }).digest = "global-digest-999";
    const reset = vi.fn();

    render(<GlobalError error={error} reset={reset} />);

    expect(screen.getByRole("heading", { name: /Something went wrong/i })).toBeDefined();
    expect(screen.getByText(/global-digest-999/i)).toBeDefined();

    const homeLink = screen.getByRole("link", { name: "Home" });
    expect(homeLink.getAttribute("href")).toBe("/");

    const loginLink = screen.getByRole("link", { name: "Sign In" });
    expect(loginLink.getAttribute("href")).toBe("/login");
  });

  it("calls reset when Try Again button is clicked", () => {
    const error = new Error("Fatal failure");
    const reset = vi.fn();

    render(<GlobalError error={error} reset={reset} />);

    const retryButton = screen.getByRole("button", { name: "Try Again" });
    fireEvent.click(retryButton);

    expect(reset).toHaveBeenCalledTimes(1);
  });

  it("reports the critical error to Sentry on mount", () => {
    const error = new Error("Fatal unhandled crash");
    const reset = vi.fn();

    render(<GlobalError error={error} reset={reset} />);

    expect(Sentry.captureException).toHaveBeenCalledWith(error);
  });
});
