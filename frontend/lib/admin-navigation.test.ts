import { describe, expect, it } from "vitest";

import { ADMIN_NAVIGATION_ITEMS, isAdminNavigationItemActive } from "./admin-navigation";

describe("ADMIN_NAVIGATION_ITEMS", () => {
  it("lists the dashboard first, then the rest of the admin rail in order", () => {
    expect(ADMIN_NAVIGATION_ITEMS.map((item) => item.label)).toEqual([
      "Dashboard",
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

  it("treats the dashboard as active on the admin root only, not on other admin routes", () => {
    const dashboardItem = ADMIN_NAVIGATION_ITEMS.find((item) => item.href === "/admin");
    expect(dashboardItem).toBeDefined();
    if (!dashboardItem) {
      return;
    }

    expect(isAdminNavigationItemActive(dashboardItem, "/admin")).toBe(true);
    expect(isAdminNavigationItemActive(dashboardItem, "/admin/songs")).toBe(false);
    expect(isAdminNavigationItemActive(dashboardItem, "/admin/reports")).toBe(false);
    expect(isAdminNavigationItemActive(dashboardItem, null)).toBe(false);
  });
});
