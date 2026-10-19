const LOCK_IN_TONE_FREQUENCIES_HERTZ = [523.25, 783.99];
const LOCK_IN_TONE_SECONDS = 0.09;
const LOCK_IN_TONE_GAP_SECONDS = 0.07;
const LOCK_IN_TONE_VOLUME = 0.18;
const SILENT_VOLUME = 0.0001;
const MILLISECONDS_PER_SECOND = 1_000;
const CONTEXT_CLOSE_DELAY_MILLISECONDS = 500;

interface AudioContextWindow extends Window {
  webkitAudioContext?: typeof AudioContext;
}

// The short two-note chime played when the active player locks their card in.
export function playLockInSound() {
  if (typeof window === "undefined") return;
  const Context = window.AudioContext ?? (window as AudioContextWindow).webkitAudioContext;
  if (!Context) return;
  const context = new Context();
  LOCK_IN_TONE_FREQUENCIES_HERTZ.forEach((frequency, toneIndex) => {
    const startTime = context.currentTime + toneIndex * (LOCK_IN_TONE_SECONDS + LOCK_IN_TONE_GAP_SECONDS);
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.type = "triangle";
    oscillator.frequency.value = frequency;
    gain.gain.setValueAtTime(LOCK_IN_TONE_VOLUME, startTime);
    gain.gain.exponentialRampToValueAtTime(SILENT_VOLUME, startTime + LOCK_IN_TONE_SECONDS);
    oscillator.connect(gain).connect(context.destination);
    oscillator.start(startTime);
    oscillator.stop(startTime + LOCK_IN_TONE_SECONDS);
  });
  const totalSeconds = LOCK_IN_TONE_FREQUENCIES_HERTZ.length * (LOCK_IN_TONE_SECONDS + LOCK_IN_TONE_GAP_SECONDS);
  window.setTimeout(() => void context.close().catch(() => {}), totalSeconds * MILLISECONDS_PER_SECOND + CONTEXT_CLOSE_DELAY_MILLISECONDS);
}
