import { describe, expect, it } from "vitest";
import fs from "fs";
import path from "path";

describe("Cookie and browser storage audit", () => {
  const AUDITED_COOKIES = [
    "access_token",
    "refresh_token",
    "session_hint",
    "XSRF-TOKEN",
    "sidebar_state",
    "oauth2_auth_request",
  ];

  const AUDITED_LOCAL_STORAGE_KEYS = [
    "theme",
    "hittiguess-microphone-device-id",
    "hittiguess-speaker-device-id",
    "hittiguess-microphone-volume",
    "hittiguess-speaker-volume",
    "hittiguess-noise-gate-sensitivity",
    "hittiguess-rnnoise-enabled",
    "hittiguess-song-volume",
    "hittiguess-active-playlist-import",
  ];

  const AUDITED_SESSION_STORAGE_PREFIXES = [
    "hittiguess-round-intro-seen-",
    "hittiguess.audioShareWarningDismissed",
    "bulk-import-progress-events",
    "bulk-import-connection-state",
  ];

  it("documents every cookie and storage key in the privacy policy as strictly necessary", () => {
    const policyPath = path.resolve(__dirname, "../../docs/legal/privacy-policy.md");
    const policyContent = fs.readFileSync(policyPath, "utf-8");

    expect(policyContent).toContain("strictly necessary");
    expect(policyContent).toContain("Sentry");

    for (const cookieName of AUDITED_COOKIES) {
      expect(policyContent).toContain(`\`${cookieName}\``);
    }

    for (const storageKey of AUDITED_LOCAL_STORAGE_KEYS) {
      expect(policyContent).toContain(`\`${storageKey}\``);
    }

    for (const storagePrefix of AUDITED_SESSION_STORAGE_PREFIXES) {
      expect(policyContent).toContain(storagePrefix);
    }
  });

  it("verifies that no non-essential cookies, trackers, or marketing scripts exist", () => {
    const policyPath = path.resolve(__dirname, "../../docs/legal/privacy-policy.md");
    const policyContent = fs.readFileSync(policyPath, "utf-8");

    expect(policyContent).toContain("No advertising or analytics cookies");
    expect(policyContent).toContain("zero advertising, profiling, or third-party tracking cookies");
  });

  it("verifies that every cookie and storage key used in source code is in the audited inventory", () => {
    const frontendDir = path.resolve(__dirname, "..");
    const searchDirs = [
      path.join(frontendDir, "app"),
      path.join(frontendDir, "components"),
      path.join(frontendDir, "hooks"),
      path.join(frontendDir, "lib"),
    ];

    const sourceFiles: string[] = [path.join(frontendDir, "proxy.ts")];

    function collectFiles(dirPath: string) {
      const entries = fs.readdirSync(dirPath, { withFileTypes: true });
      for (const entry of entries) {
        const fullPath = path.join(dirPath, entry.name);
        if (entry.isDirectory()) {
          if (entry.name !== "node_modules" && entry.name !== ".next") {
            collectFiles(fullPath);
          }
        } else if (
          (entry.name.endsWith(".ts") || entry.name.endsWith(".tsx")) &&
          !entry.name.includes(".test.") &&
          !entry.name.includes(".spec.")
        ) {
          sourceFiles.push(fullPath);
        }
      }
    }

    for (const dir of searchDirs) {
      if (fs.existsSync(dir)) {
        collectFiles(dir);
      }
    }

    const literalCookieRegex = /cookies\.has\("([^"]+)"\)|cookies\.get\("([^"]+)"\)|document\.cookie\s*=\s*`?([a-zA-Z0-9_-]+)=/g;
    const literalStorageRegex = /(?:localStorage|sessionStorage)\.(?:getItem|setItem|removeItem)\((?:`([^`$]+)|"([^"]+)")/g;

    const discoveredKeys = new Set<string>();

    for (const filePath of sourceFiles) {
      const content = fs.readFileSync(filePath, "utf-8");
      let cookieMatch: RegExpExecArray | null;
      while ((cookieMatch = literalCookieRegex.exec(content)) !== null) {
        const cookieName = cookieMatch[1] || cookieMatch[2] || cookieMatch[3];
        if (cookieName) discoveredKeys.add(cookieName);
      }

      let storageMatch: RegExpExecArray | null;
      while ((storageMatch = literalStorageRegex.exec(content)) !== null) {
        const storageKey = storageMatch[1] || storageMatch[2];
        if (storageKey) discoveredKeys.add(storageKey);
      }
    }

    for (const key of discoveredKeys) {
      const isKnownCookie = AUDITED_COOKIES.includes(key);
      const isKnownLocalStorage = AUDITED_LOCAL_STORAGE_KEYS.includes(key);
      const isKnownSessionStorage = AUDITED_SESSION_STORAGE_PREFIXES.some((prefix) => key.startsWith(prefix));

      expect(
        isKnownCookie || isKnownLocalStorage || isKnownSessionStorage,
        `Discovered unaudited cookie or storage key in source code: "${key}"`
      ).toBe(true);
    }
  });
});
