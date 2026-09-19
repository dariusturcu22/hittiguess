import { expect, test, type Browser, type Page } from "@playwright/test";

const ADMIN_ACCOUNT = {
  email: "test-agent-1@hittiguess.local",
  password: "HittiguessTestAgent1!2026",
};
const MEMBER_ACCOUNT = {
  email: "test-agent-2@hittiguess.local",
  password: "HittiguessTestAgent2!2026",
};
const MEMBER_DISPLAY_NAME = "Batch E lobby member";
const CHAT_MESSAGE = "Batch E realtime chat check";
const LIVE_CONNECTION_TEXT = "Live";
const LOGIN_PATH = "/login";
const PLAYLISTS_PATH = "/playlists";
const GROUPS_PATH = "/api/groups";
const GROUP_CLEANUP_TIMEOUT_MILLISECONDS = 10_000;
const PLAYLISTS_API_PATH = "/api/users/me/playlists";
const SONG_COLOR = "a6e3a1";
const MINIMUM_GAMEPLAY_SONG_COUNT = 3;
const RESULTS_SESSION_ID = 777;
const RESULTS_GROUP_ID = 888;
const SESSION_API_PATH = `/api/sessions/${RESULTS_SESSION_ID}`;
const RESULTS_API_PATH = `/api/sessions/groups/${RESULTS_GROUP_ID}/results`;
const RESULTS_FILE_NAME = `hittiguess-results-${RESULTS_SESSION_ID}.csv`;
const GAMEPLAY_SESSION_ID = 779;
const GAMEPLAY_SESSION_API_PATH = `/api/sessions/${GAMEPLAY_SESSION_ID}`;
const CURRENT_USER_API_PATH = "/api/users/me";
const LINK_OUT_API_PATH = `/api/sessions/${GAMEPLAY_SESSION_ID}/round/link-out`;
const PLAYER_ID = 10;
const DJ_ID = 20;

interface TestAccount {
  email: string;
  password: string;
}

interface GroupResponse {
  id: number;
  inviteCode: string;
}

interface PlaylistResponse {
  id: number;
}

async function login(browser: Browser, account: TestAccount): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto(LOGIN_PATH);
  await page.getByLabel("Email").fill(account.email);
  await page.getByLabel("Password").fill(account.password);
  await page.getByRole("button", { name: "Log in" }).click();
  await page.waitForURL(PLAYLISTS_PATH);

  return page;
}

async function createGroupFromSidebar(page: Page): Promise<GroupResponse> {
  await page.getByTitle("Create group lobby").click();
  await page.waitForURL(/\/groups\/\d+/);
  const response = await page.request.get(`${GROUPS_PATH}/active`);
  expect(response.ok()).toBeTruthy();
  return response.json() as Promise<GroupResponse>;
}

async function joinGroup(page: Page, inviteCode: string): Promise<void> {
  const response = await page.request.post(`${GROUPS_PATH}/join`, {
    data: { inviteCode, displayName: MEMBER_DISPLAY_NAME },
  });
  expect(response.ok()).toBeTruthy();
}

async function configurePlayablePlaylist(page: Page, groupId: number): Promise<void> {
  const playlistResponse = await page.request.post(PLAYLISTS_API_PATH);
  expect(playlistResponse.ok()).toBeTruthy();
  const playlist = await playlistResponse.json() as PlaylistResponse;
  const songs = [
    { artist: "Batch E artist one", title: "Batch E song one", releaseYear: 1998, youtubeId: "dQw4w9WgXcQ" },
    { artist: "Batch E artist two", title: "Batch E song two", releaseYear: 2003, youtubeId: "3JZ_D3ELwOQ" },
    { artist: "Batch E artist three", title: "Batch E song three", releaseYear: 2009, youtubeId: "L_jWHffIx5E" },
  ];
  expect(songs).toHaveLength(MINIMUM_GAMEPLAY_SONG_COUNT);
  for (const song of songs) {
    const songResponse = await page.request.post(`/api/playlists/${playlist.id}/songs`, {
      data: { ...song, color: SONG_COLOR },
    });
    expect(songResponse.ok()).toBeTruthy();
  }
  const settingsResponse = await page.request.patch(`${GROUPS_PATH}/${groupId}/settings`, {
    data: { playlistIds: [playlist.id], djMode: "ROTATING", winConditionCardCount: 5 },
  });
  expect(settingsResponse.ok()).toBeTruthy();
}

async function leaveGroup(page: Page, groupId: number): Promise<void> {
  await page.request.post(`${GROUPS_PATH}/${groupId}/leave`, {
    timeout: GROUP_CLEANUP_TIMEOUT_MILLISECONDS,
  });
}

test.describe.configure({ mode: "serial" });

test("group lobby joins members, persists settings, relays chat, and starts a session", async ({ browser }) => {
  const [adminPage, memberPage] = await Promise.all([
    login(browser, ADMIN_ACCOUNT),
    login(browser, MEMBER_ACCOUNT),
  ]);
  let groupId: number | undefined;

  try {
    const group = await createGroupFromSidebar(adminPage);
    groupId = group.id;
    await joinGroup(memberPage, group.inviteCode);

    await Promise.all([
      adminPage.goto(`/groups/${group.id}`),
      memberPage.goto(`/groups/${group.id}`),
    ]);
    await expect(adminPage.getByRole("heading", { name: "Group Lobby" })).toBeVisible();
    await expect(memberPage.getByText(MEMBER_DISPLAY_NAME)).toBeVisible();

    await adminPage.getByRole("button", { name: "Settings" }).click();
    await adminPage.getByLabel("DJ mode").selectOption("ROTATING");
    await adminPage.getByRole("button", { name: "Save changes" }).click();
    await expect(adminPage.getByRole("button", { name: "Settings" })).toBeVisible();

    await adminPage.getByRole("button", { name: "Chat" }).click();
    await expect(adminPage.getByText(LIVE_CONNECTION_TEXT)).toBeVisible();
    await adminPage.getByPlaceholder("Type a message...").fill(CHAT_MESSAGE);
    await adminPage.getByRole("button", { name: "Send message" }).click();

    await memberPage.getByRole("button", { name: "Chat" }).click();
    await expect(memberPage.getByText(CHAT_MESSAGE)).toBeVisible();

    await configurePlayablePlaylist(adminPage, group.id);
    await adminPage.reload();
    await adminPage.getByRole("button", { name: "Start game" }).click();
    await expect(adminPage).toHaveURL(/\/sessions\/\d+/);
  } finally {
    if (groupId !== undefined) {
      await Promise.allSettled([
        leaveGroup(memberPage, groupId),
        leaveGroup(adminPage, groupId),
      ]);
    }
    await Promise.all([adminPage.context().close(), memberPage.context().close()]);
  }
});

test("results screen exports the final ranking", async ({ browser }) => {
  const page = await login(browser, ADMIN_ACCOUNT);
  const session = {
    id: RESULTS_SESSION_ID,
    groupId: RESULTS_GROUP_ID,
    status: "COMPLETED",
    djMode: "ROTATING",
    winConditionCardCount: 5,
    currentRoundNumber: 4,
    players: [],
  };
  const results = {
    groupId: RESULTS_GROUP_ID,
    cardCountRanking: [
      { playerId: 1, displayName: "Winner", cardCount: 5, rank: 1 },
      { playerId: 2, displayName: "Runner up", cardCount: 3, rank: 2 },
    ],
    mostArtistsGuessed: [{ playerId: 1, displayName: "Winner", value: 4 }],
    mostTitlesGuessed: [{ playerId: 2, displayName: "Runner up", value: 3 }],
  };

  try {
    await page.route(`**${SESSION_API_PATH}`, (route) => route.fulfill({ json: session }));
    await page.route(`**${RESULTS_API_PATH}`, (route) => route.fulfill({ json: results }));
    await page.goto(`/sessions/${RESULTS_SESSION_ID}/results`);

    await expect(page.getByRole("heading", { name: "Game Over" })).toBeVisible();
    const downloadPromise = page.waitForEvent("download");
    await page.getByRole("button", { name: "Download results" }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toBe(RESULTS_FILE_NAME);
  } finally {
    await page.context().close();
  }
});

test("gameplay shell renders placement, betting, and DJ link-out states", async ({ browser }) => {
  const page = await login(browser, ADMIN_ACCOUNT);
  const player = {
    id: PLAYER_ID,
    displayName: "Player one",
    tokenCount: 2,
    timeline: [{ songId: 100, title: "Anchor", releaseYear: 1999, position: 0 }],
  };
  const dj = {
    id: DJ_ID,
    displayName: "DJ two",
    tokenCount: 1,
    timeline: [{ songId: 200, title: "Second anchor", releaseYear: 2004, position: 0 }],
  };
  const baseSession = {
    id: GAMEPLAY_SESSION_ID,
    groupId: RESULTS_GROUP_ID,
    status: "IN_PROGRESS",
    djMode: "ROTATING",
    winConditionCardCount: 5,
    currentRoundNumber: 4,
  };

  try {
    await page.route(`**${CURRENT_USER_API_PATH}`, (route) => route.fulfill({ json: { id: PLAYER_ID } }));
    await page.route(`**${GAMEPLAY_SESSION_API_PATH}`, (route) => route.fulfill({ json: {
      ...baseSession,
      players: [player, dj],
      currentRound: { roundNumber: 4, activePlayerId: PLAYER_ID, djPlayerId: DJ_ID, status: "AWAITING_PLACEMENT" },
    } }));
    await page.goto(`/sessions/${GAMEPLAY_SESSION_ID}`);
    const card = page.locator('[draggable="true"]');
    await expect(card).toBeVisible();
    await page.getByRole("button", { name: "Open chat" }).click();
    await expect(page.getByRole("heading", { name: "Chat" })).toBeVisible();
    await page.getByRole("button", { name: "Close chat" }).click();
    await card.dispatchEvent("dragstart");
    await expect(page.getByRole("button", { name: "Place card at timeline position 1" })).toBeVisible();

    await page.unroute(`**${GAMEPLAY_SESSION_API_PATH}`);
    await page.route(`**${GAMEPLAY_SESSION_API_PATH}`, (route) => route.fulfill({ json: {
      ...baseSession,
      players: [player, dj],
      currentRound: { roundNumber: 4, activePlayerId: DJ_ID, djPlayerId: DJ_ID, status: "BETTING" },
    } }));
    await page.reload();
    await page.getByRole("button", { name: "Use 1 token to bet" }).click();
    await expect(page.getByRole("button", { name: "Choose a timeline gap" })).toBeVisible();
    await page.getByRole("button", { name: "Skip betting" }).click();
    await expect(page.getByText("Betting skipped. Waiting for the reveal.")).toBeVisible();

    await page.unroute(`**${CURRENT_USER_API_PATH}`);
    await page.route(`**${CURRENT_USER_API_PATH}`, (route) => route.fulfill({ json: { id: DJ_ID } }));
    await page.unroute(`**${GAMEPLAY_SESSION_API_PATH}`);
    await page.route(`**${GAMEPLAY_SESSION_API_PATH}`, (route) => route.fulfill({ json: {
      ...baseSession,
      players: [player, dj],
      currentRound: { roundNumber: 4, activePlayerId: PLAYER_ID, djPlayerId: DJ_ID, status: "AWAITING_PLACEMENT" },
    } }));
    await page.route(`**${LINK_OUT_API_PATH}`, (route) => route.fulfill({
      json: { watchUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ" },
    }));
    await page.addInitScript(() => {
      window.open = () => null;
    });
    await page.goto(`/sessions/${GAMEPLAY_SESSION_ID}`);
    await page.getByRole("button", { name: "Open on YouTube to play" }).click();
    await expect(page.getByText("Shares your tab or system audio with the group.")).toBeVisible();
  } finally {
    await page.context().close();
  }
});
