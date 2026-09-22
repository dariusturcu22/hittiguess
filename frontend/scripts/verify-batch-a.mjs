import { chromium } from "@playwright/test";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const BASE_URL = "http://localhost:3000";
const OUTPUT_DIRECTORY = resolve("batch-a-artifacts");
const SOURCE_DIRECTORY = resolve("../docs/design/source");
const DESKTOP_VIEWPORT = { width: 1440, height: 900 };
const MOBILE_VIEWPORT = { width: 390, height: 844 };
const SOURCE_SCREENS = ["LandingDesktop", "LandingMobile", "Login", "Register", "ForgotPassword", "OAuth2Redirect", "AppShell", "YourPlaylists", "PlaylistDetail", "SongDetail", "SongDetailNeedsReview", "EditSongDetails", "AddSong", "AddNewSongLink", "AddNewSongReviewEditable", "AddNewSongReviewLocked", "JoinInvite"];
const THEMES = ["Dark", "Light"];
const songs = [
  { id: 1, artists: [{ name: "Radiohead", role: "MAIN" }], title: "Creep", releaseYear: 1994, youtubeId: "XFkzRNyygfk", verificationStatus: "VERIFIED", gradientColor1: "a6e3a1", gradientColor2: "89b4fa", addedBy: { id: 1, username: "Darius" } },
  { id: 2, artists: [{ name: "Snow Patrol", role: "MAIN" }], title: "Chasing Cars", releaseYear: 2006, youtubeId: "GemKqzILV4w", verificationStatus: "NEEDS_REVIEW", gradientColor1: "f9e2af", gradientColor2: "fab387", addedBy: { id: 1, username: "Darius" } },
  { id: 3, artists: [{ name: "Radiohead", role: "MAIN" }], title: "No Surprises", releaseYear: 1997, youtubeId: "u5CVsCnxyXg", verificationStatus: "MANUAL_ENTRY", gradientColor1: "cba6f7", gradientColor2: "89b4fa", addedBy: { id: 1, username: "Darius" } },
];
const playlist = { id: 1, name: "Late night rotation", color: "8B5CF6", inviteCode: "batch-a-preview", songCount: songs.length, songs, ownerId: 1, members: [{ userId: 1, username: "Darius", displayName: "Darius", owner: true, canRead: true, canWrite: true, canDelete: true }] };
const playlistSummaries = [playlist, { id: 2, name: "Indie discoveries", color: "89b4fa", songCount: 24 }, { id: 3, name: "Road trip", color: "fab387", songCount: 40 }];

await mkdir(OUTPUT_DIRECTORY, { recursive: true });
const browser = await chromium.launch();
const measurements = [];
try {
  for (const theme of THEMES) {
    const context = await browser.newContext({ viewport: DESKTOP_VIEWPORT });
    await context.addInitScript((selectedTheme) => localStorage.setItem("theme", selectedTheme), theme.toLowerCase());
    await context.addCookies(["access_token", "refresh_token"].map((name) => ({ name, value: "visual-fixture", url: BASE_URL })));
    await context.route("**/api/**", async (route) => {
      const pathname = new URL(route.request().url()).pathname;
      let body;
      if (pathname === "/api/users/me") body = { id: 1, username: "Darius", email: "fixture@example.invalid" };
      else if (pathname === "/api/users/me/playlists") body = playlistSummaries;
      else if (pathname === "/api/playlists/1") body = playlist;
      else if (pathname.startsWith("/api/playlists/1/songs/")) body = songs.find((song) => pathname.endsWith(`/${song.id}`));
      else if (pathname === "/api/songs/search") body = songs;
      else if (pathname === "/api/metadata/song") body = { status: "SUCCESS", content: { title: "Creep", artist: "Radiohead", releaseYear: 1994, gradientColor1: "a6e3a1", gradientColor2: "89b4fa", verificationStatus: "NEEDS_REVIEW", confidence: "medium" } };
      else body = [];
      await route.fulfill({ json: body });
    });
    const page = await context.newPage();
    const capture = async (name) => {
      await page.evaluate(() => document.fonts.ready);
      await page.screenshot({ path: resolve(OUTPUT_DIRECTORY, `${name}${theme}.png`), fullPage: true, animations: "disabled" });
      measurements.push({ name: `${name}${theme}`, ...(await page.evaluate(() => ({ width: innerWidth, scrollWidth: document.documentElement.scrollWidth, headings: [...document.querySelectorAll("h1,h2")].map((heading) => ({ text: heading.textContent, rect: heading.getBoundingClientRect().toJSON() })) }))) });
      console.log(`Captured ${name}${theme}`);
    };
    const routes = [["LandingDesktop", "/"], ["Login", "/login"], ["Register", "/register"], ["ForgotPassword", "/forgot-password"], ["YourPlaylists", "/playlists"], ["PlaylistDetail", "/playlists/1"], ["SongDetail", "/playlists/1/songs/1"], ["SongDetailNeedsReview", "/playlists/1/songs/2"], ["EditSongDetails", "/playlists/1/songs/3"], ["JoinInvite", "/playlists/join/batch-a-preview"], ["AddSong", "/playlists/1/songs/add"]];
    for (const [name, pathname] of routes) {
      await page.goto(`${BASE_URL}${pathname}`, { waitUntil: "networkidle" });
      if (name === "AddSong") await page.getByPlaceholder("Search by title or artist...").fill("Radiohead");
      await capture(`actual-${name}`);
    }
    await page.getByRole("button", { name: "Add a new song", exact: true }).click();
    await capture("actual-AddNewSongLink");
    await page.getByPlaceholder(/youtube|youtu|paste/i).fill("https://www.youtube.com/watch?v=XFkzRNyygfk");
    await page.getByRole("button", { name: /fetch/i }).click();
    await page.getByText("STEP 2 OF 2").waitFor();
    await capture("actual-AddNewSongReviewEditable");
    await page.setViewportSize(MOBILE_VIEWPORT);
    await page.goto(BASE_URL, { waitUntil: "networkidle" });
    await capture("actual-LandingMobile");
    for (const screen of SOURCE_SCREENS) {
      await page.setViewportSize(screen === "LandingMobile" ? MOBILE_VIEWPORT : DESKTOP_VIEWPORT);
      const source = await readFile(resolve(SOURCE_DIRECTORY, `${screen}${theme}.dc.html`), "utf8");
      const markup = source.replace(/<script\b[^>]*>[\s\S]*?<\/script>/g, "").replace(/\{\{bar[A-D]\}\}/g, "16px");
      await page.setContent(markup, { waitUntil: "networkidle" });
      await capture(`mockup-${screen}`);
    }
    await context.close();
  }
  await writeFile(resolve(OUTPUT_DIRECTORY, "measurements.json"), JSON.stringify(measurements, null, 2));
} finally {
  await browser.close();
}
