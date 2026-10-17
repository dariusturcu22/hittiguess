import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { VoiceSettingsPopup } from "./voice-settings-popup";

const setMicrophoneVolume = vi.fn();
const setSpeakerVolume = vi.fn();
const setNoiseGateSensitivity = vi.fn();
const setRnnoiseEnabled = vi.fn();
const setSongVolume = vi.fn();

vi.mock("@/hooks/use-audio-devices", () => ({
  MAX_MICROPHONE_VOLUME_PERCENT: 200,
  MAX_SPEAKER_VOLUME_PERCENT: 100,
  MAX_SONG_VOLUME_PERCENT: 100,
  MIN_NOISE_GATE_SENSITIVITY: 0,
  MAX_NOISE_GATE_SENSITIVITY: 100,
  useAudioDevices: () => ({
    microphoneVolume: 100,
    speakerVolume: 60,
    noiseGateSensitivity: 50,
    isRnnoiseEnabled: true,
    songVolume: 30,
    setMicrophoneVolume,
    setSpeakerVolume,
    setNoiseGateSensitivity,
    setRnnoiseEnabled,
    setSongVolume,
    microphones: [{ deviceId: "mic-1", label: "Built-in mic" }],
    speakers: [{ deviceId: "spk-1", label: "Speakers" }],
    microphoneDeviceId: undefined,
    speakerDeviceId: undefined,
    selectMicrophone: vi.fn(),
    selectSpeaker: vi.fn(),
    refreshDevices: vi.fn(async () => {}),
  }),
}));

function stubUserAgent(userAgent: string) {
  return vi.spyOn(window.navigator, "userAgent", "get").mockReturnValue(userAgent);
}

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

  it("has a volume slider for the microphone, the speaker, and the song", () => {
    render(<VoiceSettingsPopup onClose={vi.fn()} />);

    const microphoneSlider = screen.getByLabelText(/Microphone volume/);
    const speakerSlider = screen.getByLabelText(/Speaker volume/);
    const songSlider = screen.getByLabelText(/Song volume/);
    expect(microphoneSlider).toHaveAttribute("max", "200");
    expect(speakerSlider).toHaveAttribute("max", "100");
    expect(speakerSlider).toHaveValue("60");
    expect(songSlider).toHaveAttribute("max", "100");
    expect(songSlider).toHaveValue("30");

    fireEvent.change(speakerSlider, { target: { value: "25" } });
    fireEvent.change(microphoneSlider, { target: { value: "150" } });
    fireEvent.change(songSlider, { target: { value: "70" } });

    expect(setSpeakerVolume).toHaveBeenCalledWith(25);
    expect(setMicrophoneVolume).toHaveBeenCalledWith(150);
    expect(setSongVolume).toHaveBeenCalledWith(70);
  });

  it("exposes a noise gate sensitivity slider", () => {
    render(<VoiceSettingsPopup onClose={vi.fn()} />);

    const sensitivitySlider = screen.getByLabelText(/Noise gate sensitivity/);
    expect(sensitivitySlider).toHaveAttribute("min", "0");
    expect(sensitivitySlider).toHaveAttribute("max", "100");
    expect(sensitivitySlider).toHaveValue("50");

    fireEvent.change(sensitivitySlider, { target: { value: "80" } });

    expect(setNoiseGateSensitivity).toHaveBeenCalledWith(80);
  });

  it("has an RNNoise toggle that is on by default", () => {
    render(<VoiceSettingsPopup onClose={vi.fn()} />);

    const rnnoiseToggle = screen.getByLabelText(/Noise reduction/);
    expect(rnnoiseToggle).toBeChecked();

    fireEvent.click(rnnoiseToggle);

    expect(setRnnoiseEnabled).toHaveBeenCalledWith(false);
  });

  it("shows the Windows call-volume hint only on Windows", () => {
    const windowsUserAgent = stubUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36");
    const { unmount } = render(<VoiceSettingsPopup onClose={vi.fn()} />);

    const windowsHint = screen.getByText(/Windows lowers other sounds/);
    expect(windowsHint).toBeVisible();
    expect(windowsHint.closest("details")).not.toBeNull();
    expect(screen.getByText(/Communications tab/)).toBeInTheDocument();
    unmount();
    windowsUserAgent.mockRestore();

    const macUserAgent = stubUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15");
    render(<VoiceSettingsPopup onClose={vi.fn()} />);

    expect(screen.queryByText(/Windows lowers other sounds/)).toBeNull();
    macUserAgent.mockRestore();
  });
});
