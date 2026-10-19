import { expect, test, type Browser, type Page } from "@playwright/test";

const LOGIN_PATH = "/login";
const REGISTER_PATH = "/register";
const PLAYLISTS_PATH = "/playlists";
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
const KNOWN_VIDEO_ID = "dQw4w9WgXcQ";
const PLAYLIST_EXPORT_TIMEOUT_MILLISECONDS = 60_000;

function uniqueId(prefix: string): string {
  return `${prefix}${Date.now().toString(36)}`;
}

function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

async function csrfHeaders(page: Page): Promise<Record<string, string>> {
  const csrfCookie = (await page.context().cookies(API_BASE_URL)).find(
    (cookie) => cookie.name === "XSRF-TOKEN",
  );
  return csrfCookie ? { "X-XSRF-TOKEN": csrfCookie.value } : {};
}

async function playlistSongIds(page: Page, playlistId: number): Promise<number[]> {
  const response = await page.request.get(apiUrl(`/api/playlists/${playlistId}`));
  expect(response.ok()).toBeTruthy();
  const playlist = await response.json() as { songs: Array<{ id: number }> };
  return playlist.songs.map((song) => song.id);
}

test("core flows: register, playlist CRUD, song add and edit, export", async ({ browser }) => {
  test.setTimeout(PLAYLIST_EXPORT_TIMEOUT_MILLISECONDS * 2);
  const context = await browser.newContext();
  const page = await context.newPage();
  const username = uniqueId("e2e");
  const email = `${uniqueId("e2e")}@example.com`;

  try {
    await test.step("registers a new account and is asked to verify", async () => {
      await page.goto(REGISTER_PATH);
      await page.getByLabel("Name").fill(username);
      await page.getByLabel("Email").fill(email);
      await page.getByLabel("Password", { exact: true }).fill("E2ePassword123");
      await page.getByLabel("Confirm password").fill("E2ePassword123");
      await page.getByRole("button", { name: "Create account" }).click();
      await expect(page.getByText("Account created. Check your email to verify, then log in.")).toBeVisible();
      await page.waitForURL(/\/login/);
    });

    await test.step("logs in as an existing user", async () => {
      await page.getByLabel("Email").fill("test-agent-2@hittiguess.local");
      await page.getByLabel("Password", { exact: true }).fill("HittiguessTestAgent2!2026");
      await page.getByRole("button", { name: "Log in" }).click();
      await page.waitForURL(PLAYLISTS_PATH);
      await expect(page.getByRole("heading", { name: "Your playlists" })).toBeVisible();
    });

    let playlistId = 0;
    await test.step("creates a playlist from the library", async () => {
      await page.getByRole("button", { name: "Create playlist" }).click();
      await page.waitForURL(/\/playlists\/\d+/);
      playlistId = Number(new URL(page.url()).pathname.split("/")[2]);
      expect(playlistId).toBeGreaterThan(0);
    });

    await test.step("adds a song to the playlist", async () => {
      const songResponse = await page.request.post(apiUrl(`/api/playlists/${playlistId}/songs`), {
        data: {
          artist: "Core Flows artist",
          title: "Core Flows song",
          releaseYear: 2001,
          youtubeId: KNOWN_VIDEO_ID,
          color: "a6e3a1",
        },
        headers: await csrfHeaders(page),
      });
      expect(songResponse.ok(), `${songResponse.status()} ${await songResponse.text()}`).toBeTruthy();

      await page.reload();
      await expect(page.getByRole("button", { name: "Remove from playlist" })).toBeVisible();
      expect(await playlistSongIds(page, playlistId)).toHaveLength(1);
    });

    await test.step("edits the song title", async () => {
      const [songId] = await playlistSongIds(page, playlistId);
      await page.goto(`/playlists/${playlistId}/songs/${songId}`);
      await page.getByLabel("Title").fill("Edited Title");
      await page.getByRole("button", { name: "Save changes" }).click();
      await expect(page.getByLabel("Title")).toHaveValue("Edited Title");
    });

    await test.step("exports the playlist cards as PDF", async () => {
      await page.goto(`/playlists/${playlistId}`);
      await page.getByTitle("Export cards").click();
      const downloadPromise = page.waitForEvent("download", {
        timeout: PLAYLIST_EXPORT_TIMEOUT_MILLISECONDS,
      });
      await page.getByRole("button", { name: "Download PDF" }).click();
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toBe(`playlist-${playlistId}-info-A4.pdf`);
    });

    await test.step("deletes the playlist behind its warning", async () => {
      await page.goto(`/playlists/${playlistId}/edit`);
      await page.getByRole("button", { name: "Delete" }).click();
      await page.getByRole("button", { name: "Delete playlist" }).click();
      await page.waitForURL(PLAYLISTS_PATH);
      const response = await page.request.get(apiUrl(`/api/playlists/${playlistId}`), {
        headers: await csrfHeaders(page),
      });
      expect(response.status()).toBe(404);
    });
  } finally {
    await context.close();
  }
});
