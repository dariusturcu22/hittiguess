"use client";

import { useState } from "react";
import { Loader2, Play } from "lucide-react";

import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/shadcn/alert-dialog";
import { AudioCheckCard, AudioCheckHeader, AudioCheckRow, OwnShareCard } from "@/components/audio-check/audio-check-parts";
import type { AudioShareTest } from "@/hooks/use-audio-share-test";
import type { GroupDetailDTO } from "@/hooks/models/groupDetailDTO";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import {
  audioCheckView,
  BROWSER_FIX_MESSAGE,
  CANNOT_SHARE_STATUS,
  connectedMembersByJoinTime,
  fixedDjCandidates,
  memberAudioStatus,
  OK_STATUS,
  requiredMembers,
} from "@/lib/audio-check";

const OWN_CARD_SUBTITLE = "The game needs your computer's audio for the turns where you are the DJ. Share it once now, then run a quick test. Keep the share running for the whole game.";
const ROTATING_SUBTITLE = "Everyone in a rotating game takes a turn as DJ, so everyone shares their computer's audio once and keeps it running for the whole game.";
const READY_SUBTITLE = "Every player has a working share. The game is ready to start.";
const NOBODY_SUBTITLE = "The DJ plays songs through their computer's audio, and none of the browsers in this group can share it, so the game cannot start yet.";

export interface AudioCheckScreenProps {
  group: GroupDetailDTO;
  currentMemberId: number | undefined;
  ownBrowserName?: string;
  shareTest: AudioShareTest;
  isStarting?: boolean;
  isBusy?: boolean;
  errorMessage?: string;
  onStart: () => void;
  onSwitchToFixedDj: (memberId: number) => void;
  onBackToLobby: () => void;
}

function playersReadyLabel(readyCount: number, requiredCount: number): string {
  return `${readyCount} of ${requiredCount} ${requiredCount === 1 ? "player" : "players"} ready`;
}

export function AudioCheckScreen({ group, currentMemberId, ownBrowserName, shareTest, isStarting = false, isBusy = false, errorMessage, onStart, onSwitchToFixedDj, onBackToLobby }: AudioCheckScreenProps) {
  const [isFixedDjPickerOpen, setIsFixedDjPickerOpen] = useState(false);
  const view = audioCheckView(group, currentMemberId);
  const members = connectedMembersByJoinTime(group);
  const allMembersByJoinTime = [...(group.members ?? [])].sort((first, second) => Date.parse(first.joinedAt ?? "") - Date.parse(second.joinedAt ?? ""));
  const colorIndexOf = (member: MemberDTO) => Math.max(0, allMembersByJoinTime.findIndex((candidate) => candidate.id === member.id));
  const currentMember = group.members?.find((member) => member.id === currentMemberId);
  const isHost = Boolean(currentMember?.isAdmin);
  const required = requiredMembers(group);
  const readyCount = group.audioCheck?.readyCount ?? 0;
  const canStart = Boolean(group.audioCheck?.canStart);
  const isRotating = group.djMode !== "FIXED";
  const fixedDj = isRotating ? undefined : required.at(0);
  const candidates = fixedDjCandidates(group);
  const showsSwitchToFixed = isHost && isRotating && !canStart && view !== "nobody-can-share";
  const showsBackToLobby = isHost && !canStart;

  function renderRow(member: MemberDTO, isLast: boolean, detail?: string) {
    const isYou = member.id === currentMemberId;
    return <AudioCheckRow
      key={member.id}
      name={member.displayName ?? "Player"}
      colorIndex={colorIndexOf(member)}
      status={memberAudioStatus(member)}
      isAdmin={member.isAdmin}
      isYou={isYou}
      detail={detail ?? (isYou && memberAudioStatus(member) === CANNOT_SHARE_STATUS ? ownBrowserName : undefined)}
      onShareAgain={isYou ? () => { void shareTest.changeSharedScreen(); } : undefined}
      isLast={isLast}
    />;
  }

  let title = "Audio check";
  let subtitle: string = canStart ? READY_SUBTITLE : ROTATING_SUBTITLE;
  let body = <AudioCheckCard><ul>{members.map((member, memberIndex) => renderRow(member, memberIndex === members.length - 1))}</ul></AudioCheckCard>;
  let footnote: string | null = null;

  if (view === "nobody-can-share") {
    title = "Nobody can share audio";
    subtitle = NOBODY_SUBTITLE;
    body = <>
      <AudioCheckCard><ul>{members.map((member, memberIndex) => renderRow(member, memberIndex === members.length - 1))}</ul></AudioCheckCard>
      <AudioCheckCard className="px-5 py-5 sm:px-7 sm:py-6">
        <h2 className="text-base font-bold text-card-foreground">What to do</h2>
        <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{BROWSER_FIX_MESSAGE} Once someone can share, the audio check continues here, and the host can switch to a fixed DJ.</p>
      </AudioCheckCard>
    </>;
  } else if (view === "own-card") {
    subtitle = OWN_CARD_SUBTITLE;
    body = <OwnShareCard shareTest={shareTest} />;
  } else if (view === "fixed" && fixedDj) {
    const djName = fixedDj.displayName ?? "The DJ";
    const isDjReady = memberAudioStatus(fixedDj) === OK_STATUS;
    if (fixedDj.id === currentMemberId) {
      subtitle = "You are the DJ for this game. Your share works; keep it running for the whole game.";
    } else {
      title = `Waiting for ${djName}`;
      subtitle = `${djName} is the DJ for this game. Only ${djName} shares audio, and keeps the share running for the whole game. Nothing to set up on your side.`;
    }
    body = <AudioCheckCard><ul>{renderRow(fixedDj, true, "DJ")}</ul></AudioCheckCard>;
    footnote = isDjReady ? `${djName} is ready.${isHost ? "" : " The host starts the game."}` : `The host starts the game once ${djName} is ready.`;
  }

  return <main className="h-full min-h-0 overflow-y-auto">
    <div className="flex min-h-full flex-col px-4 pt-10 sm:px-8 sm:pt-14">
      <AudioCheckHeader title={title} subtitle={subtitle} />
      {body}
      {footnote ? <p className="mt-7 text-center text-sm text-muted-foreground/80">{footnote}</p> : null}
      <div className="mt-auto flex flex-col items-center gap-3 pb-10 pt-10 sm:pb-12">
        <div className="flex flex-wrap items-center justify-center gap-4">
          {isHost ? <button
            type="button"
            onClick={onStart}
            disabled={!canStart || isStarting}
            className="inline-flex items-center gap-2.5 rounded-full border-2 border-primary bg-primary px-6 py-[13px] font-display text-[13px] text-primary-foreground shadow-[4px_4px_0_var(--shadow-color)] transition-transform enabled:hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:border-border disabled:bg-card disabled:text-muted-foreground/70 disabled:shadow-none"
          >{isStarting ? <Loader2 className="size-4 animate-spin" /> : <Play className="size-4 fill-current" />}Start game</button> : null}
          {view !== "fixed" ? <span className={`text-sm font-semibold ${canStart ? "text-green" : "text-muted-foreground"}`}>{playersReadyLabel(readyCount, required.length)}</span> : null}
          {showsSwitchToFixed ? <button type="button" onClick={() => setIsFixedDjPickerOpen(true)} disabled={isBusy} className="inline-flex items-center rounded-full border-2 border-border bg-card px-[22px] py-[13px] text-[13px] font-semibold text-card-foreground transition-colors hover:border-primary disabled:opacity-60">Switch to a fixed DJ</button> : null}
          {showsBackToLobby ? <button type="button" onClick={onBackToLobby} disabled={isBusy} className="inline-flex items-center rounded-full border-2 border-border bg-card px-[22px] py-[13px] text-[13px] font-semibold text-card-foreground transition-colors hover:border-primary disabled:opacity-60">Back to lobby</button> : null}
        </div>
        {errorMessage ? <p role="alert" className="max-w-[560px] text-center text-[13px] font-semibold text-destructive">{errorMessage}</p> : null}
      </div>
    </div>
    <AlertDialog open={isFixedDjPickerOpen} onOpenChange={setIsFixedDjPickerOpen}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Switch to a fixed DJ</AlertDialogTitle>
          <AlertDialogDescription>{candidates.length > 0
            ? "One player DJs the whole game and never takes a guessing turn. Pick a player whose share passed the test."
            : "Nobody's share has passed the test yet. A player who can share has to pass it first."}</AlertDialogDescription>
        </AlertDialogHeader>
        {candidates.length > 0 ? <ul className="flex flex-col gap-2">
          {candidates.map((member) => <li key={member.id}>
            <button type="button" onClick={() => { setIsFixedDjPickerOpen(false); if (member.id !== undefined) onSwitchToFixedDj(member.id); }} className="flex w-full items-center gap-3 rounded-2xl border-2 border-border bg-card px-4 py-2.5 text-left text-sm font-semibold text-card-foreground transition-colors hover:border-primary">
              {member.displayName}{member.id === currentMemberId ? " (you)" : ""}
            </button>
          </li>)}
        </ul> : null}
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </main>;
}
