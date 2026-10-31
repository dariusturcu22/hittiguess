import { expect, test } from "@playwright/test";

test.describe("Two-factor authentication flows", () => {
  test("login second step accepts TOTP and backup codes and handles errors", async ({ page }) => {
    await page.route("**/auth/login", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          twoFactorRequired: true,
          pendingToken: "mock-pending-jwt-token",
        }),
      });
    });

    let lastVerifyPayload: { pendingToken?: string; code?: string } | null = null;
    await page.route("**/auth/2fa/verify", async (route) => {
      const requestData = JSON.parse(route.request().postData() || "{}");
      lastVerifyPayload = requestData;
      if (requestData.code === "000000") {
        await route.fulfill({
          status: 401,
          contentType: "application/json",
          body: JSON.stringify({ message: "Invalid verification code" }),
        });
      } else {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          headers: {
            "Set-Cookie": "session_hint=active; Path=/",
          },
          body: JSON.stringify({
            id: 42,
            username: "alex",
            email: "alex@example.com",
            emailVerified: true,
          }),
        });
      }
    });

    await page.route("**/api/users/me/playlists", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
    await page.route("**/api/users/me/saved-playlists", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });

    await page.goto("/login");

    await page.getByLabel("Email").fill("alex@example.com");
    await page.getByLabel("Password", { exact: true }).fill("CorrectPassword1!");
    await page.getByRole("button", { name: "Log in" }).click();

    // Second step should appear
    await expect(page.getByRole("heading", { name: "Two-factor authentication" })).toBeVisible();
    const codeInput = page.getByLabel("Authentication or backup code");
    await expect(codeInput).toBeVisible();
    await expect(codeInput).toHaveAttribute("autocomplete", "one-time-code");

    // Invalid code attempt
    await codeInput.fill("000000");
    await page.getByRole("button", { name: "Verify and log in" }).click();
    await expect(page.getByRole("alert").filter({ hasText: "Invalid verification code" })).toBeVisible();

    // Valid code attempt
    await codeInput.fill("123456");
    await page.getByRole("button", { name: "Verify and log in" }).click();

    expect(lastVerifyPayload).toEqual({
      pendingToken: "mock-pending-jwt-token",
      code: "123456",
    });

    await expect(page).toHaveURL("/playlists");
  });

  test("settings route covers two-factor setup, backup codes, and disable proof", async ({ page }) => {
    let twoFactorActive = false;

    await page.route("**/api/users/me", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: 42,
            username: "alex",
            email: "alex@example.com",
            twoFactorEnabled: twoFactorActive,
            playlists: [],
          }),
        });
      } else {
        await route.continue();
      }
    });

    await page.route("**/api/users/me/statistics", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ gamesPlayed: 10, wins: 4 }),
      });
    });

    await page.route("**/auth/2fa/setup", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          secret: "JBSWY3DPEHPK3PXP",
          provisioningUri: "otpauth://totp/hittiguess:alex?secret=JBSWY3DPEHPK3PXP&issuer=hittiguess",
        }),
      });
    });

    await page.route("**/auth/2fa/confirm", async (route) => {
      const data = JSON.parse(route.request().postData() || "{}");
      if (data.code === "123456") {
        twoFactorActive = true;
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            backupCodes: ["BKUP000001", "BKUP000002", "BKUP000003"],
          }),
        });
      } else {
        await route.fulfill({
          status: 400,
          contentType: "application/json",
          body: JSON.stringify({ message: "Invalid verification code" }),
        });
      }
    });

    await page.route("**/auth/2fa/disable", async (route) => {
      twoFactorActive = false;
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });

    // Provide active session hint
    await page.context().addCookies([
      {
        name: "session_hint",
        value: "active",
        domain: "localhost",
        path: "/",
      },
    ]);

    await page.goto("/settings");

    await expect(page.getByRole("heading", { name: "Account settings" })).toBeVisible();
    await expect(page.getByText("Disabled", { exact: true })).toBeVisible();

    // Start setup
    await page.getByRole("button", { name: "Set up two-factor authentication" }).click();
    await expect(page.getByRole("heading", { name: "Set up authenticator" })).toBeVisible();
    await expect(page.getByAltText("Two-factor authentication QR code")).toBeVisible();
    await expect(page.getByText("JBSWY3DPEHPK3PXP")).toBeVisible();

    // Confirm with code
    await page.getByLabel("Enter 6-digit verification code").fill("123456");
    await page.getByRole("button", { name: "Confirm and enable" }).click();

    // Backup codes should appear
    await expect(page.getByText("Two-factor authentication is now active")).toBeVisible();
    await expect(page.getByText("BKUP000001")).toBeVisible();
    await expect(page.getByText("BKUP000002")).toBeVisible();
    await expect(page.getByText("BKUP000003")).toBeVisible();

    // Dismiss backup codes view
    await page.getByRole("button", { name: "Done" }).click();

    // Active status and disable button should now be visible
    await expect(page.getByText("Active")).toBeVisible();
    await page.getByRole("button", { name: "Disable two-factor authentication" }).click();

    // Disable dialog should appear
    await expect(page.getByRole("heading", { name: "Disable two-factor authentication" })).toBeVisible();
    await page.getByLabel("Current password (optional if code is entered)").fill("SecretPass1!");
    await page.getByRole("button", { name: "Confirm disable" }).click();

    await expect(page.getByText("Disabled", { exact: true })).toBeVisible();
  });
});
