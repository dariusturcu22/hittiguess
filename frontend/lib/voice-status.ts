export const VOICE_STATUS_CHANNEL = "voice-status";
const OPEN_CHANNEL_STATE = "open";
const MAXIMUM_STATUS_MESSAGE_LENGTH = 256;
const SPEAKING_SAMPLE_SIZE = 512;
const SPEAKING_LEVEL_THRESHOLD = 0.025;
const SPEAKING_HOLD_MILLISECONDS = 300;
const SPEAKING_POLL_MILLISECONDS = 100;

export interface VoiceStatus {
  isMuted: boolean;
  isDeafened: boolean;
  isSpeaking: boolean;
}

export function sendVoiceStatus(channel: RTCDataChannel, status: VoiceStatus) {
  if (channel.readyState === OPEN_CHANNEL_STATE) channel.send(JSON.stringify(status));
}

export function parseVoiceStatus(message: unknown): VoiceStatus | undefined {
  if (typeof message !== "string" || message.length > MAXIMUM_STATUS_MESSAGE_LENGTH) return undefined;
  try {
    const status = JSON.parse(message) as Partial<VoiceStatus> | null;
    if (!status || typeof status.isMuted !== "boolean" || typeof status.isDeafened !== "boolean" || typeof status.isSpeaking !== "boolean") return undefined;
    return { isMuted: status.isMuted, isDeafened: status.isDeafened, isSpeaking: status.isSpeaking && !status.isMuted };
  } catch {
    return undefined;
  }
}

export function observeMicrophoneSpeaking(context: AudioContext, stream: MediaStream, onSpeaking: (speaking: boolean) => void): () => void {
  const source = context.createMediaStreamSource(stream);
  const analyser = context.createAnalyser();
  analyser.fftSize = SPEAKING_SAMPLE_SIZE;
  source.connect(analyser);
  const samples = new Float32Array(analyser.fftSize);
  let lastSpeechAt = Number.NEGATIVE_INFINITY;
  let previousSpeaking = false;
  const interval = window.setInterval(() => {
    analyser.getFloatTimeDomainData(samples);
    const energy = samples.reduce((total, sample) => total + sample * sample, 0);
    if (Math.sqrt(energy / samples.length) >= SPEAKING_LEVEL_THRESHOLD) lastSpeechAt = Date.now();
    const isSpeaking = Date.now() - lastSpeechAt < SPEAKING_HOLD_MILLISECONDS;
    if (isSpeaking !== previousSpeaking) {
      previousSpeaking = isSpeaking;
      onSpeaking(isSpeaking);
    }
  }, SPEAKING_POLL_MILLISECONDS);
  return () => {
    window.clearInterval(interval);
    source.disconnect();
    analyser.disconnect();
    onSpeaking(false);
  };
}
