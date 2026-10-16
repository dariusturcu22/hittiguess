import { describe, expect, it } from "vitest";

import { ADMIN_NAVIGATION_ITEMS, isAdminNavigationItemActive } from "./admin-navigation";

describe("ADMIN_NAVIGATION_ITEMS", () => {
  it("lists the song catalog before reports, matching the admin rail order", () => {
    expect(ADMIN_NAVIGATION_ITEMS.map((item) => item.label)).toEqual([
      "Backfill queue",
      "Song catalog",
      "Reports",
    ]);
  });

  it("treats the song catalog as active on its own route and nested routes only", () => {
    const songCatalogItem = ADMIN_NAVIGATION_ITEMS.find((item) => item.href === "/admin/songs");
    expect(songCatalogItem).toBeDefined();
    if (!songCatalogItem) {
      return;
    }

    expect(isAdminNavigationItemActive(songCatalogItem, "/admin/songs")).toBe(true);
    expect(isAdminNavigationItemActive(songCatalogItem, "/admin/reports")).toBe(false);
    expect(isAdminNavigationItemActive(songCatalogItem, null)).toBe(false);
  });
});
