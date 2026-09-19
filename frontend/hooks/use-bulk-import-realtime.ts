"use client";

import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";

const WEBSOCKET_PATH = "/ws";
const BULK_IMPORT_PROGRESS_DESTINATION = "/user/queue/bulk-import-progress";
const RECONNECT_DELAY_MILLISECONDS = 3_000;
const BULK_IMPORT_EVENTS_STORAGE_KEY = "bulk-import-progress-events";
const BULK_IMPORT_EVENT_NAME = "bulk-import-event";
const BULK_IMPORT_RESET_EVENT_NAME = "bulk-import-progress-reset";
const BULK_IMPORT_CONNECTION_EVENT_NAME = "bulk-import-connection";
const BULK_IMPORT_CONNECTION_STORAGE_KEY = "bulk-import-connection-state";

export interface BulkImportProgressEvent {
  importJobId?: string;
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

interface BulkImportRealtimeOptions {
  subscribeToProgress?: boolean;
}

export function useBulkImportRealtime({
  subscribeToProgress = true,
}: BulkImportRealtimeOptions = {}) {
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
  const [isConnected, setIsConnected] = useState(
    () => typeof window !== "undefined" && sessionStorage.getItem(BULK_IMPORT_CONNECTION_STORAGE_KEY) === "true",
  );

  useEffect(() => {
    sessionStorage.setItem(BULK_IMPORT_EVENTS_STORAGE_KEY, JSON.stringify(events));
  }, [events]);

  useEffect(() => {
    const clearProgressEvents = () => setEvents([]);
    window.addEventListener(BULK_IMPORT_RESET_EVENT_NAME, clearProgressEvents);
    return () => window.removeEventListener(BULK_IMPORT_RESET_EVENT_NAME, clearProgressEvents);
  }, []);

  useEffect(() => {
    const updateConnectionState = (event: Event) => {
      setIsConnected((event as CustomEvent<boolean>).detail);
    };
    window.addEventListener(BULK_IMPORT_CONNECTION_EVENT_NAME, updateConnectionState);
    return () => window.removeEventListener(BULK_IMPORT_CONNECTION_EVENT_NAME, updateConnectionState);
  }, []);

  useEffect(() => {
    if (!subscribeToProgress) {
      const addProgressEvent = (event: Event) => {
        const progressEvent = event as CustomEvent<BulkImportProgressEvent>;
        setEvents((previousEvents) => [...previousEvents, progressEvent.detail]);
      };
      window.addEventListener(BULK_IMPORT_EVENT_NAME, addProgressEvent);
      return () => {
        window.removeEventListener(BULK_IMPORT_EVENT_NAME, addProgressEvent);
      };
    }

    const client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: RECONNECT_DELAY_MILLISECONDS,
      onConnect: () => {
        setIsConnected(true);
        sessionStorage.setItem(BULK_IMPORT_CONNECTION_STORAGE_KEY, "true");
        window.dispatchEvent(new CustomEvent(BULK_IMPORT_CONNECTION_EVENT_NAME, { detail: true }));
        client.subscribe(BULK_IMPORT_PROGRESS_DESTINATION, (message) => {
          const progressEvent = JSON.parse(message.body) as BulkImportProgressEvent;
          setEvents((previousEvents) => [...previousEvents, progressEvent]);
          window.dispatchEvent(new CustomEvent(BULK_IMPORT_EVENT_NAME, { detail: progressEvent }));
        });
      },
    });
    client.activate();
    return () => {
      setIsConnected(false);
      sessionStorage.setItem(BULK_IMPORT_CONNECTION_STORAGE_KEY, "false");
      window.dispatchEvent(new CustomEvent(BULK_IMPORT_CONNECTION_EVENT_NAME, { detail: false }));
      void client.deactivate();
    };
  }, [subscribeToProgress]);

  function reset() {
    setEvents([]);
    sessionStorage.removeItem(BULK_IMPORT_EVENTS_STORAGE_KEY);
    window.dispatchEvent(new Event(BULK_IMPORT_RESET_EVENT_NAME));
  }

  return { events, isConnected, reset };
}
