"use client";

import * as React from "react";
import type { AxiosError } from "axios";
import { Loader2, LogIn, Plus } from "lucide-react";

const JOIN_CODE_LENGTH = 4;
const NON_LETTER_PATTERN = /[^A-Z]/g;
const NOT_FOUND_STATUS = 404;
const TOO_MANY_REQUESTS_STATUS = 429;

interface ErrorBody {
  message?: string;
}

export function joinCodeErrorMessage(error: unknown): string {
  const response = (error as AxiosError<ErrorBody>).response;
  if (response?.status === NOT_FOUND_STATUS) return "No lobby uses that code.";
  if (response?.status === TOO_MANY_REQUESTS_STATUS) return "Too many tries. Wait a few minutes and try again.";
  return response?.data?.message ?? "Couldn't join that lobby. Try again.";
}

export function normalizeJoinCode(value: string): string {
  return value.toUpperCase().replace(NON_LETTER_PATTERN, "").slice(0, JOIN_CODE_LENGTH);
}

interface LobbyLauncherProps {
  isCreating: boolean;
  isJoining: boolean;
  joinError: string;
  onCreate: () => void;
  onJoin: (joinCode: string) => void;
  onClose: () => void;
}

// The play button's menu for a player not in a group yet: start a new lobby, or type
// the four-letter code another lobby shows.
export function LobbyLauncher({ isCreating, isJoining, joinError, onCreate, onJoin, onClose }: LobbyLauncherProps) {
  const [joinCode, setJoinCode] = React.useState("");
  const isCodeComplete = joinCode.length === JOIN_CODE_LENGTH;

  function submitJoin(event: React.FormEvent) {
    event.preventDefault();
    if (isCodeComplete && !isJoining) onJoin(joinCode);
  }

  return <>
    <button type="button" aria-label="Close lobby menu" onClick={onClose} className="fixed inset-0 z-30 cursor-default" />
    <section role="dialog" aria-label="Start or join a lobby" className="absolute left-full top-0 z-40 ml-3 w-64 rounded-2xl border-[3px] border-border-strong bg-card p-4 shadow-lg animate-in fade-in-0">
      <button type="button" onClick={onCreate} disabled={isCreating} className="flex w-full items-center justify-center gap-2 rounded-full bg-primary px-4 py-2.5 font-display text-xs text-primary-foreground disabled:opacity-60">
        {isCreating ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}Create a lobby
      </button>
      <div className="my-3 flex items-center gap-2 text-[10px] font-bold uppercase tracking-wider text-muted-foreground"><span className="h-px flex-1 bg-border" />or join one<span className="h-px flex-1 bg-border" /></div>
      <form onSubmit={submitJoin} className="flex items-center gap-2">
        <input
          aria-label="Lobby code"
          value={joinCode}
          onChange={(event) => setJoinCode(normalizeJoinCode(event.target.value))}
          placeholder="ABCD"
          autoComplete="off"
          spellCheck={false}
          className="min-w-0 flex-1 rounded-full border-2 border-secondary bg-background px-4 py-2 text-center font-display text-sm uppercase tracking-[0.3em] text-card-foreground outline-none focus:border-primary"
        />
        <button type="submit" disabled={!isCodeComplete || isJoining} aria-label="Join lobby" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-accent text-accent-foreground disabled:opacity-50">
          {isJoining ? <Loader2 className="size-4 animate-spin" /> : <LogIn className="size-4" />}
        </button>
      </form>
      {joinError ? <p role="alert" className="mt-2 text-[11px] text-destructive">{joinError}</p> : null}
    </section>
  </>;
}
