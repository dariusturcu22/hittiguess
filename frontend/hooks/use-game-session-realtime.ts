"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";

import { getGetSessionQueryKey } from "@/hooks/generated/game-session/game-session";

const WEBSOCKET_PATH = "/ws";
const SESSION_ROUND_TOPIC = "/topic/sessions";
const SESSION_APP_DESTINATION = "/app/sessions";
const RECONNECT_DELAY_MILLISECONDS = 3_000;
const PLACE_ACTION = "place";
const GUESS_ACTION = "guess";
const BET_ACTION = "bet";
const SKIP_BETTING_ACTION = "skip-betting";

type ConnectionState = "connecting" | "connected" | "disconnected" | "error";

export interface SessionRoundEvent {
  type: string;
  sessionId: number;
  payload?: { activePlayerId?: number };
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
) {
  const queryClient = useQueryClient();
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
          onRoundEvent?.(JSON.parse(message.body) as SessionRoundEvent);
          void queryClient.invalidateQueries({ queryKey: getGetSessionQueryKey(sessionId) });
        });
        client.subscribe(`${SESSION_ROUND_TOPIC}/${sessionId}/ended`, () => {
          void queryClient.invalidateQueries({ queryKey: getGetSessionQueryKey(sessionId) });
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
    placeCard: (position: number) => publish(PLACE_ACTION, { position }),
    submitGuess: (guessedArtist: string, guessedTitle: string) => publish(GUESS_ACTION, { guessedArtist, guessedTitle }),
    placeBet: (position: number) => publish(BET_ACTION, { position }),
    skipBetting: () => publish(SKIP_BETTING_ACTION),
  };
}
