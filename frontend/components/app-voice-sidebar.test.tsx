import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppVoiceSidebar } from "./app-voice-sidebar";

const joinVoice = vi.fn();
const startMicrophone = vi.fn(async () => true);
const toggleMute = vi.fn();
const toggleDeafen = vi.fn();
let inVoice = true;
let currentPathname = "/groups/4";

vi.mock("next/navigation", () => ({
  usePathname: () => currentPathname,
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetActiveMembershipQueryKey: () => ["active-membership"],
  getGetGroupQueryKey: (groupId: number) => ["group", groupId],
  useGetActiveMembership: () => ({
    data: { id: 4, members: [{ id: 1, userId: 11, displayName: "Alex", isInVoice: inVoice }] },
  }),
  useJoinVoice: () => ({ mutate: joinVoice, isPending: false }),
  useLeaveVoice: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  useGetActiveSessionForGroup: () => ({ data: undefined }),
  useGetSession: () => ({ data: undefined }),
}));

vi.mock("@/hooks/generated/user-management/user-management", () => ({
  useGetCurrentUser: () => ({ data: { id: 11 } }),
}));

vi.mock("@/hooks/use-game-session-realtime", () => ({
  useGameSessionRealtime: () => undefined,
}));

vi.mock("@/hooks/use-voice-mesh", () => ({
  shouldCutoffAudioStream: () => false,
  useVoiceMesh: () => ({
    startMicrophone,
    stopMicrophone: vi.fn(),
    toggleMute,
    toggleDeafen,
    isMuted: false,
    isDeafened: false,
    microphoneError: false,
    tabAudioError: false,
  }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({
    invalidateQueries: vi.fn(),
  }),
}));

describe("AppVoiceSidebar", () => {
  beforeEach(() => {
    inVoice = true;
    currentPathname = "/groups/4";
    window.localStorage.clear();
    joinVoice.mockClear();
    startMicrophone.mockClear();
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
    inVoice = false;
    render(<AppVoiceSidebar />);

    fireEvent.click(screen.getByRole("button", { name: "Join call" }));

    await waitFor(() => expect(startMicrophone).toHaveBeenCalledOnce());
    expect(joinVoice).toHaveBeenCalledWith({ groupId: 4 }, expect.anything());
  });

  it("hides the sidebar outside a call except on the lobby page", () => {
    inVoice = false;
    currentPathname = "/playlists";
    render(<AppVoiceSidebar />);

    expect(screen.queryByRole("complementary", { name: "Voice sidebar" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Join call" })).toBeNull();
  });

  it("shows only the slim rail with no collapse control outside a call", () => {
    inVoice = false;
    render(<AppVoiceSidebar />);

    expect(screen.getByRole("button", { name: "Join call" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /ollapse|Expand/ })).toBeNull();
  });

  it("keeps the full sidebar everywhere while in a call", () => {
    inVoice = true;
    currentPathname = "/playlists";
    render(<AppVoiceSidebar />);

    expect(screen.getByRole("button", { name: "Leave voice" })).toBeVisible();
    expect(screen.queryByRole("button", { name: /ollapse|Expand/ })).toBeNull();
  });
});
