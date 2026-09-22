import { expect, test } from "@playwright/test";

const LAN = "http://192.168.1.135:3000";

test("lan probe: import websocket connects", async ({ browser }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const badResponses: Array<string> = [];
  page.on("response", (response) => {
    if (response.status() >= 400) {
      badResponses.push(`${response.status()} ${response.url().slice(0, 120)}`);
    }
  });
  await page.goto(`${LAN}/login`);
  await page.getByLabel("Email").fill("test-agent-2@hittiguess.local");
  await page.getByLabel("Password", { exact: true }).fill("HittiguessTestAgent2!2026");
  await page.getByRole("button", { name: "Log in" }).click();
  await page.waitForURL("**/playlists", { timeout: 20000 });
  await page.getByRole("button", { name: "Create playlist" }).click();
  await page.waitForURL(/\/playlists\/\d+/, { timeout: 20000 });
  const playlistId = new URL(page.url()).pathname.split("/")[2];
  console.log(`PLAYLIST ${playlistId}`);
  await page.goto(`${LAN}/playlists/${playlistId}/import/youtube`);
  const fetchButton = page.getByRole("button", { name: /Fetch playlist|Connecting|Fetching/ });
  await expect(fetchButton).toBeVisible();
  await page.waitForTimeout(8000);
  console.log(`IMPORT_BUTTON ${await fetchButton.textContent()}`);
  console.log(badResponses.length === 0 ? "NO_BAD_RESPONSES" : badResponses.join("\n"));
  await context.close();
});
