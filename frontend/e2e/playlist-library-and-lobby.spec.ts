import { expect, test, type Page } from "@playwright/test";

const GROUP_ID = 1;
const PLAYLIST_ID = 7;
const OWNER_USER_ID = 11;
const MEMBER_USER_ID = 12;
const MEMBER_ID = 2;
const DESKTOP = { width: 1440, height: 900 };
const API_PATTERN = "**/api/**";
const RECTANGLE_CENTER_DIVISOR = 2;
const ALIGNMENT_TOLERANCE_PIXELS = 1;
const THEMES = ["dark", "light"] as const;
const PLAYLISTS = [
  { id: PLAYLIST_ID, name: "Midnight Radio", color: "cba6f7", songCount: 32, ownedByCurrentUser: true, previewYoutubeIds: [] },
  { id: 8, name: "Road Trip", color: "89b4fa", songCount: 21, ownedByCurrentUser: false, previewYoutubeIds: [] },
];
const SAVED_PLAYLIST = { id: 9, name: "Weekend classics", color: "fab387", songCount: 18, previewYoutubeIds: [], owner: { id: MEMBER_USER_ID, username: "Sam" } };
const [OWNED_PLAYLIST] = PLAYLISTS;
const MEMBERS = [
  { id: 1, userId: OWNER_USER_ID, displayName: "Alex", isAdmin: true, isConnected: true, isInVoice: false, joinedAt: "2026-10-09T10:00:00Z" },
  { id: MEMBER_ID, userId: MEMBER_USER_ID, displayName: "Sam", isAdmin: false, isConnected: true, isInVoice: false, joinedAt: "2026-10-09T10:01:00Z" },
];

async function fixture(page: Page, theme: string) {
  let transferred = false;
  let difficultyTier = "MEDIUM";
  await page.context().addCookies([{ name: "session_hint", value: "playlist-lobby-fixture", url: "http://localhost:3000" }]);
  await page.addInitScript((selectedTheme) => localStorage.setItem("theme", selectedTheme), theme);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.route(API_PATTERN, async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/settings") && route.request().method() === "PATCH") difficultyTier = route.request().postDataJSON().difficultyTier ?? difficultyTier;
    const members = MEMBERS.map((member) => ({ ...member, isAdmin: transferred ? member.userId === MEMBER_USER_ID : member.isAdmin }));
    const group = { id: GROUP_ID, joinCode: "ABCD", inviteCode: "group-invite", status: "OPEN", djMode: "ROTATING", winConditionCardCount: 5, difficultyTier, members, playlists: [OWNED_PLAYLIST] };
    let body: unknown = {};
    let status = 200;
    if (path.endsWith(`/members/${MEMBER_ID}/promote`)) { transferred = true; body = { ...group, members: members.map((member) => ({ ...member, isAdmin: member.userId === MEMBER_USER_ID })) }; }
    else if (path === "/api/users/me") body = { id: OWNER_USER_ID, username: "Alex", email: "alex@example.com", role: "USER" };
    else if (path === "/api/users/me/playlists") body = PLAYLISTS;
    else if (path === "/api/users/me/saved-playlists") body = [SAVED_PLAYLIST];
    else if (path === "/api/groups/active") body = group;
    else if (path === `/api/groups/${GROUP_ID}` || path.endsWith("/settings")) body = group;
    else if (path.endsWith("/session/generate")) body = [{ id: 31, title: "Sample track", artists: ["Sample artist"], releaseYear: 1999 }];
    else if (path === "/api/playlists/public") body = [{ ...OWNED_PLAYLIST, owner: { id: OWNER_USER_ID, username: "Alex" } }, SAVED_PLAYLIST];
    else if (path === `/api/playlists/${PLAYLIST_ID}`) body = { ...OWNED_PLAYLIST, ownerId: OWNER_USER_ID, inviteCode: "MIDNIGHT", createdAt: "2026-10-09T10:00:00Z", members: [{ userId: OWNER_USER_ID, username: "Alex", displayName: "Alex", owner: true }, { userId: MEMBER_USER_ID, username: "Sam", displayName: "Sam", owner: false }], songs: [
      { id: 31, title: "Sample track", artists: [{ name: "Sample artist", role: "MAIN" }], releaseYear: 1999, youtubeId: "video-one", verificationStatus: "VERIFIED", durationSeconds: 210 },
      { id: 32, title: "Legacy track", artists: [{ name: "Another artist", role: "MAIN" }], releaseYear: 2001, youtubeId: "video-two", verificationStatus: "UNVERIFIED" },
    ] };
    else if (path.includes("/chat/messages")) body = [];
    else if (path.includes("/active") || path.includes("/import")) { status = 204; body = undefined; }
    await route.fulfill({ status, contentType: "application/json", body: body === undefined ? undefined : JSON.stringify(body), headers: { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-credentials": "true" } });
  });
}

for (const theme of THEMES) {
  for (const [viewportName, viewport] of Object.entries({ desktop: DESKTOP })) {
    test(`${theme} ${viewportName}: library, explore, detail and lobby controls`, async ({ page }, testInfo) => {
      await page.setViewportSize(viewport);
      await fixture(page, theme);
      await page.goto("/playlists");
      await expect(page.getByRole("heading", { name: "Your playlists" })).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath("library.png"), animations: "disabled" });
      for (const tab of ["All", "Owned", "Joined", "Saved"]) await page.getByRole("button", { name: tab, exact: true }).click();
      await expect(page.getByText("Weekend classics", { exact: true })).toBeVisible();
      await page.goto("/explore");
      await expect(page.getByText("Midnight Radio", { exact: true })).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath("explore.png"), animations: "disabled" });
      for (const tab of ["All", "Saved", "Not saved"]) await page.getByRole("button", { name: tab, exact: true }).and(page.locator(":enabled")).click();
      await expect(page.getByText("Midnight Radio", { exact: true })).toBeVisible();
      await page.goto(`/playlists/${PLAYLIST_ID}`);
      await expect(page.getByText("Created 9 Oct 2026")).toBeVisible();
      await expect(page.getByText("3:30", { exact: true }).filter({ visible: true })).toBeVisible();
      await page.getByRole("button", { name: "Members (2)" }).click();
      await expect(page.getByText("Sam", { exact: true })).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath("playlist-members.png"), animations: "disabled" });
      await page.getByRole("button", { name: "Members (2)" }).click();
      await page.getByRole("button", { name: "Add song", exact: true }).click();
      await expect(page.getByRole("menuitem", { name: "Add a new song" })).toBeVisible();
      await expect(page.getByRole("menuitem", { name: "Import playlist" })).toHaveAttribute("href", `/playlists/${PLAYLIST_ID}/import`);
      await page.screenshot({ path: testInfo.outputPath("playlist-add-menu.png"), animations: "disabled" });
      await page.goto(`/groups/${GROUP_ID}`);
      await page.getByRole("button", { name: "Choose playlists" }).click();
      await expect(page.getByRole("button", { name: "Confirm", exact: true })).toBeVisible();
      const tierButtons = page.getByRole("region", { name: "Start options" }).getByRole("button").filter({ hasNotText: "Confirm" });
      const tierPositions = await tierButtons.evaluateAll((buttons, centerDivisor) => buttons.map((button) => {
        const rectangle = button.getBoundingClientRect();
        return rectangle.top + rectangle.height / centerDivisor;
      }), RECTANGLE_CENTER_DIVISOR);
      expect(Math.max(...tierPositions) - Math.min(...tierPositions)).toBeLessThan(ALIGNMENT_TOLERANCE_PIXELS);
      const tierBounds = await tierButtons.evaluateAll((buttons) => buttons.map((button) => {
        const rectangle = button.getBoundingClientRect();
        return { left: rectangle.left, right: rectangle.right };
      }));
      for (const bounds of tierBounds) {
        expect(bounds.left).toBeGreaterThanOrEqual(0);
        expect(bounds.right).toBeLessThanOrEqual(viewport.width);
      }
      await page.screenshot({ path: testInfo.outputPath("lobby-tiers.png"), animations: "disabled" });
      await page.getByRole("button", { name: "Confirm", exact: true }).click();
      await expect(page.getByText("Medium difficulty", { exact: true })).toBeVisible();
      await page.getByRole("button", { name: "Choose playlists" }).click();
      await page.getByRole("button", { name: "Custom", exact: true }).click();
      await expect(page.getByRole("region", { name: "Custom playlist selection" })).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath("lobby-custom.png"), animations: "disabled" });
      await page.getByRole("button", { name: "Close", exact: true }).first().click();
      await page.getByRole("button", { name: "Make Sam group admin" }).click();
      await page.screenshot({ path: testInfo.outputPath("lobby-transfer.png"), animations: "disabled" });
      await page.getByRole("button", { name: "Transfer admin", exact: true }).click();
      await expect(page.getByRole("button", { name: "Make Sam group admin" })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Choose playlists" })).toHaveCount(0);
      const pageWidth = await page.evaluate(() => document.documentElement.scrollWidth);
      expect(pageWidth).toBeLessThanOrEqual(viewport.width);
    });
  }
}
