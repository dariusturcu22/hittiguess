import React, { Component, ReactNode } from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import ErrorPage from "./error";
import NotFound from "./not-found";

vi.mock("@sentry/nextjs", () => ({
  captureException: vi.fn(),
  init: vi.fn(),
  addBreadcrumb: vi.fn(),
}));

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
}

class TestErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  render() {
    if (this.state.hasError && this.state.error) {
      return (
        <ErrorPage
          error={this.state.error}
          reset={() => this.setState({ hasError: false, error: undefined })}
        />
      );
    }
    return this.props.children;
  }
}

function ProblematicComponent({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) {
    throw new Error("Simulated render failure");
  }
  return <div>Normal Content</div>;
}

describe("Route Error Boundaries and Fallbacks", () => {
  it("renders the 404 page for unknown routes", () => {
    render(<NotFound />);

    expect(screen.getByText("404")).toBeDefined();
    expect(screen.getByRole("heading", { name: /Page Not Found/i })).toBeDefined();
    expect(screen.getByRole("link", { name: "Back to Home" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Sign In" })).toBeDefined();
  });

  it("shows the error page when a child component throws a render error", () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    const { rerender } = render(
      <TestErrorBoundary>
        <ProblematicComponent shouldThrow={true} />
      </TestErrorBoundary>
    );

    expect(screen.getByRole("heading", { name: /Something Went Wrong/i })).toBeDefined();
    expect(screen.getByRole("button", { name: "Try Again" })).toBeDefined();
    expect(screen.getByRole("link", { name: "Back to Home" })).toBeDefined();

    // Recover by resetting state with non-throwing child
    rerender(
      <TestErrorBoundary>
        <ProblematicComponent shouldThrow={false} />
      </TestErrorBoundary>
    );

    fireEvent.click(screen.getByRole("button", { name: "Try Again" }));
    expect(screen.getByText("Normal Content")).toBeDefined();

    consoleErrorSpy.mockRestore();
  });
});
