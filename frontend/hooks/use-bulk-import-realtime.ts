"use client";

import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";

const WEBSOCKET_PATH = "/ws";
const BULK_IMPORT_PROGRESS_DESTINATION = "/user/queue/bulk-import-progress";
const RECONNECT_DELAY_MILLISECONDS = 3_000;
const BULK_IMPORT_EVENTS_STORAGE_KEY = "bulk-import-progress-events";

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
  const [events, setEvents] = useState<BulkImportProgressEvent[]>(() => {
    if (typeof window === "undefined") {
      return [];
    }
    const savedEvents = sessionStorage.getItem(BULK_IMPORT_EVENTS_STORAGE_KEY);
    if (!savedEvents) {
      return [];
    }
    try {
      return JSON.parse(savedEvents) as BulkImportProgressEvent[];
    } catch {
      sessionStorage.removeItem(BULK_IMPORT_EVENTS_STORAGE_KEY);
      return [];
    }
  });

  useEffect(() => {
    sessionStorage.setItem(BULK_IMPORT_EVENTS_STORAGE_KEY, JSON.stringify(events));
  }, [events]);

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
    sessionStorage.removeItem(BULK_IMPORT_EVENTS_STORAGE_KEY);
  }

  return { events, reset };
}
