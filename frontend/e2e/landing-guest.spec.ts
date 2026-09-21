import { test, expect } from "@playwright/test";

/**
 * Guest entry gates: logged-out users see the landing page and stay there,
 * and logged-out invite links lead into login with a return to the invite.
 * Needs the local stack running (see docs/DEV_SETUP.md); not part of CI.
 */
test("logged-out landing stays put with hero, toggle, and animation", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL("/");

  await expect(page.getByRole("heading", { name: /PLACE IT/ })).toBeVisible();
  await expect(page.getByRole("link", { name: "Start a session" }).first()).toBeVisible();

  const toggle = page.getByRole("switch").first();
  await expect(toggle).toBeVisible();
  await toggle.click();
  await expect(page.locator("html.dark")).toHaveCount(1);

  const animationName = await page.evaluate(() => {
    const bar = document.querySelector(".animate-waveform-bar");
    return bar ? getComputedStyle(bar).animationName : null;
  });
  expect(animationName).toBe("waveform-bar");
});

test("logged-out playlist invite links into login with a return target", async ({ page }) => {
  await page.goto("/playlists/join/abc123");

  const loginLink = page.getByRole("link", { name: "Log in to join" });
  await expect(loginLink).toBeVisible();
  await expect(loginLink).toHaveAttribute("href", "/login?returnTo=%2Fplaylists%2Fjoin%2Fabc123");
});
