"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";

import { getGetGroupQueryKey } from "@/hooks/generated/group-management/group-management";
import { getGetHistoryQueryKey } from "@/hooks/generated/group-chat/group-chat";

const WEBSOCKET_PATH = "/ws";
const GROUP_TOPIC_PREFIX = "/topic/groups";
const GROUP_APP_PREFIX = "/app/groups";
const RECONNECT_DELAY_MILLISECONDS = 3_000;
const GROUP_TOPICS = ["membership", "settings", "voice", "chat"];

type ConnectionState = "connecting" | "connected" | "disconnected" | "error";

function websocketUrl(): string {
  const apiUrl = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
  apiUrl.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
  apiUrl.pathname = WEBSOCKET_PATH;
  apiUrl.search = "";
  return apiUrl.toString();
}

export function useGroupRealtime(groupId: number) {
  const queryClient = useQueryClient();
  const clientReference = useRef<Client | null>(null);
  const [connectionState, setConnectionState] = useState<ConnectionState>("connecting");
  const hasValidGroupId = Number.isInteger(groupId) && groupId > 0;

  useEffect(() => {
    if (!hasValidGroupId) return;
    const client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: RECONNECT_DELAY_MILLISECONDS,
      onConnect: () => {
        setConnectionState("connected");
        GROUP_TOPICS.forEach((topic) => {
          client.subscribe(`${GROUP_TOPIC_PREFIX}/${groupId}/${topic}`, () => {
            void queryClient.invalidateQueries({ queryKey: getGetGroupQueryKey(groupId) });
            if (topic === "chat") void queryClient.invalidateQueries({ queryKey: getGetHistoryQueryKey(groupId) });
          });
        });
      },
      onWebSocketClose: () => setConnectionState("disconnected"),
      onWebSocketError: () => setConnectionState("error"),
      onStompError: () => setConnectionState("error"),
    });
    clientReference.current = client;
    client.activate();
    return () => { clientReference.current = null; void client.deactivate(); };
  }, [groupId, hasValidGroupId, queryClient]);

  const sendChat = useCallback((content: string) => {
    const client = clientReference.current;
    if (!client?.connected || !content.trim()) return false;
    client.publish({ destination: `${GROUP_APP_PREFIX}/${groupId}/chat`, body: JSON.stringify({ content: content.trim() }) });
    return true;
  }, [groupId]);

  return { connectionState: hasValidGroupId ? connectionState : "disconnected", sendChat };
}
