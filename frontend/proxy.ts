import { NextRequest, NextResponse } from "next/server";

const PUBLIC_ROUTES = [
  "/login",
  "/register",
  "/forgot-password",
  "/oauth2/redirect",
  "/backend",
];

export default function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  const isPublic =
    pathname === "/" ||
    PUBLIC_ROUTES.some((route) => pathname.startsWith(route));

  if (isPublic) {
    return NextResponse.next();
  }

  const hasAccessToken = request.cookies.has("access_token");
  if (!hasAccessToken) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.png$).*)"],
};
