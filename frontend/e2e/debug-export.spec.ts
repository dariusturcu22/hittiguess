import { expect, test } from "@playwright/test";

const LAN = "http://192.168.1.135:3000";
const API = "http://192.168.1.135:8080";

async function csrfHeaders(page): Promise<Record<string, string>> {
  const cookies = await page.context().cookies(API);
  const csrf = cookies.find((cookie) => cookie.name === "XSRF-TOKEN");
  return csrf ? { "X-XSRF-TOKEN": csrf.value } : {};
}

test("lan probe: export download", async ({ browser }) => {
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

  const songResponse = await page.request.post(`${API}/api/playlists/3/songs`, {
    data: {
      artist: "Probe artist",
      title: "Probe song",
      releaseYear: 2001,
      youtubeId: "dQw4w9WgXcQ",
      color: "a6e3a1",
    },
    headers: await csrfHeaders(page),
  });
  console.log(`ADD_SONG ${songResponse.status()}`);

  await page.goto(`${LAN}/playlists/3`);
  await page.getByTitle("Export cards").click();
  const downloadPromise = page.waitForEvent("download", { timeout: 60000 });
  await page.getByRole("button", { name: "Download PDF" }).click();
  try {
    const download = await downloadPromise;
    console.log(`DOWNLOAD ${download.suggestedFilename()}`);
  } catch {
    console.log("DOWNLOAD timed out");
  }
  console.log(badResponses.length === 0 ? "NO_BAD_RESPONSES" : badResponses.join("\n"));
  await context.close();
});
