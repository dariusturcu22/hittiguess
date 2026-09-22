import { expect, test } from "@playwright/test";

const LAN = "http://192.168.1.135:3000";

test("lan probe: fetch metadata", async ({ browser }) => {
  test.setTimeout(240000);
  const context = await browser.newContext();
  const page = await context.newPage();
  const badResponses: Array<string> = [];
  page.on("response", (response) => {
    if (response.status() >= 400) {
      badResponses.push(`${response.status()} ${response.url().slice(0, 130)}`);
    }
  });
  await page.goto(`${LAN}/login`);
  await page.getByLabel("Email").fill("test-agent-2@hittiguess.local");
  await page.getByLabel("Password", { exact: true }).fill("HittiguessTestAgent2!2026");
  await page.getByRole("button", { name: "Log in" }).click();
  await page.waitForURL("**/playlists", { timeout: 20000 });
  await page.goto(`${LAN}/playlists/3/songs/add`);
  await page.getByRole("button", { name: "Add a new song" }).click();
  await page.getByLabel("YouTube link").fill("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
  await page.getByRole("button", { name: "Fetch details" }).click();
  try {
    await page.getByRole("button", { name: /Save & add to playlist|Add to playlist/ }).waitFor({ timeout: 180000 });
    console.log("FETCH ok, review step shown");
  } catch {
    console.log("FETCH timed out waiting for review");
  }
  console.log(badResponses.length === 0 ? "NO_BAD_RESPONSES" : badResponses.join("\n"));
  await context.close();
});
