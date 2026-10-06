import { NextRequest, NextResponse } from "next/server";

import {
  BETA_ACCESS_COOKIE,
  BETA_ACCESS_MAX_AGE_SECONDS,
  CLOSED_BETA_PATH,
  createBetaAccessToken,
  isValidBetaPassword,
} from "@/lib/beta-access";

const REDIRECT_AFTER_POST = 303;
const PASSWORD_FIELD = "password";

export async function POST(request: NextRequest) {
  const formData = await request.formData();
  const submittedPassword = formData.get(PASSWORD_FIELD);
  const token = createBetaAccessToken();

  if (typeof submittedPassword !== "string" || !isValidBetaPassword(submittedPassword) || token === undefined) {
    const rejectedUrl = new URL(CLOSED_BETA_PATH, request.url);
    rejectedUrl.searchParams.set("error", "1");
    return NextResponse.redirect(rejectedUrl, REDIRECT_AFTER_POST);
  }

  const response = NextResponse.redirect(new URL("/", request.url), REDIRECT_AFTER_POST);
  response.cookies.set(BETA_ACCESS_COOKIE, token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: BETA_ACCESS_MAX_AGE_SECONDS,
  });
  return response;
}
