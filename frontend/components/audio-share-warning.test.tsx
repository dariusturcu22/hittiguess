import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";

import { AudioShareWarning } from "./audio-share-warning";

describe("AudioShareWarning", () => {
  beforeEach(() => window.sessionStorage.clear());
  afterEach(cleanup);

  it("shows nothing when the audio can be shared", () => {
    render(<AudioShareWarning canShareAudio isDj={false} djCanShareAudio />);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("explains the skip and stays gone for the session once dismissed", () => {
    const { unmount } = render(<AudioShareWarning canShareAudio={false} isDj={false} djCanShareAudio />);
    expect(screen.getByRole("status")).toHaveTextContent("skipped as DJ");
    fireEvent.click(screen.getByRole("button", { name: "Dismiss audio warning" }));
    expect(screen.queryByRole("status")).toBeNull();
    unmount();
    render(<AudioShareWarning canShareAudio={false} isDj={false} djCanShareAudio />);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("tells listeners when the DJ cannot share", () => {
    render(<AudioShareWarning canShareAudio isDj={false} djCanShareAudio={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("will not play on your device");
  });
});
