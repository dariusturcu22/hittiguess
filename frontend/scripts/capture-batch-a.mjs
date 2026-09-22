import { chromium } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { join } from "node:path";

const OUTPUT_DIRECTORY = process.env.CAPTURE_OUT || join(process.cwd(), "..", ".capture");
const BASE_URL = "http://127.0.0.1:3000";
const VIEWPORT = { width: 1440, height: 900 };
const THEMES = ["dark", "light"];
const UNGATED_ROUTES = [
  { name: "landing", path: "/" },
  { name: "login", path: "/login" },
  { name: "register", path: "/register" },
  { name: "forgot-password", path: "/forgot-password" },
];

mkdirSync(OUTPUT_DIRECTORY, { recursive: true });

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: VIEWPORT });

await context.addInitScript((themeName) => {
  window.localStorage.setItem("theme", themeName);
}, "dark");

const page = await context.newPage();

for (const theme of THEMES) {
  await context.addInitScript((themeName) => {
    window.localStorage.setItem("theme", themeName);
  }, theme);
  for (const route of UNGATED_ROUTES) {
    const targetUrl = `${BASE_URL}${route.path}`;
    try {
      await page.goto(targetUrl, { waitUntil: "networkidle", timeout: 45000 });
      await page.waitForTimeout(800);
      const finalUrl = page.url();
      const outputPath = join(OUTPUT_DIRECTORY, `${route.name}-${theme}.png`);
      await page.screenshot({ path: outputPath, fullPage: false });
      console.log(`OK ${route.name} ${theme} -> ${outputPath} (landed: ${finalUrl})`);
    } catch (error) {
      console.log(`FAIL ${route.name} ${theme}: ${error.message}`);
    }
  }
}

await browser.close();
