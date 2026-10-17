// A share attempt's outcome is recorded so the results from different browsers can be
// compared. The recorder is registered by instrumentation-client.ts, which keeps the
// Sentry SDK out of the modules the unit tests import.
export interface AudioShareOutcome {
  browser: string;
  platform: string;
  displaySurface: string | null;
  audioTrackCount: number;
}

export const AUDIO_SHARE_OUTCOME_MESSAGE = "Song audio share attempt";

type AudioShareOutcomeRecorder = (outcome: AudioShareOutcome) => void;

let recorder: AudioShareOutcomeRecorder | null = null;

export function registerAudioShareOutcomeRecorder(nextRecorder: AudioShareOutcomeRecorder | null) {
  recorder = nextRecorder;
}

export function recordAudioShareOutcome(outcome: AudioShareOutcome) {
  console.info(AUDIO_SHARE_OUTCOME_MESSAGE, outcome);
  recorder?.(outcome);
}
