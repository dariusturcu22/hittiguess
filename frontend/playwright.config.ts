import { defineConfig, devices } from "@playwright/test";

/**
 * Runs against the local dev stack started separately (see docs/DEV_SETUP.md).
 * A single chromium project is enough at this stage; a cross-browser matrix
 * is deferred to story 22.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: "html",
  use: {
    baseURL: "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
