"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";

import { getGetActiveSessionForGroupQueryKey, getGetGuessStateQueryKey, getGetResultsQueryKey, getGetSessionQueryKey } from "@/hooks/generated/game-session/game-session";
import { getGetActiveMembershipQueryKey, getGetGroupQueryKey } from "@/hooks/generated/group-management/group-management";

const WEBSOCKET_PATH = "/ws";
const SESSION_ROUND_TOPIC = "/topic/sessions";
const SESSION_USER_QUEUE = "/user/queue/sessions";
const SESSION_APP_DESTINATION = "/app/sessions";
const RECONNECT_DELAY_MILLISECONDS = 3_000;
const PREVIEW_ACTION = "preview";
const PLACE_ACTION = "place";
export const PLACEMENT_PREVIEW_EVENT = "PLACEMENT_PREVIEW";
export const GUESS_CORRECT_EVENT = "GUESS_CORRECT";
const GUESS_ACTION = "guess";
const BET_ACTION = "bet";
const SKIP_BETTING_ACTION = "skip-betting";
const ROUND_EVENT_PARSE_FAILURE_MESSAGE = "Unable to parse session round event";
const GUESS_RESULT_PARSE_FAILURE_MESSAGE = "Unable to parse guess result";
const SESSION_ENDED_PARSE_FAILURE_MESSAGE = "Unable to parse session ended event";
const ACTIVE_SESSION_QUERY_PATTERN = /^\/api\/sessions\/groups\/\d+\/active$/;

type ConnectionState = "connecting" | "connected" | "disconnected" | "error";

export interface SessionRoundEvent {
  type: string;
  sessionId: number;
  payload?: {
    activePlayerId?: number;
    roundId?: number;
    position?: number | null;
    playerId?: number;
    displayName?: string;
    artistGuessed?: boolean;
    titleGuessed?: boolean;
  };
}

export interface GuessState {
  roundId?: number;
  artistCount?: number;
  correctArtistCount?: number;
  artistGuessingClosed?: boolean;
  titleGuessed?: boolean;
  titleCorrect?: boolean;
  tokenEarned?: boolean;
}

// Sent only to the player who guessed.
export interface GuessResult {
  roundId?: number;
  artistCorrect?: boolean;
  titleCorrect?: boolean;
  state?: GuessState;
}

// The session's rows are purged the moment it ends, so the ended event is the only place
// a client learns which group's results to show. An abandoned session carries no results.
export interface SessionEnded {
  groupId?: number;
}

interface SessionEndedEvent {
  payload?: { groupId?: number } | null;
}

function websocketUrl(): string {
  const apiUrl = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
  apiUrl.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
  apiUrl.pathname = WEBSOCKET_PATH;
  apiUrl.search = "";
  return apiUrl.toString();
}

function sessionDestination(sessionId: number, action: string): string {
  return `${SESSION_APP_DESTINATION}/${sessionId}/${action}`;
}

export function useGameSessionRealtime(
  sessionId: number,
  onRoundEvent?: (event: SessionRoundEvent) => void,
  onGuessResult?: (result: GuessResult) => void,
  onSessionEnded?: (ended: SessionEnded) => void,
) {
  const queryClient = useQueryClient();
  const onGuessResultReference = useRef(onGuessResult);
  const onSessionEndedReference = useRef(onSessionEnded);
  useEffect(() => {
    onGuessResultReference.current = onGuessResult;
    onSessionEndedReference.current = onSessionEnded;
  });
  const clientReference = useRef<Client | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>("connecting");
  const hasValidSessionId = Number.isInteger(sessionId) && sessionId > 0;

  useEffect(() => {
    if (!hasValidSessionId) {
      return;
    }

    const client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: RECONNECT_DELAY_MILLISECONDS,
      onConnect: () => {
        setConnectionState("connected");
        client.subscribe(`${SESSION_ROUND_TOPIC}/${sessionId}/round`, (message) => {
          let roundEvent: SessionRoundEvent | undefined;
          try {
            roundEvent = JSON.parse(message.body) as SessionRoundEvent;
            onRoundEvent?.(roundEvent);
          } catch (parseError) {
            console.warn(ROUND_EVENT_PARSE_FAILURE_MESSAGE, parseError);
          }
          // A placement preview only mirrors the active player's drag; the round itself
          // hasn't changed, so there's nothing to refetch.
          if (roundEvent?.type !== PLACEMENT_PREVIEW_EVENT) {
            void queryClient.invalidateQueries({ queryKey: getGetSessionQueryKey(sessionId) });
            void queryClient.invalidateQueries({ queryKey: getGetGuessStateQueryKey(sessionId) });
          }
        });
        client.subscribe(`${SESSION_USER_QUEUE}/${sessionId}/guess-result`, (message) => {
          try {
            onGuessResultReference.current?.(JSON.parse(message.body) as GuessResult);
          } catch (parseError) {
            console.warn(GUESS_RESULT_PARSE_FAILURE_MESSAGE, parseError);
          }
        });
        client.subscribe(`${SESSION_ROUND_TOPIC}/${sessionId}/ended`, (message) => {
          let groupId: number | undefined;
          try {
            groupId = (JSON.parse(message.body) as SessionEndedEvent).payload?.groupId ?? undefined;
          } catch (parseError) {
            console.warn(SESSION_ENDED_PARSE_FAILURE_MESSAGE, parseError);
          }
          queryClient.removeQueries({ queryKey: getGetSessionQueryKey(sessionId) });
          if (groupId !== undefined) {
            queryClient.removeQueries({ queryKey: getGetActiveSessionForGroupQueryKey(groupId) });
            void queryClient.invalidateQueries({ queryKey: getGetResultsQueryKey(groupId) });
            void queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(groupId) });
          } else {
            queryClient.removeQueries({ predicate: (query) => ACTIVE_SESSION_QUERY_PATTERN.test(String(query.queryKey.at(0) ?? "")) });
          }
          void queryClient.invalidateQueries({ queryKey: getGetActiveMembershipQueryKey() });
          onSessionEndedReference.current?.({ groupId });
        });
      },
      onWebSocketClose: () => setConnectionState("disconnected"),
      onWebSocketError: () => setConnectionState("error"),
      onStompError: () => setConnectionState("error"),
    });

    clientReference.current = client;
    client.activate();

    return () => {
      clientReference.current = null;
      void client.deactivate();
    };
  }, [hasValidSessionId, onRoundEvent, queryClient, sessionId]);

  const publish = useCallback((action: string, body?: Record<string, unknown>) => {
    const client = clientReference.current;
    if (!client?.connected) {
      return false;
    }
    client.publish({
      destination: sessionDestination(sessionId, action),
      body: body ? JSON.stringify(body) : undefined,
    });
    return true;
  }, [sessionId]);

  return {
    connectionState: hasValidSessionId ? connectionState : "disconnected",
    previewPlacement: (position: number | null) => publish(PREVIEW_ACTION, { position }),
    placeCard: (position: number) => publish(PLACE_ACTION, { position }),
    submitGuess: (guessedArtist: string, guessedTitle: string) => publish(GUESS_ACTION, { guessedArtist, guessedTitle }),
    placeBet: (position: number) => publish(BET_ACTION, { position }),
    skipBetting: () => publish(SKIP_BETTING_ACTION),
  };
}
