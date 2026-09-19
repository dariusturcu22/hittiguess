import { test, expect, type Browser, type Page } from "@playwright/test";

/**
 * The three seeded TEST-role accounts from TestAccountSeeder (see
 * docs/DEV_SETUP.md). Each maps to a role in a real multiplayer round: a DJ,
 * an active player, and another player, so a future test can extend this
 * fixture into a full game-session flow without changing the login setup.
 */
const TEST_ACCOUNTS = [
  { email: "test-agent-1@hittiguess.local", password: "HittiguessTestAgent1!2026" },
  { email: "test-agent-2@hittiguess.local", password: "HittiguessTestAgent2!2026" },
  { email: "test-agent-3@hittiguess.local", password: "HittiguessTestAgent3!2026" },
] as const;

async function loginAsTestAccount(
  browser: Browser,
  account: (typeof TEST_ACCOUNTS)[number],
): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto("/login");
  await page.getByLabel("Email").fill(account.email);
  await page.getByLabel("Password").fill(account.password);
  await page.getByRole("button", { name: "Log in" }).click();

  await page.waitForURL("/playlists");

  return page;
}

test("three seeded test accounts can log in as genuinely separate sessions", async ({
  browser,
}) => {
  const pages = await Promise.all(
    TEST_ACCOUNTS.map((account) => loginAsTestAccount(browser, account)),
  );

  try {
    for (const page of pages) {
      await expect(page).toHaveURL("/playlists");
      await expect(
        page.getByRole("heading", { name: "Welcome back" }),
      ).toHaveCount(0);
    }
  } finally {
    await Promise.all(pages.map((page) => page.context().close()));
  }
});
