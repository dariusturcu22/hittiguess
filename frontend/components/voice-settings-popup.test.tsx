import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { VoiceSettingsPopup } from "./voice-settings-popup";

const setMicrophoneVolume = vi.fn();
const setSpeakerVolume = vi.fn();

vi.mock("@/hooks/use-audio-devices", () => ({
  MAX_MICROPHONE_VOLUME_PERCENT: 200,
  MAX_SPEAKER_VOLUME_PERCENT: 100,
  useAudioDevices: () => ({
    microphoneVolume: 100,
    speakerVolume: 60,
    setMicrophoneVolume,
    setSpeakerVolume,
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

  it("has a volume slider for the microphone and the speaker", () => {
    render(<VoiceSettingsPopup onClose={vi.fn()} />);

    const microphoneSlider = screen.getByLabelText(/Microphone volume/);
    const speakerSlider = screen.getByLabelText(/Speaker volume/);
    expect(microphoneSlider).toHaveAttribute("max", "200");
    expect(speakerSlider).toHaveAttribute("max", "100");
    expect(speakerSlider).toHaveValue("60");

    fireEvent.change(speakerSlider, { target: { value: "25" } });
    fireEvent.change(microphoneSlider, { target: { value: "150" } });

    expect(setSpeakerVolume).toHaveBeenCalledWith(25);
    expect(setMicrophoneVolume).toHaveBeenCalledWith(150);
  });
});
