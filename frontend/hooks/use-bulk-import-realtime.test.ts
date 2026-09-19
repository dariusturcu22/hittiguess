import { renderHook } from "@testing-library/react";
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
});
