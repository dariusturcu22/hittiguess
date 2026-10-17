import * as Sentry from "@sentry/nextjs";

import {
  AUDIO_SHARE_OUTCOME_MESSAGE,
  registerAudioShareOutcomeRecorder,
  type AudioShareOutcome,
} from "@/lib/audio-share-outcome";

const SHARE_BREADCRUMB_CATEGORY = "audio.share";

const sentryDsn = process.env.NEXT_PUBLIC_SENTRY_DSN;

// The DSN is absent in local development, and initialising with an empty DSN would
// report to nothing.
if (sentryDsn) {
  Sentry.init({
    dsn: sentryDsn,
    environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT ?? process.env.NODE_ENV,
  });

  registerAudioShareOutcomeRecorder((outcome: AudioShareOutcome) => {
    Sentry.addBreadcrumb({
      category: SHARE_BREADCRUMB_CATEGORY,
      message: AUDIO_SHARE_OUTCOME_MESSAGE,
      level: "info",
      data: { ...outcome },
    });
  });
}
