import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { NewSongLinkStep } from "./NewSongLinkStep";

function renderStep(onFetch = vi.fn(), isFetching = false) {
  render(<NewSongLinkStep onFetch={onFetch} isFetching={isFetching} onBackToSearch={vi.fn()} fetchError="" />);
  return onFetch;
}

describe("NewSongLinkStep", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });
  it("rejects input without a YouTube video ID", () => {
    const onFetch = renderStep();
    fireEvent.change(screen.getByLabelText("YouTube link"), { target: { value: "not a link" } });
    fireEvent.click(screen.getByRole("button", { name: "Fetch details" }));
    expect(screen.getByText(/couldn.t extract a youtube id/i)).toBeInTheDocument();
    expect(onFetch).not.toHaveBeenCalled();
  });

  it("extracts a valid ID from a watch URL", () => {
    const onFetch = renderStep();
    fireEvent.change(screen.getByLabelText("YouTube link"), { target: { value: "https://youtube.com/watch?v=abc12345678" } });
    fireEvent.click(screen.getByRole("button", { name: "Fetch details" }));
    expect(onFetch).toHaveBeenCalledWith("abc12345678");
  });

  it("rotates through pipeline stages while fetching", () => {
    renderStep(vi.fn(), true);

    expect(screen.getByText("Reading the video title and channel...")).toBeVisible();
  });
});
