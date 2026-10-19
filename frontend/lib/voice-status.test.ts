import { afterEach, describe, expect, it, vi } from "vitest";
import { observeMicrophoneSpeaking, parseVoiceStatus, sendVoiceStatus } from "./voice-status";

afterEach(() => vi.useRealTimers());

describe("voice status", () => {
  it("rejects malformed data and never shows a muted member as speaking", () => {
    expect(parseVoiceStatus("invalid")).toBeUndefined();
    expect(parseVoiceStatus("null")).toBeUndefined();
    expect(parseVoiceStatus('{"isMuted":"true"}')).toBeUndefined();
    expect(parseVoiceStatus(JSON.stringify({ isMuted: true, isDeafened: false, isSpeaking: true }))).toEqual({ isMuted: true, isDeafened: false, isSpeaking: false });
  });

  it("sends the full current status only through open channels", () => {
    const send = vi.fn();
    const status = { isMuted: true, isDeafened: true, isSpeaking: false };
    sendVoiceStatus({ readyState: "connecting", send } as unknown as RTCDataChannel, status);
    expect(send).not.toHaveBeenCalled();
    sendVoiceStatus({ readyState: "open", send } as unknown as RTCDataChannel, status);
    const [firstCall] = send.mock.calls;
    const [message] = firstCall ?? [];
    expect(parseVoiceStatus(message)).toEqual(status);
  });

  it("detects microphone speech, holds brief gaps, and stops polling on cleanup", () => {
    vi.useFakeTimers();
    const NOW_MILLISECONDS = 1_000;
    const POLL_MILLISECONDS = 100;
    const HOLD_MILLISECONDS = 300;
    const VOICE_AMPLITUDE = 0.1;
    vi.setSystemTime(NOW_MILLISECONDS);
    let amplitude = 0;
    const source = { connect: vi.fn(), disconnect: vi.fn() };
    const analyser = { fftSize: 0, getFloatTimeDomainData: vi.fn((samples: Float32Array) => samples.fill(amplitude)), disconnect: vi.fn() };
    const context = { createMediaStreamSource: vi.fn(() => source), createAnalyser: () => analyser } as unknown as AudioContext;
    const onSpeaking = vi.fn();
    const stop = observeMicrophoneSpeaking(context, {} as MediaStream, onSpeaking);
    vi.advanceTimersByTime(POLL_MILLISECONDS);
    expect(onSpeaking).not.toHaveBeenCalled();
    amplitude = VOICE_AMPLITUDE;
    vi.advanceTimersByTime(POLL_MILLISECONDS);
    expect(onSpeaking).toHaveBeenLastCalledWith(true);
    amplitude = 0;
    vi.advanceTimersByTime(POLL_MILLISECONDS);
    expect(onSpeaking).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(HOLD_MILLISECONDS);
    expect(onSpeaking).toHaveBeenLastCalledWith(false);
    stop();
    analyser.getFloatTimeDomainData.mockClear();
    vi.advanceTimersByTime(POLL_MILLISECONDS);
    expect(analyser.getFloatTimeDomainData).not.toHaveBeenCalled();
    expect(source.disconnect).toHaveBeenCalled();
  });
});
