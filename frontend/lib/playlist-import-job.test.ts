import { describe, expect, it } from "vitest";

import { countImportItems } from "./playlist-import-job";

describe("countImportItems", () => {
  it("splits an import into working, waiting, and settled songs", () => {
    const counts = countImportItems([
      { status: "IDENTIFYING" },
      { status: "DATING" },
      { status: "PENDING" },
      {},
      { status: "RESOLVED" },
      { status: "ALREADY_KNOWN" },
      { status: "UNRESOLVED" },
    ]);

    expect(counts).toEqual({ total: 7, settled: 3, working: 2, waiting: 2, added: 2, unmatched: 1 });
  });
});
