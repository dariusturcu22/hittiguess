import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { NewSongLinkStep } from "./NewSongLinkStep";

function renderStep(onFetch = vi.fn()) {
  render(<NewSongLinkStep onFetch={onFetch} isFetching={false} onBackToSearch={vi.fn()} fetchError="" />);
  return onFetch;
}

describe("NewSongLinkStep", () => {
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
});
