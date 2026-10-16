import { expect, it, vi } from "vitest";
import CatalogBacklogRedirect from "./page";
const redirect = vi.hoisted(() =>
  vi.fn(() => {
    throw new Error("redirect");
  }),
);
vi.mock("next/navigation", () => ({ redirect }));
it("redirects the old route to the backfill queue", () => {
  expect(() => CatalogBacklogRedirect()).toThrow("redirect");
  expect(redirect).toHaveBeenCalledWith("/admin/backfill-queue");
});
