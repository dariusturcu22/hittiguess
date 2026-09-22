import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { VoiceSettingsPopup } from "./voice-settings-popup";

vi.mock("@/hooks/use-audio-devices", () => ({
  useAudioDevices: () => ({
    microphones: [{ deviceId: "mic-1", label: "Built-in mic" }],
    speakers: [{ deviceId: "spk-1", label: "Speakers" }],
    microphoneDeviceId: undefined,
    speakerDeviceId: undefined,
    selectMicrophone: vi.fn(),
    selectSpeaker: vi.fn(),
    refreshDevices: vi.fn(async () => {}),
  }),
}));

describe("VoiceSettingsPopup", () => {
  it("offers device selection and test actions", () => {
    const onClose = vi.fn();
    render(<VoiceSettingsPopup onClose={onClose} />);

    expect(screen.getByText("Built-in mic")).toBeVisible();
    expect(screen.getByText("Speakers")).toBeVisible();
    expect(screen.getAllByRole("button", { name: "Test" })).toHaveLength(2);

    fireEvent.click(screen.getByRole("button", { name: "Close" }));

    expect(onClose).toHaveBeenCalledOnce();
  });
});
