import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as Sentry from "@sentry/nextjs";
import ErrorPage from "./error";

vi.mock("@sentry/nextjs", () => ({
  captureException: vi.fn(),
  init: vi.fn(),
  addBreadcrumb: vi.fn(),
}));

describe("ErrorPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the error heading, error digest, and navigation options", () => {
    const error = new Error("Rendering crashed");
    (error as Error & { digest?: string }).digest = "test-digest-12345";
    const reset = vi.fn();

    render(<ErrorPage error={error} reset={reset} />);

    expect(screen.getByRole("heading", { name: /Something Went Wrong/i })).toBeDefined();
    expect(screen.getByText(/test-digest-12345/i)).toBeDefined();

    const homeLink = screen.getByRole("link", { name: "Back to Home" });
    expect(homeLink.getAttribute("href")).toBe("/");

    const loginLink = screen.getByRole("link", { name: "Sign In" });
    expect(loginLink.getAttribute("href")).toBe("/login");
  });

  it("calls reset callback when Try Again button is clicked", () => {
    const error = new Error("Temporary failure");
    const reset = vi.fn();

    render(<ErrorPage error={error} reset={reset} />);

    const retryButton = screen.getByRole("button", { name: "Try Again" });
    fireEvent.click(retryButton);

    expect(reset).toHaveBeenCalledTimes(1);
  });

  it("reports the exception to Sentry on mount", () => {
    const error = new Error("Reported error");
    const reset = vi.fn();

    render(<ErrorPage error={error} reset={reset} />);

    expect(Sentry.captureException).toHaveBeenCalledWith(error);
  });
});
