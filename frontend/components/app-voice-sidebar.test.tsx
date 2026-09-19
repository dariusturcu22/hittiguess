import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppVoiceSidebar } from "./app-voice-sidebar";

const joinVoice = vi.fn();
const startMicrophone = vi.fn(async () => true);
const toggleMute = vi.fn();
const toggleDeafen = vi.fn();
let inVoice = true;

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

  it("labels the join action and starts the microphone before joining", async () => {
    inVoice = false;
    render(<AppVoiceSidebar />);

    fireEvent.click(screen.getByRole("button", { name: "Join call" }));

    await waitFor(() => expect(startMicrophone).toHaveBeenCalledOnce());
    expect(joinVoice).toHaveBeenCalledWith({ groupId: 4 }, expect.anything());
  });
});
