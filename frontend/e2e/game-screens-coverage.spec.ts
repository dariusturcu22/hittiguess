import { expect, test, type Page } from "@playwright/test";

const GROUP_ID = 88;
const SESSION_ID = 99;
const ADMIN_USER_ID = 101;
const SECOND_USER_ID = 102;
const THIRD_USER_ID = 103;
const ADMIN_PLAYER_ID = 201;
const SECOND_PLAYER_ID = 202;
const THIRD_PLAYER_ID = 203;

const VIEWPORTS = [
  { width: 1366, height: 768 },
  { width: 1440, height: 900 },
  { width: 1920, height: 1080 },
];

const ZOOM_LEVELS = [1.0, 1.25, 1.5];

const EIGHT_MEMBERS = [
  { id: 1, userId: ADMIN_USER_ID, displayName: "Admin Alex", isAdmin: true, isConnected: true },
  { id: 2, userId: SECOND_USER_ID, displayName: "Member Sam", isAdmin: false, isConnected: true },
  { id: 3, userId: THIRD_USER_ID, displayName: "Member Jordan", isAdmin: false, isConnected: true },
  { id: 4, userId: 104, displayName: "Member Taylor", isAdmin: false, isConnected: true },
  { id: 5, userId: 105, displayName: "Member Casey", isAdmin: false, isConnected: true },
  { id: 6, userId: 106, displayName: "Member Riley", isAdmin: false, isConnected: true },
  { id: 7, userId: 107, displayName: "Member Morgan", isAdmin: false, isConnected: true },
  { id: 8, userId: 108, displayName: "Member Quinn", isAdmin: false, isConnected: true },
];

const SAMPLE_TIMELINE = [
  { songId: 10, artist: "ABBA", title: "Dancing Queen", releaseYear: 1976, color: "f9e2af", position: 0 },
  { songId: 11, artist: "Queen", title: "Bohemian Rhapsody", releaseYear: 1975, color: "89b4fa", position: 1 },
];

const SAMPLE_PLAYLISTS = [
  { id: 1, name: "Hits of the 70s", color: "fab387", songCount: 45 },
  { id: 2, name: "Rock Classics", color: "a6e3a1", songCount: 50 },
];

async function setupBaseMockRoutes(page: Page, currentUserId: number, members = EIGHT_MEMBERS) {
  await page.context().addCookies([
    { name: "session_hint", value: "game-screens-coverage-fixture", url: "http://localhost:3000" },
  ]);

  await page.routeWebSocket("**/ws", (socket) => {
    socket.onMessage((message) => {
      const [headersBlock] = message.toString().split("\n\n");
      const [command] = headersBlock.split("\n");
      if (command === "CONNECT" || command === "STOMP") {
        socket.send("CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0");
      }
    });
  });

  return page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;

    if (path === "/api/users/me") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ id: currentUserId, username: "admin_alex", role: "USER" }),
      });
      return;
    }

    if (path === "/api/users/me/playlists") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(SAMPLE_PLAYLISTS),
      });
      return;
    }

    if (path === `/api/groups/${GROUP_ID}`) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: GROUP_ID,
          inviteCode: "GAME88",
          status: "OPEN",
          difficultyTier: "MEDIUM",
          djMode: "ROTATING",
          winConditionCardCount: 8,
          playlists: SAMPLE_PLAYLISTS,
          members,
        }),
      });
      return;
    }

    if (path === "/api/groups/active") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ id: GROUP_ID }),
      });
      return;
    }

    if (path === `/api/sessions/groups/${GROUP_ID}/active`) {
      await route.fulfill({
        status: 204,
      });
      return;
    }

    if (path.includes("/messages")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { id: 1, senderId: ADMIN_USER_ID, senderDisplayName: "Admin Alex", content: "Welcome everyone!", createdAt: "2026-10-31T12:00:00Z" },
          { id: 2, senderId: SECOND_USER_ID, senderDisplayName: "Member Sam", content: "Ready to play!", createdAt: "2026-10-31T12:01:00Z" },
        ]),
      });
      return;
    }

    if (path.includes("/voice/turn")) {
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ iceServers: [] }) });
      return;
    }

    if (path.includes("/guess-state")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          roundId: 1,
          artistCount: 1,
          correctArtistCount: 0,
          artistGuessingClosed: false,
          titleGuessed: false,
          titleCorrect: false,
          tokenEarned: false,
        }),
      });
      return;
    }

    if (path.includes("/link-out")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ url: "https://www.youtube.com/watch?v=mock" }),
      });
      return;
    }

    if (path.includes("/import-jobs/active")) {
      await route.fulfill({ status: 204 });
      return;
    }

    if (path === `/api/sessions/groups/${GROUP_ID}/results`) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          groupId: GROUP_ID,
          cardCountRanking: [
            { playerId: ADMIN_PLAYER_ID, displayName: "Admin Alex", cardCount: 8, rank: 1 },
            { playerId: SECOND_PLAYER_ID, displayName: "Member Sam", cardCount: 5, rank: 2 },
            { playerId: THIRD_PLAYER_ID, displayName: "Member Jordan", cardCount: 4, rank: 3 },
          ],
          mostArtistsGuessed: [{ playerId: ADMIN_PLAYER_ID, displayName: "Admin Alex", value: 6 }],
          mostTitlesGuessed: [{ playerId: SECOND_PLAYER_ID, displayName: "Member Sam", value: 5 }],
        }),
      });
      return;
    }

    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({}) });
  });
}

function buildSessionData(roundStatus: string, options: {
  turnNumber?: number;
  activePlayerId?: number;
  djPlayerId?: number;
  placementCorrect?: boolean;
  bets?: Array<{ playerId: number; position: number }>;
} = {}) {
  const activePlayerId = options.activePlayerId ?? ADMIN_PLAYER_ID;
  const djPlayerId = options.djPlayerId ?? SECOND_PLAYER_ID;
  const turnNumber = options.turnNumber ?? 2;
  const bets = options.bets ?? [];

  return {
    id: SESSION_ID,
    groupId: GROUP_ID,
    status: "IN_PROGRESS",
    djMode: "ROTATING",
    winConditionCardCount: 8,
    currentRoundNumber: 1,
    currentRound: {
      id: 1,
      roundNumber: 1,
      turnNumber,
      activePlayerId,
      djPlayerId,
      status: roundStatus,
      revealedArtist: "The Beatles",
      revealedTitle: "Hey Jude",
      revealedYear: 1968,
      revealedColor: "fab387",
      placedPosition: 1,
      placementCorrect: options.placementCorrect ?? false,
      placementEndsAt: new Date(Date.now() + 600_000).toISOString(),
      countdownEndsAt: new Date(Date.now() + 30_000).toISOString(),
      bettingEndsAt: new Date(Date.now() + 30_000).toISOString(),
      bettingWindowEndsAt: new Date(Date.now() + 30_000).toISOString(),
      bets,
    },
    players: [
      { id: ADMIN_PLAYER_ID, userId: ADMIN_USER_ID, displayName: "Admin Alex", status: "ACTIVE", turnOrder: 0, timeline: SAMPLE_TIMELINE, tokenCount: 3 },
      { id: SECOND_PLAYER_ID, userId: SECOND_USER_ID, displayName: "Member Sam", status: "ACTIVE", turnOrder: 1, timeline: SAMPLE_TIMELINE, tokenCount: 2 },
      { id: THIRD_PLAYER_ID, userId: THIRD_USER_ID, displayName: "Member Jordan", status: "ACTIVE", turnOrder: 2, timeline: SAMPLE_TIMELINE, tokenCount: 1 },
    ],
  };
}

async function applyZoom(page: Page, zoom: number) {
  await page.evaluate((evaluatedZoom) => {
    document.documentElement.style.zoom = String(evaluatedZoom);
  }, zoom);
}

test.describe("Game screens layout coverage across viewports and zooms", () => {
  for (const viewport of VIEWPORTS) {
    for (const zoom of ZOOM_LEVELS) {
      const configurationLabel = `${viewport.width}x${viewport.height}-zoom-${Math.round(zoom * 100)}`;

      test(`8-member lobby and popups at ${configurationLabel}`, async ({ page }, testInfo) => {
        await page.setViewportSize(viewport);
        await setupBaseMockRoutes(page, ADMIN_USER_ID);

        await page.goto(`/groups/${GROUP_ID}`);
        await page.addStyleTag({ content: "nextjs-portal { display: none; }" });
        await applyZoom(page, zoom);

        await expect(page.getByRole("heading", { name: "Group Lobby" })).toBeVisible();
        await expect(page.getByText("Admin Alex (you)")).toBeVisible();
        await expect(page.getByText("Member Quinn")).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`lobby-8-members-${configurationLabel}.png`) });

        // Lobby popup 1: settings popup
        await page.getByRole("button", { name: "Settings", exact: true }).click();
        await expect(page.getByRole("heading", { name: "Settings" })).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`lobby-settings-popup-${configurationLabel}.png`) });
        await page.getByRole("button", { name: "Close settings" }).click();

        // Lobby popup 2: difficulty tier popup
        await page.getByRole("button", { name: "Choose playlists" }).click();
        await expect(page.getByRole("region", { name: "Start options" })).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`lobby-tier-popup-${configurationLabel}.png`) });

        // Lobby popup 3: custom picker popup
        await page.getByRole("button", { name: "Custom" }).click();
        await expect(page.getByRole("region", { name: "Custom playlist selection" })).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`lobby-custom-picker-popup-${configurationLabel}.png`) });
        await page.getByRole("region", { name: "Custom playlist selection" }).getByRole("button", { name: "Close" }).first().click();

        // Lobby popup 4: chat popup
        await page.getByRole("button", { name: "Chat", exact: true }).click();
        await expect(page.getByRole("heading", { name: "Chat" })).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`lobby-chat-popup-${configurationLabel}.png`) });
      });

      test(`gameplay placement and subtitle non-overlap at ${configurationLabel}`, async ({ page }, testInfo) => {
        await page.setViewportSize(viewport);
        await setupBaseMockRoutes(page, ADMIN_USER_ID);

        // Turn number 2 to skip intro banner and go directly to placement
        const sessionData = buildSessionData("AWAITING_PLACEMENT", { turnNumber: 2, activePlayerId: ADMIN_PLAYER_ID });
        await page.route(`**/api/sessions/${SESSION_ID}`, async (route) => {
          await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(sessionData) });
        });

        await page.goto(`/sessions/${SESSION_ID}`);
        await page.addStyleTag({ content: "nextjs-portal { display: none; }" });
        await applyZoom(page, zoom);

        const card = page.getByRole("button", { name: "Your card. Choose a timeline position." });
        await expect(card).toBeVisible();

        const subtitle = page.locator("header .text-\\[12px\\]").first();
        await expect(subtitle).toBeVisible();

        const cardBox = await card.boundingBox();
        const subtitleBox = await subtitle.boundingBox();

        expect(cardBox).not.toBeNull();
        expect(subtitleBox).not.toBeNull();

        if (cardBox && subtitleBox) {
          expect(cardBox.y).toBeGreaterThanOrEqual(subtitleBox.y + subtitleBox.height);
        }

        await page.screenshot({ path: testInfo.outputPath(`gameplay-placement-${configurationLabel}.png`) });
      });

      test(`round intro at ${configurationLabel}`, async ({ page }, testInfo) => {
        await page.setViewportSize(viewport);
        await setupBaseMockRoutes(page, ADMIN_USER_ID);

        // Turn number 1 renders the RoundIntro opener
        const sessionData = buildSessionData("AWAITING_PLACEMENT", { turnNumber: 1, activePlayerId: ADMIN_PLAYER_ID });
        await page.route(`**/api/sessions/${SESSION_ID}`, async (route) => {
          await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(sessionData) });
        });

        await page.goto(`/sessions/${SESSION_ID}`);
        await page.addStyleTag({ content: "nextjs-portal { display: none; }" });
        await applyZoom(page, zoom);

        await expect(page.getByRole("heading", { name: "Round 1", level: 2 })).toBeVisible();
        await expect(page.getByText("First DJ")).toBeVisible();
        await expect(page.getByText("First turn")).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`gameplay-round-intro-${configurationLabel}.png`) });
      });

      test(`gameplay betting phase at ${configurationLabel}`, async ({ page }, testInfo) => {
        await page.setViewportSize(viewport);
        await setupBaseMockRoutes(page, THIRD_USER_ID);

        const sessionData = buildSessionData("BETTING", { turnNumber: 2, activePlayerId: ADMIN_PLAYER_ID, djPlayerId: SECOND_PLAYER_ID });
        await page.route(`**/api/sessions/${SESSION_ID}`, async (route) => {
          await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(sessionData) });
        });

        await page.goto(`/sessions/${SESSION_ID}`);
        await page.addStyleTag({ content: "nextjs-portal { display: none; }" });
        await applyZoom(page, zoom);

        await expect(page.getByText("Betting closes")).toBeVisible();
        await expect(page.getByRole("button", { name: "Skip betting" })).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`gameplay-betting-${configurationLabel}.png`) });
      });

      test(`gameplay reveal phase at ${configurationLabel}`, async ({ page }, testInfo) => {
        await page.setViewportSize(viewport);
        await setupBaseMockRoutes(page, ADMIN_USER_ID);

        const sessionData = buildSessionData("REVEALED", { turnNumber: 2, activePlayerId: ADMIN_PLAYER_ID });
        await page.route(`**/api/sessions/${SESSION_ID}`, async (route) => {
          await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(sessionData) });
        });

        await page.goto(`/sessions/${SESSION_ID}`);
        await page.addStyleTag({ content: "nextjs-portal { display: none; }" });
        await applyZoom(page, zoom);

        await expect(page.getByText("The Beatles")).toBeVisible();
        await expect(page.getByText("Hey Jude")).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`gameplay-reveal-${configurationLabel}.png`) });
      });

      test(`gameplay between rounds phase at ${configurationLabel}`, async ({ page }, testInfo) => {
        await page.setViewportSize(viewport);
        await setupBaseMockRoutes(page, ADMIN_USER_ID);

        const sessionData = buildSessionData("SCORED", { turnNumber: 2, activePlayerId: ADMIN_PLAYER_ID, placementCorrect: true });
        await page.route(`**/api/sessions/${SESSION_ID}`, async (route) => {
          await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(sessionData) });
        });

        await page.goto(`/sessions/${SESSION_ID}`);
        await page.addStyleTag({ content: "nextjs-portal { display: none; }" });
        await applyZoom(page, zoom);

        await expect(page.getByText("Round closed. Next card is dealt in a moment.")).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`gameplay-between-rounds-${configurationLabel}.png`) });
      });

      test(`game over results screen at ${configurationLabel}`, async ({ page }, testInfo) => {
        await page.setViewportSize(viewport);
        await setupBaseMockRoutes(page, ADMIN_USER_ID);

        await page.goto(`/sessions/${SESSION_ID}/results?group=${GROUP_ID}`);
        await page.addStyleTag({ content: "nextjs-portal { display: none; }" });
        await applyZoom(page, zoom);

        await expect(page.getByRole("heading", { name: "Game Over" })).toBeVisible();
        await expect(page.getByText("Admin Alex wins with 8 cards")).toBeVisible();
        await expect(page.getByRole("button", { name: "Download results" })).toBeVisible();
        await page.screenshot({ path: testInfo.outputPath(`game-over-${configurationLabel}.png`) });
      });
    }
  }
});
