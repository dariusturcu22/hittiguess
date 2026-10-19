import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";

import proxy from "./proxy";

const PUBLIC_ROUTES_WITHOUT_A_SESSION = [
  "/",
  "/login",
  "/register",
  "/forgot-password",
  "/reset-password",
  "/verify-email",
  "/oauth2/redirect",
  "/playlists/join/abc123",
  "/groups/join/abc123",
];

const PROTECTED_ROUTES = ["/playlists", "/sessions/abc"];

function requestFor(pathname: string, cookieHeader?: string): NextRequest {
  return new NextRequest(new Request(`http://localhost:3000${pathname}`, {
    headers: cookieHeader ? { cookie: cookieHeader } : undefined,
  }));
}

describe("proxy middleware", () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it.each(PUBLIC_ROUTES_WITHOUT_A_SESSION)(
    "lets a logged-out request through to %s",
    (pathname) => {
      const response = proxy(requestFor(pathname));
      expect(response.status).not.toBe(307);
      expect(response.headers.get("location")).toBeNull();
    },
  );

  it.each(PROTECTED_ROUTES)("redirects a logged-out request to %s to /login", (pathname) => {
    const response = proxy(requestFor(pathname));

    expect(response.headers.get("location")).toBe("http://localhost:3000/login");
  });

  it.each(PROTECTED_ROUTES)("lets a request with a session through to %s", (pathname) => {
    const response = proxy(requestFor(pathname, "session_hint=1"));

    expect(response.headers.get("location")).toBeNull();
  });

  it.each(["/login", "/register", ...PROTECTED_ROUTES])(
    "never redirects %s to a closed-beta screen, even with the retired gate variables set",
    (pathname) => {
      vi.stubEnv("NEXT_PUBLIC_CLOSED_BETA", "true");
      vi.stubEnv("BETA_ACCESS_PASSWORD", "retired-tester-code");

      const location = proxy(requestFor(pathname)).headers.get("location");

      expect(location ?? "").not.toContain("closed-beta");
    },
  );
});
