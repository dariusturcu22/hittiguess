import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const TEST_PASSWORD = "correct-horse-battery-staple";
const ORIGIN = "http://localhost:3000";

function requestFor(pathname: string, cookieHeader?: string): NextRequest {
  return new NextRequest(new Request(`${ORIGIN}${pathname}`, {
    headers: cookieHeader ? { cookie: cookieHeader } : undefined,
  }));
}

async function loadModules() {
  vi.resetModules();
  const proxy = (await import("./proxy")).default;
  const betaAccess = await import("./lib/beta-access");
  return { proxy, betaAccess };
}

describe("closed beta proxy", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_CLOSED_BETA", "true");
    vi.stubEnv("BETA_ACCESS_PASSWORD", TEST_PASSWORD);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it.each(["/login", "/register", "/playlists", "/sessions/abc"])(
    "redirects a visitor without the access cookie from %s to the closed-beta screen",
    async (pathname) => {
      const { proxy } = await loadModules();
      expect(proxy(requestFor(pathname)).headers.get("location")).toBe(`${ORIGIN}/closed-beta`);
    },
  );

  it.each(["/", "/closed-beta", "/api/beta-access"])("keeps %s open to everyone", async (pathname) => {
    const { proxy } = await loadModules();
    expect(proxy(requestFor(pathname)).headers.get("location")).toBeNull();
  });

  it("rejects a forged access cookie", async () => {
    const { proxy, betaAccess } = await loadModules();
    const response = proxy(requestFor("/login", `${betaAccess.BETA_ACCESS_COOKIE}=forged`));
    expect(response.headers.get("location")).toBe(`${ORIGIN}/closed-beta`);
  });

  it("applies the normal rules to a visitor with a valid access cookie", async () => {
    const { proxy, betaAccess } = await loadModules();
    const token = betaAccess.createBetaAccessToken();
    const response = proxy(requestFor("/login", `${betaAccess.BETA_ACCESS_COOKIE}=${token}`));
    expect(response.headers.get("location")).toBeNull();
  });

  it("never grants access when no password is configured", async () => {
    vi.stubEnv("BETA_ACCESS_PASSWORD", "");
    const { proxy, betaAccess } = await loadModules();
    expect(betaAccess.isValidBetaPassword("")).toBe(false);
    expect(proxy(requestFor("/login", `${betaAccess.BETA_ACCESS_COOKIE}=`)).headers.get("location"))
      .toBe(`${ORIGIN}/closed-beta`);
  });

  it("accepts only the configured password", async () => {
    const { betaAccess } = await loadModules();
    expect(betaAccess.isValidBetaPassword(TEST_PASSWORD)).toBe(true);
    expect(betaAccess.isValidBetaPassword("wrong")).toBe(false);
  });

  it("leaves routing untouched when the flag is off", async () => {
    vi.stubEnv("NEXT_PUBLIC_CLOSED_BETA", "false");
    const { proxy } = await loadModules();
    expect(proxy(requestFor("/login")).headers.get("location")).toBeNull();
  });
});
