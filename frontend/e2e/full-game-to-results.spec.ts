import { expect, test, type Browser, type Page } from "@playwright/test";

const ADMIN_ACCOUNT = { email: "test-agent-1@hittiguess.local", password: "HittiguessTestAgent1!2026" };
const MEMBER_ACCOUNT = { email: "test-agent-2@hittiguess.local", password: "HittiguessTestAgent2!2026" };
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
const GROUPS_PATH = "/api/groups";
const PLAYLISTS_API_PATH = "/api/users/me/playlists";
const MEMBER_DISPLAY_NAME = "Full game member";
const SONG_COLOR = "89b4fa";
const WIN_CONDITION_CARD_COUNT = 5;
// Two more songs than the start minimum, so the game can end on the win condition at a
// round's end rather than only by running out of songs.
const SONG_COUNT = 12;
const FIRST_SONG_RELEASE_YEAR = 1980;
const SONG_NUMBER_WIDTH = 2;
const FULL_GAME_TIMEOUT_MILLISECONDS = 300_000;
const TURN_POLL_MILLISECONDS = 500;
const RESULTS_URL_PATTERN = /\/sessions\/\d+\/results\?group=\d+/;
const YOUR_CARD_LABEL = "Your card. Choose a timeline position.";

interface GroupResponse { id: number; inviteCode: string; }

function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

async function csrfHeaders(page: Page): Promise<Record<string, string>> {
  const csrfCookie = (await page.context().cookies(API_BASE_URL)).find((cookie) => cookie.name === "XSRF-TOKEN");
  return csrfCookie ? { "X-XSRF-TOKEN": csrfCookie.value } : {};
}

async function login(browser: Browser, account: { email: string; password: string }): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto("/login");
  await page.getByLabel("Email").fill(account.email);
  await page.getByLabel("Password", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByRole("heading", { name: "Your playlists" })).toBeVisible();
  return page;
}

async function leaveActiveGroup(page: Page): Promise<void> {
  const response = await page.request.get(apiUrl(`${GROUPS_PATH}/active`));
  const body = await response.text();
  if (response.ok() && body.trim()) {
    const group = JSON.parse(body) as GroupResponse;
    await page.request.post(apiUrl(`${GROUPS_PATH}/${group.id}/leave`), { headers: await csrfHeaders(page) });
  }
}

async function seedGroup(adminPage: Page, memberPage: Page): Promise<GroupResponse> {
  const groupResponse = await adminPage.request.post(apiUrl(GROUPS_PATH), { data: {}, headers: await csrfHeaders(adminPage) });
  expect(groupResponse.ok(), await groupResponse.text()).toBeTruthy();
  const group = await groupResponse.json() as GroupResponse;
  const joinResponse = await memberPage.request.post(apiUrl(`${GROUPS_PATH}/join`), {
    data: { inviteCode: group.inviteCode, displayName: MEMBER_DISPLAY_NAME },
    headers: await csrfHeaders(memberPage),
  });
  expect(joinResponse.ok(), await joinResponse.text()).toBeTruthy();

  const playlist = await (await adminPage.request.post(apiUrl(PLAYLISTS_API_PATH), { headers: await csrfHeaders(adminPage) })).json() as { id: number };
  for (let songIndex = 0; songIndex < SONG_COUNT; songIndex++) {
    const songNumber = String(songIndex + 1).padStart(SONG_NUMBER_WIDTH, "0");
    const songResponse = await adminPage.request.post(apiUrl(`/api/playlists/${playlist.id}/songs`), {
      data: { artist: `Full game artist ${songNumber}`, title: `Full game song ${songNumber}`, releaseYear: FIRST_SONG_RELEASE_YEAR + songIndex, youtubeId: `fullGameS${songNumber}`, color: SONG_COLOR },
      headers: await csrfHeaders(adminPage),
    });
    expect(songResponse.ok(), await songResponse.text()).toBeTruthy();
  }
  const settingsResponse = await adminPage.request.patch(apiUrl(`${GROUPS_PATH}/${group.id}/settings`), {
    data: { playlistIds: [playlist.id], djMode: "ROTATING", winConditionCardCount: WIN_CONDITION_CARD_COUNT },
    headers: await csrfHeaders(adminPage),
  });
  expect(settingsResponse.ok()).toBeTruthy();
  return group;
}

// Whoever holds the turn drops the card at the end of their timeline and locks it in,
// checking on the way that the guess fields stay next to the lock-in button.
async function playTurnIfActive(page: Page): Promise<void> {
  const card = page.getByRole("button", { name: YOUR_CARD_LABEL }).first();
  if (!(await card.isVisible().catch(() => false))) return;
  await card.press("Enter");
  const lockIn = page.getByRole("button", { name: "Lock in answer" });
  if (!(await lockIn.isVisible().catch(() => false))) return;
  await expect(page.getByRole("textbox", { name: "Guess the title" })).toBeVisible();
  await lockIn.click();
}

test("a full game ends on the results screen for every player, and the lobby reopens", async ({ browser }) => {
  test.setTimeout(FULL_GAME_TIMEOUT_MILLISECONDS);
  const [adminPage, memberPage] = await Promise.all([login(browser, ADMIN_ACCOUNT), login(browser, MEMBER_ACCOUNT)]);
  await leaveActiveGroup(adminPage);
  await leaveActiveGroup(memberPage);
  const group = await seedGroup(adminPage, memberPage);

  try {
    await Promise.all([adminPage.goto(`/groups/${group.id}`), memberPage.goto(`/groups/${group.id}`)]);
    await expect(adminPage.getByText(MEMBER_DISPLAY_NAME)).toBeVisible();
    await adminPage.waitForTimeout(TURN_POLL_MILLISECONDS * 4);
    const startResponse = await adminPage.request.post(apiUrl(`${GROUPS_PATH}/${group.id}/start-session`), { headers: await csrfHeaders(adminPage) });
    expect(startResponse.ok(), await startResponse.text()).toBeTruthy();
    await Promise.all([adminPage.waitForURL(/\/sessions\/\d+$/), memberPage.waitForURL(/\/sessions\/\d+$/)]);

    const deadline = Date.now() + FULL_GAME_TIMEOUT_MILLISECONDS;
    while (!(RESULTS_URL_PATTERN.test(adminPage.url()) && RESULTS_URL_PATTERN.test(memberPage.url())) && Date.now() < deadline) {
      await playTurnIfActive(adminPage);
      await playTurnIfActive(memberPage);
      await adminPage.waitForTimeout(TURN_POLL_MILLISECONDS);
    }

    for (const page of [adminPage, memberPage]) {
      await expect(page).toHaveURL(RESULTS_URL_PATTERN);
      await expect(page.getByRole("heading", { name: "Game Over" })).toBeVisible();
      await expect(page.getByText("FINAL RANKING")).toBeVisible();
    }

    await memberPage.getByRole("link", { name: "Back to lobby" }).click();
    await expect(memberPage).toHaveURL(new RegExp(`/groups/${group.id}$`));
    await expect(memberPage.getByRole("heading", { name: "Group Lobby" })).toBeVisible();
    await memberPage.waitForTimeout(TURN_POLL_MILLISECONDS * 4);
    await expect(memberPage).toHaveURL(new RegExp(`/groups/${group.id}$`));
  } finally {
    await leaveActiveGroup(memberPage);
    await leaveActiveGroup(adminPage);
  }
});
