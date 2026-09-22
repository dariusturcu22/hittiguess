"use client";

import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/shadcn/button";
import { useAudioDevices } from "@/hooks/use-audio-devices";

const TEST_TONE_FREQUENCY_HERTZ = 440;
const TEST_TONE_GAIN = 0.2;
const TEST_TONE_DURATION_MILLISECONDS = 1200;
const MICROPHONE_METER_SMOOTHING = 128;

interface AudioContextWindow extends Window {
  webkitAudioContext?: typeof AudioContext;
}

function audioContextConstructor(): typeof AudioContext | undefined {
  if (typeof window === "undefined") {
    return undefined;
  }
  return window.AudioContext ?? (window as AudioContextWindow).webkitAudioContext;
}

export function VoiceSettingsPopup({ onClose }: { onClose: () => void }) {
  const {
    microphones,
    speakers,
    microphoneDeviceId,
    speakerDeviceId,
    selectMicrophone,
    selectSpeaker,
    refreshDevices,
  } = useAudioDevices();
  const [microphoneLevel, setMicrophoneLevel] = useState(0);
  const [isTestingMicrophone, setIsTestingMicrophone] = useState(false);
  const microphoneTestReference = useRef<{
    stream?: MediaStream;
    animationFrame?: number;
    context?: AudioContext;
  }>({});

  function stopMicrophoneTest() {
    const testState = microphoneTestReference.current;
    if (testState.animationFrame !== undefined) {
      cancelAnimationFrame(testState.animationFrame);
    }
    testState.stream?.getTracks().forEach((track) => track.stop());
    void testState.context?.close().catch(() => {});
    microphoneTestReference.current = {};
    setMicrophoneLevel(0);
    setIsTestingMicrophone(false);
  }

  useEffect(() => {
    return () => {
      const testState = microphoneTestReference.current;
      if (testState.animationFrame !== undefined) {
        cancelAnimationFrame(testState.animationFrame);
      }
      testState.stream?.getTracks().forEach((track) => track.stop());
      void testState.context?.close().catch(() => {});
    };
  }, []);

  async function testMicrophone() {
    if (isTestingMicrophone) {
      stopMicrophoneTest();
      return;
    }
    const Context = audioContextConstructor();
    if (!Context || !navigator.mediaDevices?.getUserMedia) {
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: microphoneDeviceId ? { deviceId: { exact: microphoneDeviceId } } : true,
      });
      const context = new Context();
      const source = context.createMediaStreamSource(stream);
      const analyser = context.createAnalyser();
      analyser.fftSize = 256;
      source.connect(analyser);
      const levels = new Uint8Array(analyser.frequencyBinCount);
      const readLevel = () => {
        analyser.getByteFrequencyData(levels);
        const average = levels.reduce((sum, level) => sum + level, 0) / levels.length;
        setMicrophoneLevel(Math.min(1, average / MICROPHONE_METER_SMOOTHING));
        microphoneTestReference.current.animationFrame = requestAnimationFrame(readLevel);
      };
      microphoneTestReference.current = { stream, context };
      setIsTestingMicrophone(true);
      readLevel();
    } catch {
      stopMicrophoneTest();
    }
  }

  async function testSpeaker() {
    const Context = audioContextConstructor();
    if (!Context) {
      return;
    }
    const context = new Context();
    const oscillator = context.createOscillator();
    oscillator.frequency.value = TEST_TONE_FREQUENCY_HERTZ;
    const gain = context.createGain();
    gain.gain.value = TEST_TONE_GAIN;
    oscillator.connect(gain);
    const destination = context.createMediaStreamDestination();
    gain.connect(destination);
    const audio = new Audio();
    audio.srcObject = destination.stream;
    const sinkableAudio = audio as HTMLAudioElement & { setSinkId?: (deviceId: string) => Promise<void> };
    if (speakerDeviceId && typeof sinkableAudio.setSinkId === "function") {
      try {
        await sinkableAudio.setSinkId(speakerDeviceId);
      } catch {
        // Falls through to the default output below.
      }
    }
    try {
      await audio.play();
      oscillator.start();
      window.setTimeout(() => {
        oscillator.stop();
        void context.close().catch(() => {});
      }, TEST_TONE_DURATION_MILLISECONDS);
    } catch {
      oscillator.stop();
      void context.close().catch(() => {});
    }
  }

  return (
    <section
      aria-label="Voice settings"
      className="absolute bottom-24 right-full z-30 mr-3 w-64 rounded-2xl border-[3px] border-border-strong bg-card p-5 shadow-lg"
    >
      <div className="mb-4 flex items-center justify-between">
        <h2 className="font-display text-sm text-card-foreground">Voice settings</h2>
        <button
          type="button"
          onClick={() => {
            stopMicrophoneTest();
            onClose();
          }}
          className="text-muted-foreground hover:text-card-foreground"
        >
          Close
        </button>
      </div>
      <div className="space-y-4">
        <label className="flex flex-col gap-1.5 text-[13px] text-muted-foreground">
          Microphone
          <span className="flex gap-2">
            <select
              value={microphoneDeviceId ?? ""}
              onChange={(event) => {
                selectMicrophone(event.target.value || undefined);
                void refreshDevices();
              }}
              className="min-w-0 flex-1 rounded-full border-2 border-border bg-background px-3 py-1.5 text-sm font-semibold text-foreground outline-none"
            >
              <option value="">System default</option>
              {microphones.map((microphone) => (
                <option key={microphone.deviceId} value={microphone.deviceId}>
                  {microphone.label}
                </option>
              ))}
            </select>
            <Button type="button" variant="outline" size="sm" onClick={testMicrophone}>
              {isTestingMicrophone ? "Stop" : "Test"}
            </Button>
          </span>
        </label>
        {isTestingMicrophone ? (
          <div
            className="h-2 overflow-hidden rounded-full bg-secondary"
            role="meter"
            aria-label="Microphone level"
            aria-valuenow={Math.round(microphoneLevel * 100)}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <div
              className="h-full rounded-full bg-green transition-[width]"
              style={{ width: `${Math.round(microphoneLevel * 100)}%` }}
            />
          </div>
        ) : null}
        <label className="flex flex-col gap-1.5 text-[13px] text-muted-foreground">
          Speaker
          <span className="flex gap-2">
            <select
              value={speakerDeviceId ?? ""}
              onChange={(event) => selectSpeaker(event.target.value || undefined)}
              className="min-w-0 flex-1 rounded-full border-2 border-border bg-background px-3 py-1.5 text-sm font-semibold text-foreground outline-none"
            >
              <option value="">System default</option>
              {speakers.map((speaker) => (
                <option key={speaker.deviceId} value={speaker.deviceId}>
                  {speaker.label}
                </option>
              ))}
            </select>
            <Button type="button" variant="outline" size="sm" onClick={testSpeaker}>
              Test
            </Button>
          </span>
        </label>
      </div>
    </section>
  );
}
