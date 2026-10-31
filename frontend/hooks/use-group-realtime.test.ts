import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createElement, type ReactNode } from "react";

import { useGroupRealtime } from "./use-group-realtime";

const TEST_GROUP_ID = 5;
const topicHandlers = new Map<string, (message: { body: string }) => void>();
let publishedMessage: { destination: string; body: string } | undefined;

vi.mock("@/hooks/generated/group-management/group-management", () => ({
  getGetActiveMembershipQueryKey: () => ["active-membership"],
  getGetGroupQueryKey: (groupId: number) => ["group", groupId],
}));

vi.mock("@/hooks/generated/group-chat/group-chat", () => ({
  getGetHistoryQueryKey: (groupId: number) => ["chat-history", groupId],
}));

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    constructor(private readonly options: { onConnect: () => void }) {}
    activate() { this.options.onConnect(); }
    deactivate() { return Promise.resolve(); }
    subscribe(destination: string, callback: (message: { body: string }) => void) {
      topicHandlers.set(destination, callback);
    }
    get connected() { return true; }
    publish(parameters: { destination: string; body: string }) {
      publishedMessage = parameters;
    }
  },
}));

afterEach(() => {
  topicHandlers.clear();
  publishedMessage = undefined;
  vi.restoreAllMocks();
});

function createTestWrapper(queryClient: QueryClient) {
  return function TestWrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe("useGroupRealtime", () => {
  it("refetches group, active membership, and chat history on connect", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");

    renderHook(() => useGroupRealtime(TEST_GROUP_ID), {
      wrapper: createTestWrapper(queryClient),
    });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["group", TEST_GROUP_ID] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["active-membership"] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["chat-history", TEST_GROUP_ID] });
  });

  it("refetches active membership and group on membership changes", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");

    renderHook(() => useGroupRealtime(TEST_GROUP_ID), {
      wrapper: createTestWrapper(queryClient),
    });

    invalidateQueries.mockClear();
    const membershipHandler = topicHandlers.get(`/topic/groups/${TEST_GROUP_ID}/membership`);
    membershipHandler?.({ body: JSON.stringify({ type: "MEMBER_JOINED" }) });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["group", TEST_GROUP_ID] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["active-membership"] });
  });

  it("refetches chat history on chat message updates", () => {
    const queryClient = new QueryClient();
    const invalidateQueries = vi.spyOn(queryClient, "invalidateQueries");

    renderHook(() => useGroupRealtime(TEST_GROUP_ID), {
      wrapper: createTestWrapper(queryClient),
    });

    invalidateQueries.mockClear();
    const chatHandler = topicHandlers.get(`/topic/groups/${TEST_GROUP_ID}/chat`);
    chatHandler?.({ body: JSON.stringify({ type: "CHAT_MESSAGE" }) });

    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["group", TEST_GROUP_ID] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ["chat-history", TEST_GROUP_ID] });
  });

  it("publishes chat messages to the group destination", () => {
    const queryClient = new QueryClient();
    const hookResult = renderHook(() => useGroupRealtime(TEST_GROUP_ID), {
      wrapper: createTestWrapper(queryClient),
    });

    const sent = hookResult.result.current.sendChat("Hello group!");

    expect(sent).toBe(true);
    expect(publishedMessage).toEqual({
      destination: `/app/groups/${TEST_GROUP_ID}/chat`,
      body: JSON.stringify({ content: "Hello group!" }),
    });
  });
});
