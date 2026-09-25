import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createElement, type ReactNode } from "react";

import { useGameSessionRealtime } from "./use-game-session-realtime";

let roundMessageHandler: ((message: { body: string }) => void) | undefined;
let endedMessageHandler: ((message: { body: string }) => void) | undefined;

vi.mock("@/hooks/generated/game-session/game-session", () => ({
  getGetSessionQueryKey: (sessionId: number) => ["session", sessionId],
  getGetCurrentRoundLinkOutQueryKey: (sessionId: number) => ["round-link-out", sessionId],
  getGetGuessStateQueryKey: (sessionId: number) => ["guess-state", sessionId],
  getGetActiveSessionForGroupQueryKey: (groupId: number) => [`/api/sessions/groups/${groupId}/active`],
  getGetResultsQueryKey: (groupId: number) => [`/api/sessions/groups/${groupId}/results`],
}));

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetActiveMembershipQueryKey: () => ["active-membership"],
  getGetGroupQueryKey: (groupId: number) => ["group", groupId],
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
      if (destination.endsWith("/ended")) {
        endedMessageHandler = callback;
      }
    }
    get connected() { return true; }
    publish() {}
  },
}));

afterEach(() => {
  roundMessageHandler = undefined;
  endedMessageHandler = undefined;
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

  it("forwards a placement preview without refetching the session", () => {
    const onRoundEvent = vi.fn();
    const queryClient = new QueryClient();
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    renderHook(() => useGameSessionRealtime(1, onRoundEvent), {
      wrapper: ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children),
    });

    roundMessageHandler?.({ body: JSON.stringify({ type: "PLACEMENT_PREVIEW", sessionId: 1, payload: { roundId: 3, activePlayerId: 4, position: 2 } }) });

    expect(onRoundEvent).toHaveBeenCalledWith(expect.objectContaining({ type: "PLACEMENT_PREVIEW" }));
    expect(invalidateQueries).not.toHaveBeenCalled();
  });

  it("refetches the session for every round state change", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    renderHook(() => useGameSessionRealtime(1), {
      wrapper: ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children),
    });

    roundMessageHandler?.({ body: JSON.stringify({ type: "BETTING_OPENED", sessionId: 1 }) });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["session", 1] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["round-link-out", 1] });
  });

  it("hands the ended event's group to the page and clears the dead session's cached lookups", () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(["/api/sessions/groups/4/active"], { id: 1 });
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");
    const onSessionEnded = vi.fn();
    renderHook(() => useGameSessionRealtime(1, undefined, undefined, onSessionEnded), {
      wrapper: ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children),
    });

    endedMessageHandler?.({ body: JSON.stringify({ type: "SESSION_ENDED", sessionId: 1, payload: { groupId: 4 } }) });

    expect(onSessionEnded).toHaveBeenCalledWith({ groupId: 4 });
    expect(queryClient.getQueryData(["/api/sessions/groups/4/active"])).toBeUndefined();
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["group", 4] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["active-membership"] });
  });
});
