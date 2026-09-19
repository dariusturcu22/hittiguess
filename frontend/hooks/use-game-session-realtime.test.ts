import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createElement, type ReactNode } from "react";

import { useGameSessionRealtime } from "./use-game-session-realtime";

let roundMessageHandler: ((message: { body: string }) => void) | undefined;

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  getGetSessionQueryKey: (sessionId: number) => ["session", sessionId],
}));

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    constructor(private readonly options: { onConnect: () => void }) {}
    activate() { this.options.onConnect(); }
    deactivate() { return Promise.resolve(); }
    subscribe(destination: string, callback: (message: { body: string }) => void) {
      if (destination.endsWith("/round")) {
        roundMessageHandler = callback;
      }
    }
    get connected() { return true; }
    publish() {}
  },
}));

afterEach(() => {
  roundMessageHandler = undefined;
  vi.restoreAllMocks();
});

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: new QueryClient() }, children);
}

describe("useGameSessionRealtime", () => {
  it("forwards a guess-locked event immediately", () => {
    const onRoundEvent = vi.fn();
    renderHook(() => useGameSessionRealtime(1, onRoundEvent), { wrapper });

    roundMessageHandler?.({ body: JSON.stringify({ type: "GUESS_LOCKED", sessionId: 1, payload: { activePlayerId: 4 } }) });

    expect(onRoundEvent).toHaveBeenCalledWith({ type: "GUESS_LOCKED", sessionId: 1, payload: { activePlayerId: 4 } });
  });
});
