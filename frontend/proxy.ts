import { NextRequest, NextResponse } from "next/server";

const PUBLIC_ROUTES = [
  "/login",
  "/register",
  "/forgot-password",
  "/oauth2/redirect",
];

// Invite links carry their own context and render a logged-out state with a
// login prompt, so they stay reachable without a session.
const PUBLIC_JOIN_PREFIXES = ["/playlists/join/", "/groups/join/"];

export default function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  const isPublic =
    pathname === "/" ||
    PUBLIC_ROUTES.some((route) => pathname.startsWith(route)) ||
    PUBLIC_JOIN_PREFIXES.some((prefix) => pathname.startsWith(prefix));

  if (isPublic) {
    return NextResponse.next();
  }

  // refresh_token itself is scoped to /auth/refresh on the backend, so this
  // route-matching middleware never sees it directly; session_hint mirrors
  // its lifetime at Path=/ purely as a signal the middleware can read. The
  // actual API calls still authenticate off access_token/refresh_token.
  const hasActiveSession = request.cookies.has("session_hint");
  if (!hasActiveSession) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.png$).*)"],
};
