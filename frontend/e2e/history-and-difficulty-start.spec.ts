import { expect, test, type Page } from "@playwright/test";

const DESKTOP = { width: 1440, height: 900 };
const GROUP_ID = 1;
const SUMMARY_ID = 7;
const USER_ID = 11;
const HTTP_OK = 200;
const HTTP_NO_CONTENT = 204;
const HTTP_CONFLICT = 409;
const CUSTOM_PLAYLIST = { id: 7, name: "Road Trip", songCount: 32, color: "cba6f7", previewYoutubeIds: [] };
const API_PATTERN = "**/api/**";
const THEMES = ["dark", "light"] as const;
const summary = {
  id: SUMMARY_ID, groupName: "Group ABCD", startedAt: "2026-10-09T10:00:00Z", endedAt: "2026-10-09T10:18:00Z",
  mode: "DIFFICULTY", difficultyTier: "MEDIUM", winTargetCards: 8, participantCount: 2, turnsPlayed: 16,
  endingReason: "TARGET_REACHED", rulesVersion: "full-pass-v1",
  participants: [
    { id: 1, userId: USER_ID, displayName: "Sam", participationStatus: "FINISHED", finalCardCount: 8, cardRank: 1, artistRank: 1, titleRank: 1,
      isWinner: true, placementAttempts: 9, correctPlacements: 7, titleAttempts: 8, correctTitles: 5, artistAttempts: 10, correctArtists: 6, betsPlaced: 2, betsWon: 1 },
    { id: 2, userId: null, displayName: "Deleted player", participationStatus: "LEFT", finalCardCount: 8, cardRank: 1, artistRank: 2, titleRank: 2,
      isWinner: true, placementAttempts: 8, correctPlacements: 6, titleAttempts: 8, correctTitles: 4, artistAttempts: 11, correctArtists: 5, betsPlaced: 3, betsWon: 2 },
  ],
};
async function fixture(page: Page, theme: string, failStart = false) {
  const requests: string[] = [];
  let tier: string | null = "MEDIUM";
  let customSelected = false;
  await page.setViewportSize(DESKTOP);
  await page.context().addCookies([{ name: "session_hint", value: "history-fixture", url: "http://localhost:3000" }]);
  await page.addInitScript((selectedTheme) => localStorage.setItem("theme", selectedTheme), theme);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.route(API_PATTERN, async (route) => {
    const path = new URL(route.request().url()).pathname;
    const method = route.request().method();
    requests.push(`${method} ${path}`);
    if (path.endsWith("/settings") && method === "PATCH") {
      const settings = route.request().postDataJSON();
      if (settings.playlistIds) { tier = null; customSelected = true; }
      else tier = settings.difficultyTier;
    }
    const group = { id: GROUP_ID, joinCode: "ABCD", inviteCode: "fixture", status: "OPEN", djMode: "ROTATING", winConditionCardCount: 8,
      difficultyTier: tier, playlists: customSelected ? [CUSTOM_PLAYLIST] : [], members: [
        { id: 1, userId: USER_ID, displayName: "Sam", isAdmin: true, isConnected: true, isInVoice: false },
        { id: 2, userId: 12, displayName: "Alex", isAdmin: false, isConnected: true, isInVoice: false },
      ] };
    let body: unknown = {};
    let status = HTTP_OK;
    if (path === "/api/users/me") body = { id: USER_ID, username: "Sam", role: "USER" };
    else if (path === "/api/users/me/statistics") body = { gamesPlayed: 12, wins: 5, interruptedGames: 1, placementAttempts: 9, correctPlacements: 7, correctTitles: 5, correctArtists: 6 };
    else if (path === "/api/users/me/history") body = { items: [summary], page: 0, pageSize: 20, total: 1 };
    else if (path === `/api/users/me/history/${SUMMARY_ID}`) body = summary;
    else if (path === `/api/groups/${GROUP_ID}` || path.endsWith("/settings") || path.endsWith("/start-session")) body = group;
    else if (path === "/api/users/me/playlists") body = [CUSTOM_PLAYLIST];
    else if (path === "/api/groups/active") { status = HTTP_NO_CONTENT; body = undefined; }
    else if (path.endsWith("/playlists") || path.includes("/chat/messages")) body = [];
    else if (path.includes("/active") || path.includes("/import")) { status = HTTP_NO_CONTENT; body = undefined; }
    if (path.endsWith("/start-session") && failStart) { status = HTTP_CONFLICT; body = { message: "Not enough internationally known verified songs" }; }
    await route.fulfill({ status, contentType: "application/json", body: body === undefined ? undefined : JSON.stringify(body),
      headers: { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-credentials": "true" } });
  });
  return requests;
}
for (const theme of THEMES) {
  test(`${theme}: participant history and difficulty selection at Start`, async ({ page }, testInfo) => {
    const requests = await fixture(page, theme);
    await page.goto("/history");
    await expect(page.getByRole("heading", { name: "Game history", exact: true })).toBeVisible();
    await expect(page.getByText("42%", { exact: true })).toBeVisible();
    await expect(page.getByText("Joint winner", { exact: true })).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath("history.png"), animations: "disabled" });
    await page.getByRole("link", { name: "View results for Group ABCD" }).click();
    await expect(page.getByText("Deleted player · Winner · Left", { exact: true })).toBeVisible();
    await expect(page.getByText("7 / 9", { exact: true })).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath("history-detail.png"), animations: "disabled" });
    await page.goto(`/groups/${GROUP_ID}`);
    await page.getByRole("button", { name: "Choose playlists" }).click();
    await page.getByRole("button", { name: "Hard", exact: true }).click();
    await page.getByRole("button", { name: "Confirm", exact: true }).click();
    await expect(page.getByText("Hard difficulty", { exact: true })).toBeVisible();
    expect(requests.some((request) => request.includes("/session/generate"))).toBe(false);
    expect(requests.some((request) => request.includes("/start-session"))).toBe(false);
    await page.getByRole("button", { name: "Start game", exact: true }).click();
    await expect.poll(() => requests.includes(`POST /api/groups/${GROUP_ID}/start-session`)).toBe(true);
  });
}

test("desktop: Custom selection persists without starting a game", async ({ page }) => {
  const requests = await fixture(page, "dark");
  await page.goto(`/groups/${GROUP_ID}`);
  await page.getByRole("button", { name: "Choose playlists" }).click();
  await page.getByRole("button", { name: "Custom", exact: true }).click();
  await page.getByRole("button", { name: "Road Trip 32 songs" }).click();
  await page.getByRole("button", { name: "Confirm", exact: true }).click();
  await expect(page.getByRole("button", { name: "Choose playlists" }).getByText("Road Trip")).toBeVisible();
  expect(requests.some((request) => request.includes("/start-session"))).toBe(false);
  await page.getByRole("button", { name: "Start game", exact: true }).click();
  await expect.poll(() => requests.includes(`POST /api/groups/${GROUP_ID}/start-session`)).toBe(true);
});

test("desktop: insufficient tier catalog keeps the lobby open with an error", async ({ page }) => {
  await fixture(page, "light", true);
  await page.goto(`/groups/${GROUP_ID}`);
  await page.getByRole("button", { name: "Start game", exact: true }).click();
  await expect(page.getByText("Not enough internationally known verified songs", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Start game", exact: true })).toBeEnabled();
});
