import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { VoiceMemberList } from "./voice-member-list";

const USER_ID = 11;
const OTHER_USER_ID = 12;
const MEMBERS = [{ userId: USER_ID, displayName: "Alex", avatarUrl: "/avatar.png" }, { userId: OTHER_USER_ID, displayName: "Sam" }];
const EXIT_MILLISECONDS = 220;
afterEach(() => vi.useRealTimers());

describe("VoiceMemberList", () => {
  it("uses actual avatars and shows distinct microphone and listening status", () => {
    const localStatus = { isMuted: false, isDeafened: false, isSpeaking: true };
    const remoteStatus = { isMuted: true, isDeafened: true, isSpeaking: false };
    const { container } = render(<VoiceMemberList members={MEMBERS} currentUserId={USER_ID} localStatus={localStatus} memberStatuses={{ [OTHER_USER_ID]: remoteStatus }} />);
    expect(container.querySelector('img')?.getAttribute("src")).toMatch(/\/avatar\.png$/);
    expect(screen.getByLabelText("Alex, microphone on, listening, speaking").querySelector(".ring-green")).toBeTruthy();
    expect(screen.getByLabelText("Sam, muted, deafened")).toBeVisible();
    expect(screen.getByTitle("Muted")).toBeVisible();
    expect(screen.getByTitle("Deafened")).toBeVisible();
  });

  it("retains departures briefly for exit motion and keeps live member details", () => {
    vi.useFakeTimers();
    const view = render(<VoiceMemberList members={MEMBERS} />);
    const [remainingMember] = MEMBERS;
    view.rerender(<VoiceMemberList members={[{ ...remainingMember, displayName: "Updated name" }]} />);
    expect(screen.getByText("Updated name")).toBeVisible();
    expect(screen.getByLabelText("Sam, connecting")).toHaveClass("voice-member-leaving");
    act(() => vi.advanceTimersByTime(EXIT_MILLISECONDS));
    expect(screen.queryByText("Sam")).not.toBeInTheDocument();
  });
});
