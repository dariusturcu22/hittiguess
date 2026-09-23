import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";

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

function requestFor(pathname: string, cookieHeader?: string): NextRequest {
  return new NextRequest(new Request(`http://localhost:3000${pathname}`, {
    headers: cookieHeader ? { cookie: cookieHeader } : undefined,
  }));
}

describe("proxy middleware", () => {
  it.each(PUBLIC_ROUTES_WITHOUT_A_SESSION)(
    "lets a logged-out request through to %s",
    (pathname) => {
      const response = proxy(requestFor(pathname));
      expect(response.status).not.toBe(307);
      expect(response.headers.get("location")).toBeNull();
    },
  );

  it("redirects a logged-out request to a protected route to /login", () => {
    const response = proxy(requestFor("/playlists"));

    expect(response.headers.get("location")).toBe("http://localhost:3000/login");
  });

  it("lets a request with a session through to a protected route", () => {
    const response = proxy(requestFor("/playlists", "session_hint=1"));

    expect(response.headers.get("location")).toBeNull();
  });
});
