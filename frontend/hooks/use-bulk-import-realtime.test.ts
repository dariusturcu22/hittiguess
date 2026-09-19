import { act, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { useBulkImportRealtime } from "./use-bulk-import-realtime";

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    activate() {}
    deactivate() { return Promise.resolve(); }
    subscribe() {}
  },
}));

const STORAGE_KEY = "bulk-import-progress-events";

afterEach(() => {
  sessionStorage.clear();
});

describe("useBulkImportRealtime", () => {
  it("restores progress events saved before the import page remounts", () => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify([
      { youtubeId: "dQw4w9WgXcQ", outcome: "RESOLVED" },
    ]));

    const { result } = renderHook(() => useBulkImportRealtime());

    expect(result.current.events).toEqual([
      { youtubeId: "dQw4w9WgXcQ", outcome: "RESOLVED" },
    ]);
  });

  it("updates a page consumer from progress received by the global subscription", () => {
    const { result } = renderHook(() => useBulkImportRealtime({ subscribeToProgress: false }));

    act(() => {
      window.dispatchEvent(new CustomEvent("bulk-import-event", {
        detail: { youtubeId: "oHg5SJYRHA0", outcome: "ALREADY_KNOWN" },
      }));
    });

    expect(result.current.events).toEqual([
      { youtubeId: "oHg5SJYRHA0", outcome: "ALREADY_KNOWN" },
    ]);
  });
});
