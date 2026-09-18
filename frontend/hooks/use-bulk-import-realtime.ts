"use client";

import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";

const WEBSOCKET_PATH = "/ws";
const BULK_IMPORT_PROGRESS_DESTINATION = "/user/queue/bulk-import-progress";
const RECONNECT_DELAY_MILLISECONDS = 3_000;

export interface BulkImportProgressEvent {
  youtubeId: string;
  outcome: "ALREADY_KNOWN" | "RESOLVED" | "UNRESOLVED";
}

function websocketUrl(): string {
  const apiUrl = new URL(process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080");
  apiUrl.protocol = apiUrl.protocol === "https:" ? "wss:" : "ws:";
  apiUrl.pathname = WEBSOCKET_PATH;
  apiUrl.search = "";
  return apiUrl.toString();
}

export function useBulkImportRealtime() {
  const [events, setEvents] = useState<BulkImportProgressEvent[]>([]);

  useEffect(() => {
    const client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: RECONNECT_DELAY_MILLISECONDS,
      onConnect: () => {
        client.subscribe(BULK_IMPORT_PROGRESS_DESTINATION, (message) => {
          setEvents((previousEvents) => [...previousEvents, JSON.parse(message.body) as BulkImportProgressEvent]);
        });
      },
    });
    client.activate();
    return () => { void client.deactivate(); };
  }, []);

  function reset() {
    setEvents([]);
  }

  return { events, reset };
}
