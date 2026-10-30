import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { GroupDetailDTO } from "@/hooks/models/groupDetailDTO";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import type { AudioShareTest } from "@/hooks/use-audio-share-test";
import { AudioCheckScreen, type AudioCheckScreenProps } from "./audio-check-screen";

const HOST_ID = 1;
const onStart = vi.fn();
const onSwitchToFixedDj = vi.fn();
const onBackToLobby = vi.fn();
const share = vi.fn(async () => {});
const changeSharedScreen = vi.fn(async () => {});

function member(id: number, displayName: string, audioCheckStatus: string, overrides: Record<string, unknown> = {}): MemberDTO {
  return { id, userId: id + 100, displayName, isAdmin: id === HOST_ID, isConnected: true, joinedAt: `2026-10-30T10:00:0${id}Z`, audioCheckStatus, ...overrides } as MemberDTO;
}

function group(overrides: Partial<GroupDetailDTO>): GroupDetailDTO {
  return { id: 4, status: "AUDIO_CHECK", djMode: "ROTATING", ...overrides } as GroupDetailDTO;
}

function shareTest(overrides: Partial<AudioShareTest> = {}): AudioShareTest {
  return { isCapturing: false, level: 0, hasPassed: false, isSilent: false, shareErrorMessage: null, shareErrorKind: null, share, changeSharedScreen, ...overrides };
}

function renderScreen(props: Partial<AudioCheckScreenProps> & { group: GroupDetailDTO }) {
  return render(<AudioCheckScreen currentMemberId={HOST_ID} shareTest={shareTest()} onStart={onStart} onSwitchToFixedDj={onSwitchToFixedDj} onBackToLobby={onBackToLobby} {...props} />);
}

const ROTATING_IN_PROGRESS = group({
  members: [
    member(1, "Darius", "OK"),
    member(2, "Mara", "OK"),
    member(3, "Sam", "TESTING"),
    member(4, "Lena", "FAILED"),
    member(5, "Kai", "NOT_SHARED"),
    member(6, "Theo", "CANNOT_SHARE"),
  ],
  audioCheck: { requiredMemberIds: [1, 2, 3, 4, 5, 6], readyCount: 2, canStart: false, nobodyCanShare: false },
});

describe("AudioCheckScreen", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows the host every player's share and keeps Start disabled until everyone passed", () => {
    renderScreen({ group: ROTATING_IN_PROGRESS });

    expect(screen.getByRole("heading", { name: "Audio check" })).toBeVisible();
    const rows = screen.getAllByRole("listitem");
    expect(rows.map((row) => row.getAttribute("data-audio-status"))).toEqual(["OK", "OK", "TESTING", "FAILED", "NOT_SHARED", "CANNOT_SHARE"]);
    expect(within(rows[0]).getByText("Shared, audio OK")).toBeVisible();
    expect(within(rows[0]).getByText("You")).toBeVisible();
    expect(within(rows[2]).getByText("Testing audio...")).toBeVisible();
    expect(within(rows[3]).getByText("No audio detected")).toBeVisible();
    expect(within(rows[3]).getByText("The share has no sound")).toBeVisible();
    expect(within(rows[3]).queryByRole("button", { name: "Share again" })).toBeNull();
    expect(within(rows[4]).getByText("Waiting to share")).toBeVisible();
    expect(within(rows[5]).getByText("Can't share audio in this browser")).toBeVisible();
    expect(within(rows[5]).getByText(/Open the game in Chrome or Edge on a Windows computer or a Mac/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Start game" })).toBeDisabled();
    expect(screen.getByText("2 of 6 players ready")).toBeVisible();
    expect(screen.getByRole("button", { name: "Switch to a fixed DJ" })).toBeVisible();
  });

  it("shows a player whose share failed their own card to share again", () => {
    renderScreen({ group: ROTATING_IN_PROGRESS, currentMemberId: 4 });
    expect(screen.getByRole("heading", { name: "Audio check" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Share your audio" })).toBeVisible();
  });

  it("shows a player who has not shared their own card with one big share button", () => {
    renderScreen({ group: ROTATING_IN_PROGRESS, currentMemberId: 5 });

    expect(screen.getByText(/Share it once now, then run a quick test/)).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Share your audio" }));
    expect(share).toHaveBeenCalledOnce();
    expect(screen.getByText("Test the audio")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Start game" })).toBeNull();
    expect(screen.getByText("2 of 6 players ready")).toBeVisible();
  });

  it("runs the sound test with a live meter once the screen is shared", () => {
    renderScreen({ group: ROTATING_IN_PROGRESS, currentMemberId: 3, shareTest: shareTest({ isCapturing: true, level: 0.2 }) });

    expect(screen.getByText("Screen shared")).toBeVisible();
    expect(screen.getByText(/Keep this share running for the whole game/)).toBeVisible();
    expect(screen.getByText("Play anything to test the audio")).toBeVisible();
    expect(Number(screen.getByRole("meter", { name: "Shared audio level" }).getAttribute("aria-valuenow"))).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: "Open YouTube" })).toHaveAttribute("target", "_blank");
    expect(screen.getByText("Listening...")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Change shared screen" }));
    expect(changeSharedScreen).toHaveBeenCalledOnce();
  });

  it("explains a share without sound on the player's own card", () => {
    renderScreen({ group: ROTATING_IN_PROGRESS, currentMemberId: 5, shareTest: shareTest({ shareErrorKind: "no-audio-track", shareErrorMessage: "Chrome shared no audio." }) });

    expect(screen.getByRole("alert")).toHaveTextContent("Tab and window shares do not carry sound");
  });

  it("tells players in a fixed game they are waiting for the DJ", () => {
    renderScreen({
      group: group({
        djMode: "FIXED",
        fixedDjMemberId: 2,
        members: [member(1, "Darius", "NOT_SHARED"), member(2, "Mara", "TESTING"), member(3, "Sam", "NOT_SHARED")],
        audioCheck: { requiredMemberIds: [2], readyCount: 0, canStart: false, nobodyCanShare: false },
      }),
      currentMemberId: 3,
    });

    expect(screen.getByRole("heading", { name: "Waiting for Mara" })).toBeVisible();
    expect(screen.getByText(/Nothing to set up on your side/)).toBeVisible();
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
    expect(screen.getByText("DJ")).toBeVisible();
    expect(screen.getByText("The host starts the game once Mara is ready.")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Start game" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Back to lobby" })).toBeNull();
  });

  it("lets the host start once every player has a working share", () => {
    renderScreen({
      group: group({
        members: [member(1, "Darius", "OK"), member(2, "Mara", "OK")],
        audioCheck: { requiredMemberIds: [1, 2], readyCount: 2, canStart: true, nobodyCanShare: false },
      }),
    });

    expect(screen.getByText("Every player has a working share. The game is ready to start.")).toBeVisible();
    expect(screen.getByText("2 of 2 players ready")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Switch to a fixed DJ" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Start game" }));
    expect(onStart).toHaveBeenCalledOnce();
  });

  it("says the game cannot start when nobody can share, with only Back to lobby", () => {
    renderScreen({
      group: group({
        members: [member(1, "Darius", "CANNOT_SHARE"), member(2, "Mara", "CANNOT_SHARE")],
        audioCheck: { requiredMemberIds: [1, 2], readyCount: 0, canStart: false, nobodyCanShare: true },
      }),
      ownBrowserName: "Safari",
    });

    expect(screen.getByRole("heading", { name: "Nobody can share audio" })).toBeVisible();
    expect(screen.getByText("What to do")).toBeVisible();
    expect(screen.getByText(/^Safari\. Open the game in Chrome or Edge/)).toBeVisible();
    expect(screen.getByRole("button", { name: "Start game" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Switch to a fixed DJ" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Back to lobby" }));
    expect(onBackToLobby).toHaveBeenCalledOnce();
  });

  it("switches to a fixed DJ chosen among players whose share passed", () => {
    renderScreen({ group: ROTATING_IN_PROGRESS });

    fireEvent.click(screen.getByRole("button", { name: "Switch to a fixed DJ" }));
    const picker = screen.getByRole("alertdialog");
    expect(within(picker).queryByRole("button", { name: "Sam" })).toBeNull();
    fireEvent.click(within(picker).getByRole("button", { name: "Mara" }));

    expect(onSwitchToFixedDj).toHaveBeenCalledWith(2);
  });

  it("shows a start error under the actions", () => {
    renderScreen({ group: ROTATING_IN_PROGRESS, errorMessage: "Waiting for a working audio share from Sam" });
    expect(screen.getByRole("alert")).toHaveTextContent("Waiting for a working audio share from Sam");
  });
});
