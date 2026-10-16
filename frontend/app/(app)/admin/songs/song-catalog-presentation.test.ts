import { describe, expect, it } from "vitest";

import { CATALOG_COLUMNS, catalogColumnClassName } from "./song-catalog-presentation";

describe("catalog columns", () => {
  it("keeps the mockup's column order", () => {
    expect(CATALOG_COLUMNS.map((column) => column.label)).toEqual([
      "Title",
      "Artist",
      "Year",
      "Status",
      "Pipeline",
      "Added",
      "Added by",
    ]);
  });

  it("drops Added by first and Pipeline next as the table narrows", () => {
    expect(catalogColumnClassName("addedBy")).toContain("hidden @6xl:block");
    expect(catalogColumnClassName("pipeline")).toContain("hidden @4xl:block");
  });

  it("never hides the title, artist, year, status, or added columns", () => {
    for (const key of ["title", "artist", "year", "status", "added"] as const) {
      expect(catalogColumnClassName(key)).not.toContain("hidden");
    }
  });

  it("lets title and artist shrink with ellipsis instead of overflowing", () => {
    expect(catalogColumnClassName("title")).toContain("min-w-0");
    expect(catalogColumnClassName("artist")).toContain("min-w-0");
  });
});
