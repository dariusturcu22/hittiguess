"use client";

import type { ReactNode } from "react";
import { Check, Crown, ExternalLink, MonitorUp, X } from "lucide-react";

import { PlayerAvatar } from "@/components/gameplay/game-pieces";
import type { AudioShareTest } from "@/hooks/use-audio-share-test";
import {
  AUDIO_METER_BAR_COUNT,
  BROWSER_FIX_MESSAGE,
  CANNOT_SHARE_STATUS,
  FAILED_STATUS,
  meterFill,
  NOT_SHARED_STATUS,
  OK_STATUS,
  TESTING_STATUS,
  type AudioShareStatus,
} from "@/lib/audio-check";
import { YOUTUBE_LINK_OUT_REL, YOUTUBE_LINK_OUT_TARGET } from "@/lib/youtube-link-out";

const YOUTUBE_HOME_URL = "https://www.youtube.com/";
// Bar heights in pixels, a fixed wave shape the level colors in from the left.
const METER_BAR_HEIGHTS = [10, 25, 34, 32, 21, 18, 30, 34, 27, 14, 24, 36, 30, 16, 22, 34, 29, 20, 33, 26, 12, 28, 36, 24, 18, 31, 22, 14, 30, 36, 25, 16];

export function AudioCheckHeader({ title, subtitle }: { title: string; subtitle: ReactNode }) {
  return <header className="text-center">
    <p className="mb-3.5 text-[12px] font-bold uppercase tracking-[3px] text-muted-foreground/70">Before the game starts</p>
    <h1 className="font-display text-[28px] leading-tight text-foreground [text-shadow:3px_3px_0_var(--text-shadow-on-page)] sm:text-[44px]">{title}</h1>
    <p className="mx-auto mt-3.5 max-w-[600px] text-[15px] leading-normal text-muted-foreground sm:text-base">{subtitle}</p>
  </header>;
}

export function AudioCheckCard({ children, className = "px-4 py-1 sm:px-7" }: { children: ReactNode; className?: string }) {
  return <section className={`mx-auto mt-7 w-full max-w-[680px] rounded-2xl border-[3px] border-border bg-card shadow-[6px_6px_0_var(--shadow-color)] ${className}`}>{children}</section>;
}

function Spinner() {
  return <span className="size-[18px] shrink-0 animate-spin rounded-full border-[3px] border-border border-t-blue" aria-hidden="true" />;
}

function StatusIcon({ status }: { status: AudioShareStatus }) {
  if (status === OK_STATUS) return <span className="flex size-[26px] shrink-0 items-center justify-center rounded-full bg-green"><Check className="size-4 text-green-foreground" strokeWidth={3.2} /></span>;
  if (status === TESTING_STATUS) return <Spinner />;
  if (status === FAILED_STATUS) return <span className="flex size-[26px] shrink-0 items-center justify-center rounded-full bg-warning font-display text-[15px] text-warning-foreground">!</span>;
  if (status === CANNOT_SHARE_STATUS) return <span className="flex size-[26px] shrink-0 items-center justify-center rounded-full bg-destructive"><X className="size-3.5 text-destructive-foreground" strokeWidth={3.4} /></span>;
  return <span className="size-[22px] shrink-0 rounded-full border-2 border-dashed border-muted-foreground/70" aria-hidden="true" />;
}

const STATUS_LABELS: Record<AudioShareStatus, string> = {
  [OK_STATUS]: "Shared, audio OK",
  [TESTING_STATUS]: "Testing audio...",
  [NOT_SHARED_STATUS]: "Waiting to share",
  [FAILED_STATUS]: "No audio detected",
  [CANNOT_SHARE_STATUS]: "Can't share audio in this browser",
};

const STATUS_TEXT_CLASSES: Record<AudioShareStatus, string> = {
  [OK_STATUS]: "text-green",
  [TESTING_STATUS]: "text-blue",
  [NOT_SHARED_STATUS]: "text-muted-foreground",
  [FAILED_STATUS]: "text-warning",
  [CANNOT_SHARE_STATUS]: "text-destructive",
};

export function AudioStatusBadge({ status }: { status: AudioShareStatus }) {
  return <div className="flex items-center justify-start gap-2.5 sm:min-w-[250px] sm:justify-end">
    <span className={`text-[13px] font-semibold sm:text-right sm:text-sm ${STATUS_TEXT_CLASSES[status]}`}>{STATUS_LABELS[status]}</span>
    <StatusIcon status={status} />
  </div>;
}

export function NoSoundHelp() {
  return <div className="mt-3 rounded-xl border-2 border-warning/40 bg-warning/[0.08] px-4 py-3 text-[13px] leading-normal text-muted-foreground sm:ml-[54px]">
    <p className="mb-1.5 font-bold text-warning">The share has no sound</p>
    <p>In the share window, pick <b className="text-foreground">Entire screen</b> and tick <b className="text-foreground">Share system audio</b> at the bottom left. Tab and window shares do not carry sound.</p>
  </div>;
}

export interface AudioCheckRowProps {
  name: string;
  colorIndex: number;
  status: AudioShareStatus;
  isAdmin?: boolean;
  isYou?: boolean;
  detail?: string;
  onShareAgain?: () => void;
  isLast?: boolean;
}

export function AudioCheckRow({ name, colorIndex, status, isAdmin = false, isYou = false, detail, onShareAgain, isLast = false }: AudioCheckRowProps) {
  const fixDetail = status === CANNOT_SHARE_STATUS ? (isYou && detail ? `${detail}. ${BROWSER_FIX_MESSAGE}` : BROWSER_FIX_MESSAGE) : detail;
  return <li className={`py-[11px] ${isLast ? "" : "border-b-2 border-border"}`} data-audio-status={status}>
    <div className="flex flex-wrap items-center gap-x-3.5 gap-y-1.5 sm:flex-nowrap">
      <PlayerAvatar name={name} colorIndex={colorIndex} className="size-10 text-[15px]" />
      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 items-center gap-2 text-base font-semibold text-card-foreground">
          <span className="truncate">{name}</span>
          {isAdmin ? <Crown className="size-3.5 shrink-0 fill-warning text-warning" aria-label="Host" /> : null}
          {isYou ? <span className="shrink-0 rounded-full bg-secondary px-2 py-0.5 text-[10px] font-bold uppercase tracking-[1px] text-muted-foreground">You</span> : null}
        </div>
        {fixDetail ? <p className="mt-0.5 text-xs text-muted-foreground">{fixDetail}</p> : null}
      </div>
      {status === FAILED_STATUS && onShareAgain ? <button type="button" onClick={onShareAgain} className="shrink-0 whitespace-nowrap rounded-full border-2 border-muted-foreground/70 bg-secondary px-3.5 py-1.5 text-[13px] font-semibold text-card-foreground transition-colors hover:border-primary">Share again</button> : null}
      <div className="flex basis-full pl-[54px] sm:basis-auto sm:pl-0"><AudioStatusBadge status={status} /></div>
    </div>
    {status === FAILED_STATUS ? <NoSoundHelp /> : null}
  </li>;
}

export function AudioMeter({ level, isLive }: { level: number; isLive: boolean }) {
  const filledBars = isLive ? Math.round(meterFill(level) * AUDIO_METER_BAR_COUNT) : 0;
  return <div className="flex h-11 min-w-0 items-center gap-[3px] overflow-hidden sm:gap-[5px]" role="meter" aria-label="Shared audio level" aria-valuemin={0} aria-valuemax={AUDIO_METER_BAR_COUNT} aria-valuenow={filledBars}>
    {METER_BAR_HEIGHTS.map((height, barIndex) => <span key={barIndex} className={`w-1.5 shrink-0 rounded-full transition-colors duration-75 sm:w-2 ${barIndex < filledBars ? "bg-green" : "bg-secondary"}`} style={{ height }} />)}
  </div>;
}

function StepNumber({ step, isDone = false, isMuted = false }: { step: number; isDone?: boolean; isMuted?: boolean }) {
  if (isDone) return <span className="flex size-[26px] shrink-0 items-center justify-center rounded-full bg-green"><Check className="size-4 text-green-foreground" strokeWidth={3.2} /></span>;
  return <span className={`flex size-[26px] shrink-0 items-center justify-center rounded-full font-display text-[13px] ${isMuted ? "bg-secondary text-muted-foreground" : "bg-primary text-primary-foreground"}`}>{step}</span>;
}

// The player's own share in two steps: share the screen with system audio, then play any
// sound until the meter has stayed up long enough.
export function OwnShareCard({ shareTest }: { shareTest: Pick<AudioShareTest, "isCapturing" | "level" | "hasPassed" | "isSilent" | "shareErrorMessage" | "shareErrorKind" | "share" | "changeSharedScreen"> }) {
  if (!shareTest.isCapturing) {
    return <AudioCheckCard className="p-5 sm:p-7">
      <div className="flex items-center gap-3"><StepNumber step={1} /><h2 className="text-[17px] font-bold text-card-foreground">Share your audio</h2></div>
      <p className="mt-4 text-sm leading-normal text-muted-foreground sm:ml-[38px]">A window from your browser opens. Pick <b className="text-foreground">Entire screen</b> and tick <b className="text-foreground">Share system audio</b>.</p>
      <div className="mt-5 sm:ml-[38px]">
        <button type="button" onClick={() => { void shareTest.share(); }} className="inline-flex w-full items-center justify-center gap-2.5 rounded-full bg-primary px-6 py-[18px] font-display text-base text-primary-foreground shadow-[4px_4px_0_var(--shadow-color)] transition-transform hover:-translate-y-0.5"><MonitorUp className="size-5" strokeWidth={2.4} />Share your audio</button>
        {shareTest.shareErrorKind === "no-audio-track" ? <NoSoundHelpInCard /> : shareTest.shareErrorMessage ? <p role="alert" className="mt-3 text-[13px] font-semibold text-warning">{shareTest.shareErrorMessage}</p> : null}
      </div>
      <div className="my-7 h-0.5 bg-border" />
      <div className="flex items-center gap-3"><StepNumber step={2} isMuted /><h2 className="text-[17px] font-bold text-muted-foreground/70">Test the audio</h2></div>
      <div className="mt-3.5 sm:ml-[38px]"><AudioMeter level={0} isLive={false} /></div>
    </AudioCheckCard>;
  }
  return <AudioCheckCard className="p-5 sm:p-7">
    <div className="flex items-center gap-3"><StepNumber step={1} isDone /><h2 className="text-[17px] font-bold text-card-foreground">Screen shared</h2></div>
    <div className="mt-2.5 flex flex-col gap-1.5 text-[13px] text-muted-foreground sm:ml-[38px] sm:flex-row sm:items-center sm:gap-3.5">
      <span className="flex-1">Entire screen with system audio. Keep this share running for the whole game.</span>
      <button type="button" onClick={() => { void shareTest.changeSharedScreen(); }} className="self-start font-semibold text-blue hover:underline sm:self-auto">Change shared screen</button>
    </div>
    <div className="my-[22px] h-0.5 bg-border" />
    <div className="flex items-center gap-3"><StepNumber step={2} isDone={shareTest.hasPassed} /><h2 className="text-[17px] font-bold text-card-foreground">Play anything to test the audio</h2></div>
    <p className="mt-3.5 text-sm leading-normal text-muted-foreground sm:ml-[38px]">Play any sound on your computer, for example a song on YouTube. The meter moves when the share carries sound. Sound from this tab is not part of the share, so it does not register.</p>
    <div className="mt-4 flex items-center gap-4 sm:ml-[38px]">
      <AudioMeter level={shareTest.level} isLive />
      {shareTest.hasPassed
        ? <span className="flex shrink-0 items-center gap-1.5 text-[13px] font-semibold text-green"><Check className="size-4" strokeWidth={3} />Audio OK</span>
        : <span className="flex shrink-0 items-center gap-2 text-[13px] font-semibold text-blue"><Spinner />Listening...</span>}
    </div>
    <div className="mt-4 sm:ml-[38px]">
      <a href={YOUTUBE_HOME_URL} target={YOUTUBE_LINK_OUT_TARGET} rel={YOUTUBE_LINK_OUT_REL} className="inline-flex items-center gap-2 rounded-full border-2 border-border bg-card px-[18px] py-2.5 text-[13px] font-semibold text-card-foreground transition-colors hover:border-primary"><ExternalLink className="size-3.5" />Open YouTube</a>
    </div>
    {shareTest.isSilent
      ? <div className="sm:ml-[38px]"><NoSoundHelpInCard /></div>
      : <p className="mt-5 rounded-xl border-2 border-dashed border-border px-4 py-3 text-[13px] leading-normal text-muted-foreground/80 sm:ml-[38px]">Once the meter has stayed up for a moment, this step turns into a check mark and your row shows <span className="font-semibold text-green">Shared, audio OK</span>.</p>}
  </AudioCheckCard>;
}

function NoSoundHelpInCard() {
  return <div role="alert" className="mt-4 rounded-xl border-2 border-warning/40 bg-warning/[0.08] px-4 py-3 text-[13px] leading-normal text-muted-foreground">
    <p className="mb-1.5 font-bold text-warning">The share has no sound</p>
    <p>In the share window, pick <b className="text-foreground">Entire screen</b> and tick <b className="text-foreground">Share system audio</b> at the bottom left. Tab and window shares do not carry sound.</p>
  </div>;
}
