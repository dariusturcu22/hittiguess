import { expect, test, type Browser, type Page } from "@playwright/test";

const ADMIN_ACCOUNT = {
  email: "test-agent-2@hittiguess.local",
  password: "HittiguessTestAgent2!2026",
};
const MEMBER_ACCOUNT = {
  email: "test-agent-3@hittiguess.local",
  password: "HittiguessTestAgent3!2026",
};
const MEMBER_DISPLAY_NAME = "Story 47 round member";
const LOGIN_PATH = "/login";
const GROUPS_PATH = "/api/groups";
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
const GROUP_CLEANUP_TIMEOUT_MILLISECONDS = 10_000;
const PLAYLISTS_API_PATH = "/api/users/me/playlists";
const CURRENT_USER_API_PATH = "/api/users/me";
const SONG_COLOR = "a6e3a1";
const REVEAL_WAIT_TIMEOUT_MILLISECONDS = 60_000;
const IMPORT_DONE_POLL_INTERVAL_MILLISECONDS = 5_000;
const IMPORT_DONE_TIMEOUT_MILLISECONDS = 240_000;
const IMPORT_GONE_WAIT_TIMEOUT_MILLISECONDS = 15_000;
const KNOWN_VIDEO_ID = "dQw4w9WgXcQ";
const LOBBY_PLAYER_COUNT = 2;
const WIN_CONDITION_CARD_COUNT = 5;
// A session needs a song per card every player could win with.
const MINIMUM_GAMEPLAY_SONG_COUNT = LOBBY_PLAYER_COUNT * WIN_CONDITION_CARD_COUNT;
const FILLER_SONG_FIRST_RELEASE_YEAR = 1980;
const SONG_NUMBER_WIDTH = 2;

interface TestAccount {
  email: string;
  password: string;
}

interface GroupResponse {
  id: number;
  inviteCode: string;
  status: string;
}

interface PlaylistResponse {
  id: number;
}

interface SessionPlayer {
  id: number;
  userId: number;
}

interface SessionResponse {
  id: number;
  players: SessionPlayer[];
  currentRound: { activePlayerId: number; status: string; roundNumber: number } | null;
}

const REVEAL_POLL_INTERVAL_MILLISECONDS = 2_000;

function apiUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

async function csrfHeaders(page: Page): Promise<Record<string, string>> {
  const csrfCookie = (await page.context().cookies(API_BASE_URL)).find(
    (cookie) => cookie.name === "XSRF-TOKEN",
  );
  return csrfCookie ? { "X-XSRF-TOKEN": csrfCookie.value } : {};
}

async function login(browser: Browser, account: TestAccount): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto(LOGIN_PATH);
  await page.getByLabel("Email").fill(account.email);
  await page.getByLabel("Password", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByRole("heading", { name: "Your playlists" })).toBeVisible();

  return page;
}

async function leaveActiveGroup(page: Page): Promise<void> {
  const activeGroupResponse = await page.request.get(apiUrl(`${GROUPS_PATH}/active`));
  const activeGroupBody = await activeGroupResponse.text();
  if (activeGroupResponse.ok() && activeGroupBody.trim()) {
    const activeGroup = JSON.parse(activeGroupBody) as GroupResponse;
    await page.request.post(apiUrl(`${GROUPS_PATH}/${activeGroup.id}/leave`), {
      headers: await csrfHeaders(page),
      timeout: GROUP_CLEANUP_TIMEOUT_MILLISECONDS,
    });
    await page.reload();
  }
}

async function readActiveGroup(page: Page): Promise<GroupResponse | null> {
  const activeGroupResponse = await page.request.get(apiUrl(`${GROUPS_PATH}/active`));
  const activeGroupBody = await activeGroupResponse.text();
  if (activeGroupResponse.ok() && activeGroupBody.trim()) {
    return JSON.parse(activeGroupBody) as GroupResponse;
  }
  return null;
}

async function createGroup(page: Page): Promise<GroupResponse> {
  await leaveActiveGroup(page);
  await page.getByTitle("Create group lobby").click();
  await page.waitForURL(/\/groups\/\d+/);
  const response = await page.request.get(apiUrl(`${GROUPS_PATH}/active`));
  expect(response.ok(), `${response.status()} ${await response.text()}`).toBeTruthy();
  return response.json() as Promise<GroupResponse>;
}

async function joinGroup(page: Page, inviteCode: string): Promise<void> {
  const response = await page.request.post(apiUrl(`${GROUPS_PATH}/join`), {
    data: { inviteCode, displayName: MEMBER_DISPLAY_NAME },
    headers: await csrfHeaders(page),
  });
  expect(response.ok(), `${response.status()} ${await response.text()}`).toBeTruthy();
}

async function seedPlayablePlaylist(page: Page): Promise<number> {
  const playlistResponse = await page.request.post(apiUrl(PLAYLISTS_API_PATH), {
    headers: await csrfHeaders(page),
  });
  expect(playlistResponse.ok()).toBeTruthy();
  const playlist = await playlistResponse.json() as PlaylistResponse;
  const songs = [
    { artist: "Story 47 artist one", title: "Story 47 song one", releaseYear: 1998, youtubeId: "dQw4w9WgXcQ" },
    { artist: "Story 47 artist two", title: "Story 47 song two", releaseYear: 2003, youtubeId: "3JZ_D3ELwOQ" },
    { artist: "Story 47 artist three", title: "Story 47 song three", releaseYear: 2009, youtubeId: "L_jWHffIx5E" },
    { artist: "Story 47 artist four", title: "Story 47 song four", releaseYear: 2005, youtubeId: "jNQXAC9IVRw" },
    { artist: "Story 47 artist five", title: "Story 47 song five", releaseYear: 2012, youtubeId: "9bZkp7q19f0" },
  ];
  const fillerSongs = Array.from({ length: MINIMUM_GAMEPLAY_SONG_COUNT - songs.length }, (unusedEntry, fillerIndex) => {
    const songNumber = String(fillerIndex + 1).padStart(SONG_NUMBER_WIDTH, "0");
    return {
      artist: `Story 47 filler artist ${songNumber}`,
      title: `Story 47 filler song ${songNumber}`,
      releaseYear: FILLER_SONG_FIRST_RELEASE_YEAR + fillerIndex,
      youtubeId: `story47fl${songNumber}`,
    };
  });
  songs.push(...fillerSongs);
  for (const song of songs) {
    const songResponse = await page.request.post(apiUrl(`/api/playlists/${playlist.id}/songs`), {
      data: { ...song, color: SONG_COLOR },
      headers: await csrfHeaders(page),
    });
    expect(songResponse.ok()).toBeTruthy();
  }
  return playlist.id;
}

async function configurePlaylist(page: Page, groupId: number, playlistId: number): Promise<void> {
  const settingsResponse = await page.request.patch(apiUrl(`${GROUPS_PATH}/${groupId}/settings`), {
    data: { playlistIds: [playlistId], djMode: "ROTATING", winConditionCardCount: WIN_CONDITION_CARD_COUNT },
    headers: await csrfHeaders(page),
  });
  expect(settingsResponse.ok()).toBeTruthy();
}

async function leaveGroup(page: Page, groupId: number): Promise<void> {
  await page.request.post(apiUrl(`${GROUPS_PATH}/${groupId}/leave`), {
    timeout: GROUP_CLEANUP_TIMEOUT_MILLISECONDS,
    headers: await csrfHeaders(page),
  });
}

async function currentUserId(page: Page): Promise<number> {
  const response = await page.request.get(apiUrl(CURRENT_USER_API_PATH));
  expect(response.ok()).toBeTruthy();
  const body = await response.json() as { id: number };
  return body.id;
}

async function readSession(page: Page, sessionId: number): Promise<SessionResponse> {
  const response = await page.request.get(apiUrl(`/api/sessions/${sessionId}`));
  expect(response.ok()).toBeTruthy();
  return response.json() as Promise<SessionResponse>;
}

// The scored round holds on its reveal before the next one starts, so round 2 existing
// is what proves round 1 fully resolved.
async function waitForRoundTwo(page: Page, sessionId: number): Promise<void> {
  const deadline = Date.now() + REVEAL_WAIT_TIMEOUT_MILLISECONDS;
  for (;;) {
    const session = await readSession(page, sessionId);
    const round = session.currentRound;
    if (round !== null && round.roundNumber > 1) {
      return;
    }
    if (Date.now() > deadline) {
      throw new Error(`Round 2 did not start in time, round 1 stuck on ${round?.status ?? "no round"}`);
    }
    await page.waitForTimeout(REVEAL_POLL_INTERVAL_MILLISECONDS);
  }
}

test.describe.configure({ mode: "serial" });

test("two players complete a full round to the reveal", async ({ browser }) => {
  test.setTimeout(REVEAL_WAIT_TIMEOUT_MILLISECONDS * 3);
  const [adminPage, memberPage] = await Promise.all([
    login(browser, ADMIN_ACCOUNT),
    login(browser, MEMBER_ACCOUNT),
  ]);

  // Never disturb a live game: both accounts must be free before this test
  // touches any group state.
  const [adminActiveGroup, memberActiveGroup] = await Promise.all([
    readActiveGroup(adminPage),
    readActiveGroup(memberPage),
  ]);
  if (adminActiveGroup?.status === "LOCKED" || memberActiveGroup?.status === "LOCKED") {
    test.skip(true, "A test account is mid-game; skipping the live round.");
  }

  let groupId: number | undefined;

  try {
    await leaveActiveGroup(memberPage);
    const group = await createGroup(adminPage);
    groupId = group.id;
    await joinGroup(memberPage, group.inviteCode);
    const playlistId = await seedPlayablePlaylist(adminPage);
    await configurePlaylist(adminPage, group.id, playlistId);

    await Promise.all([
      adminPage.goto(`/groups/${group.id}`),
      memberPage.goto(`/groups/${group.id}`),
    ]);
    await adminPage.getByRole("button", { name: "Start game" }).click();
    await expect(adminPage).toHaveURL(/\/sessions\/\d+/);
    const sessionId = Number(new URL(adminPage.url()).pathname.split("/")[2]);
    await memberPage.goto(`/sessions/${sessionId}`);

    const session = await readSession(adminPage, sessionId);
    const activePlayer = session.players.find((player) => player.id === session.currentRound?.activePlayerId);
    expect(activePlayer).toBeDefined();
    const [adminUserId, memberUserId] = await Promise.all([
      currentUserId(adminPage),
      currentUserId(memberPage),
    ]);
    let activePage: Page;
    if (activePlayer?.userId === memberUserId) {
      activePage = memberPage;
    } else {
      expect(activePlayer?.userId).toBe(adminUserId);
      activePage = adminPage;
    }

    await activePage.getByRole("button", { name: "Your card. Choose a timeline position." }).focus();
    await activePage.keyboard.press("Enter");
    await activePage.getByRole("button", { name: "Lock in answer" }).click();
    await expect(activePage.getByText("Card locked in")).toBeVisible();

    await waitForRoundTwo(adminPage, sessionId);

    for (const page of [adminPage, memberPage]) {
      await page.reload();
      await expect(page.getByRole("heading", { name: "Round 2" })).toBeVisible();
    }
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

test("playlist detail links to the import progress while a job runs", async ({ browser }) => {
  const page = await login(browser, ADMIN_ACCOUNT);

  try {
    const playlistResponse = await page.request.post(apiUrl(PLAYLISTS_API_PATH), {
      headers: await csrfHeaders(page),
    });
    expect(playlistResponse.ok()).toBeTruthy();
    const playlist = await playlistResponse.json() as PlaylistResponse;

    await page.route(`**/api/playlists/${playlist.id}/import-jobs/active`, (route) => route.fulfill({
      json: {
        id: "job-1",
        playlistId: playlist.id,
        status: "RUNNING",
        items: [
          { youtubeId: "pending-video-1", status: "PENDING" },
          { youtubeId: "pending-video-2", status: "UNRESOLVED" },
        ],
      },
    }));
    await page.addInitScript((storedPlaylistId: number) => {
      window.localStorage.setItem(
        "hittiguess-active-playlist-import",
        JSON.stringify({ importJobId: "job-1", playlistId: storedPlaylistId }),
      );
    }, playlist.id);
    await page.goto(`/playlists/${playlist.id}`);

    await expect(page.getByText("Importing 2 songs in the background...")).toBeVisible();
    // The unresolved item counts as finished, only the pending one is still running.
    const progressLink = page.getByTitle("1 of 2 songs imported so far.");
    await expect(progressLink).toBeVisible();
    await expect(progressLink).toHaveAttribute("href", `/playlists/${playlist.id}/import/youtube`);
    await expect(page.getByTitle("Import in progress")).toBeVisible();

    await page.unroute(`**/api/playlists/${playlist.id}/import-jobs/active`);
    await page.route(`**/api/playlists/${playlist.id}/import-jobs/active`, (route) => route.abort());
    await expect(page.getByText("Importing 2 songs in the background...")).toBeHidden({
      timeout: IMPORT_GONE_WAIT_TIMEOUT_MILLISECONDS,
    });
  } finally {
    await page.context().close();
  }
});

test("a real background import resolves and lands its songs", async ({ browser }) => {
  test.setTimeout(IMPORT_DONE_TIMEOUT_MILLISECONDS);
  const page = await login(browser, ADMIN_ACCOUNT);

  try {
    const playlistResponse = await page.request.post(apiUrl(PLAYLISTS_API_PATH), {
      headers: await csrfHeaders(page),
    });
    expect(playlistResponse.ok()).toBeTruthy();
    const playlist = await playlistResponse.json() as PlaylistResponse;

    const startResponse = await page.request.post(
      apiUrl(`/api/playlists/${playlist.id}/import-jobs`),
      {
        data: { videoIdsOrLinks: [KNOWN_VIDEO_ID] },
        headers: await csrfHeaders(page),
      },
    );
    expect(startResponse.ok(), `${startResponse.status()} ${await startResponse.text()}`).toBeTruthy();
    const startedJob = await startResponse.json() as { importJobId: string };
    expect(startedJob.importJobId).toBeTruthy();

    const deadline = Date.now() + IMPORT_DONE_TIMEOUT_MILLISECONDS;
    for (;;) {
      const activeResponse = await page.request.get(
        apiUrl(`/api/playlists/${playlist.id}/import-jobs/active`),
      );
      if (activeResponse.status() === 404 || Date.now() > deadline) {
        expect(activeResponse.status()).toBe(404);
        break;
      }
      await page.waitForTimeout(IMPORT_DONE_POLL_INTERVAL_MILLISECONDS);
    }

    const playlistResponseAfter = await page.request.get(apiUrl(`/api/playlists/${playlist.id}`));
    expect(playlistResponseAfter.ok()).toBeTruthy();
    const playlistAfter = await playlistResponseAfter.json() as { songs: unknown[] };
    expect(playlistAfter.songs.length).toBeGreaterThan(0);
  } finally {
    await page.context().close();
  }
});
