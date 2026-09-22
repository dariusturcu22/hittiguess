import { test } from "@playwright/test";

const LAN = "http://192.168.1.135:3000";

test("lan probe: trace metadata request", async ({ browser }) => {
  test.setTimeout(120000);
  const context = await browser.newContext();
  const page = await context.newPage();
  page.on("request", (request) => {
    if (request.url().includes("metadata")) {
      console.log(`REQ ${request.method()} ${request.url().slice(0, 150)}`);
    }
  });
  page.on("response", (response) => {
    if (response.url().includes("metadata")) {
      console.log(`RESP ${response.status()} ${response.url().slice(0, 150)}`);
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
  await page.waitForTimeout(60000);
  await context.close();
});
