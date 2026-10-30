import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { AudioShareTest } from "@/hooks/use-audio-share-test";
import { AudioPauseOverlay, stoppedSharingMessage } from "./audio-pause-overlay";

function shareTest(overrides: Partial<AudioShareTest> = {}): AudioShareTest {
  return { isCapturing: false, level: 0, hasPassed: false, isSilent: false, shareErrorMessage: null, shareErrorKind: null, share: vi.fn(async () => {}), changeSharedScreen: vi.fn(async () => {}), ...overrides };
}

describe("AudioPauseOverlay", () => {
  it("tells everyone else who stopped sharing", () => {
    render(<AudioPauseOverlay waitingForNames={["Mara"]} isWaitingForCurrentPlayer={false} shareTest={shareTest()} />);

    expect(screen.getByRole("dialog", { name: "Game paused" })).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent("Mara stopped sharing, waiting for them to share again");
    expect(screen.queryByRole("button", { name: "Share your audio" })).toBeNull();
  });

  it("names every player the game waits for", () => {
    expect(stoppedSharingMessage(["Mara", "Sam"])).toBe("Mara and Sam stopped sharing, waiting for them to share again");
  });

  it("gives the player whose share stopped the share and the test inline", () => {
    const test = shareTest();
    render(<AudioPauseOverlay waitingForNames={["Mara"]} isWaitingForCurrentPlayer shareTest={test} />);

    expect(screen.getByRole("dialog", { name: "Share your audio again" })).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Share your audio" }));
    expect(test.share).toHaveBeenCalledOnce();
  });

  it("shows the running test once the new share is up", () => {
    render(<AudioPauseOverlay waitingForNames={["Mara"]} isWaitingForCurrentPlayer shareTest={shareTest({ isCapturing: true, level: 0.1 })} />);

    expect(screen.getByText("Play anything to test the audio")).toBeVisible();
    expect(screen.getByRole("meter", { name: "Shared audio level" })).toBeVisible();
  });
});
