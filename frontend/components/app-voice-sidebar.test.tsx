import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppVoiceSidebar } from "./app-voice-sidebar";

const joinVoice = vi.fn();
const leaveVoice = vi.fn();
const reportAudioCapability = vi.fn();
const startMicrophone = vi.fn(async () => true);
let groupMembers: Array<{ id: number; userId: number; displayName: string; isInVoice: boolean }> = [];
let hasGroup = true;
const toggleMute = vi.fn();
const toggleDeafen = vi.fn();

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetActiveMembershipQueryKey: () => ["active-membership"],
  getGetGroupQueryKey: (groupId: number) => ["group", groupId],
  useGetActiveMembership: () => ({
    data: hasGroup ? { id: 4, members: groupMembers } : undefined,
  }),
  useJoinVoice: () => ({ mutate: joinVoice, isPending: false }),
  useLeaveVoice: () => ({ mutate: leaveVoice, isPending: false }),
  useReportAudioCapability: () => ({ mutate: reportAudioCapability }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({ data: { id: 11 } }),
}));

const voiceMeshState = vi.hoisted(() => ({ holdSignalling: false, isVoiceStarted: true }));

vi.mock("@/hooks/use-voice-mesh", () => ({
  useVoiceMesh: (_groupId: number, _userId: number | undefined, _members: unknown[], isSignalling: boolean) => ({
    // The signalling socket connects as soon as the sidebar asks for it, unless a test
    // holds it back to check the join waits for it.
    isSignalConnected: isSignalling && !voiceMeshState.holdSignalling,
    isVoiceStarted: voiceMeshState.isVoiceStarted,
    startMicrophone,
    stopVoice: vi.fn(),
    toggleMute,
    toggleDeafen,
    isMuted: false,
    isDeafened: false,
    microphoneError: false,
    microphoneErrorMessage: null,
    localStatus: { isMuted: false, isDeafened: false, isSpeaking: false },
    memberStatuses: {},
  }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({
    invalidateQueries: vi.fn(),
  }),
}));

describe("AppVoiceSidebar", () => {
  beforeEach(() => {
    hasGroup = true;
    voiceMeshState.holdSignalling = false;
    voiceMeshState.isVoiceStarted = true;
    groupMembers = [{ id: 1, userId: 11, displayName: "Alex", isInVoice: true }];
    window.localStorage.clear();
    joinVoice.mockClear();
    leaveVoice.mockClear();
    reportAudioCapability.mockClear();
    startMicrophone.mockClear();
    startMicrophone.mockResolvedValue(true);
    toggleMute.mockClear();
    toggleDeafen.mockClear();
  });

  it("exposes labeled voice controls that activate from the keyboard", async () => {
    render(<AppVoiceSidebar />);

    expect(screen.getByRole("button", { name: "Mute" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Deafen" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Leave voice" })).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Mute" }));
    fireEvent.click(screen.getByRole("button", { name: "Deafen" }));

    expect(toggleMute).toHaveBeenCalledOnce();
    expect(toggleDeafen).toHaveBeenCalledOnce();
  });

  it("reports the mute and deafen toggle states to assistive technology", async () => {
    render(<AppVoiceSidebar />);

    expect(screen.getByRole("button", { name: "Mute" })).toHaveAttribute("aria-pressed", "false");
    expect(screen.getByRole("button", { name: "Deafen" })).toHaveAttribute("aria-pressed", "false");
  });

  it("labels the join action and starts the microphone before joining", async () => {
    groupMembers = [{ id: 1, userId: 11, displayName: "Alex", isInVoice: false }];
    render(<AppVoiceSidebar />);

    fireEvent.click(screen.getByRole("button", { name: "Join call" }));

    await waitFor(() => expect(startMicrophone).toHaveBeenCalledOnce());
    await waitFor(() => expect(joinVoice).toHaveBeenCalledWith({ groupId: 4 }, expect.anything()));
  });

  it("lists who is in the call before joining", () => {
    groupMembers = [
      { id: 1, userId: 11, displayName: "Alex", isInVoice: false },
      { id: 2, userId: 22, displayName: "Blair", isInVoice: true },
    ];
    render(<AppVoiceSidebar />);

    expect(screen.getByText("Blair")).toBeVisible();
    expect(screen.queryByText("Alex")).toBeNull();
    expect(screen.getByRole("button", { name: "Join call" })).toBeVisible();
  });

  it("shows the rail with the join action outside a call on any page in a group", () => {
    groupMembers = [{ id: 1, userId: 11, displayName: "Alex", isInVoice: false }];
    render(<AppVoiceSidebar />);

    expect(screen.getByRole("complementary", { name: "Voice sidebar" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Join call" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /ollapse|Expand/ })).toBeNull();
  });

  it("hides the rail only when the user is in no group", () => {
    hasGroup = false;
    render(<AppVoiceSidebar />);

    expect(screen.queryByRole("complementary", { name: "Voice sidebar" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Join call" })).toBeNull();
  });

  it("keeps the full sidebar everywhere while in a call", () => {
    render(<AppVoiceSidebar />);

    expect(screen.getByRole("button", { name: "Leave voice" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /ollapse|Expand/ })).toBeNull();
  });

  it("announces the join only once its signalling socket is connected", async () => {
    groupMembers = [{ id: 1, userId: 11, displayName: "Alex", isInVoice: false }];
    voiceMeshState.holdSignalling = true;
    const { rerender } = render(<AppVoiceSidebar />);

    fireEvent.click(screen.getByRole("button", { name: "Join call" }));
    await waitFor(() => expect(startMicrophone).toHaveBeenCalledOnce());
    expect(joinVoice).not.toHaveBeenCalled();

    voiceMeshState.holdSignalling = false;
    rerender(<AppVoiceSidebar />);
    await waitFor(() => expect(joinVoice).toHaveBeenCalledWith({ groupId: 4 }, expect.anything()));
  });

  it("still joins the call as a listener when the microphone can't start", async () => {
    groupMembers = [{ id: 1, userId: 11, displayName: "Alex", isInVoice: false }];
    startMicrophone.mockResolvedValue(false);
    render(<AppVoiceSidebar />);

    fireEvent.click(screen.getByRole("button", { name: "Join call" }));

    await waitFor(() => expect(joinVoice).toHaveBeenCalledWith({ groupId: 4 }, expect.anything()));
  });

  it("keeps the same 76px width as the left sidebar in and out of a call", () => {
    groupMembers = [{ id: 1, userId: 11, displayName: "Alex", isInVoice: false }];
    const { unmount } = render(<AppVoiceSidebar />);
    expect(screen.getByRole("complementary", { name: "Voice sidebar" })).toHaveClass("w-[76px]");
    unmount();

    groupMembers = [{ id: 1, userId: 11, displayName: "Alex", isInVoice: true }];
    render(<AppVoiceSidebar />);
    expect(screen.getByRole("complementary", { name: "Voice sidebar" })).toHaveClass("w-[76px]");
  });

  it("scales its rail height with the sidebar height steps", () => {
    groupMembers = [{ id: 1, userId: 11, displayName: "Alex", isInVoice: false }];
    render(<AppVoiceSidebar />);
    const rail = screen.getByRole("complementary", { name: "Voice sidebar" });

    expect(rail.className).toContain("h-full");
    expect(rail.className).toContain("min-h-0");
    expect(rail.className).toContain("short:py-4");
    expect(rail.className).toContain("very-short:py-3");
  });

  it("clears a stale in-call listing when this browser never opened its audio", () => {
    voiceMeshState.isVoiceStarted = false;
    render(<AppVoiceSidebar />);

    expect(leaveVoice).toHaveBeenCalledOnce();
    expect(leaveVoice).toHaveBeenCalledWith({ groupId: 4 }, expect.anything());
  });

  it("keeps a call this browser actually started", () => {
    render(<AppVoiceSidebar />);

    expect(leaveVoice).not.toHaveBeenCalled();
  });

  it("reports once per group whether this browser can capture audio", () => {
    vi.spyOn(window.navigator, "userAgent", "get").mockReturnValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)");
    const { rerender } = render(<AppVoiceSidebar />);
    rerender(<AppVoiceSidebar />);

    expect(reportAudioCapability).toHaveBeenCalledOnce();
    expect(reportAudioCapability).toHaveBeenCalledWith({ groupId: 4, data: { canShareAudio: false } });
  });

  it("reports that a desktop Chromium browser can capture audio", () => {
    vi.spyOn(window.navigator, "userAgent", "get").mockReturnValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36");
    render(<AppVoiceSidebar />);

    expect(reportAudioCapability).toHaveBeenCalledWith({ groupId: 4, data: { canShareAudio: true } });
  });

  it("reports capture support from desktop Firefox while the flag is set", () => {
    vi.spyOn(window.navigator, "userAgent", "get").mockReturnValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0");
    render(<AppVoiceSidebar />);

    expect(reportAudioCapability).toHaveBeenCalledWith({ groupId: 4, data: { canShareAudio: true } });
  });
});
