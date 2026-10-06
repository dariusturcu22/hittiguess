import { createHash, createHmac, timingSafeEqual } from "node:crypto";

export const CLOSED_BETA_ENABLED = process.env.NEXT_PUBLIC_CLOSED_BETA === "true";
export const BETA_ACCESS_COOKIE = "beta_access";
export const BETA_ACCESS_MAX_AGE_SECONDS = 60 * 60 * 24 * 90;
export const CLOSED_BETA_PATH = "/closed-beta";
export const BETA_ACCESS_API_PATH = "/api/beta-access";
export const LEGACY_APP_URL = "https://my-hitster.dariusturcu22.com";

const TOKEN_PAYLOAD = "beta-access-v1";

function sha256(value: string): Buffer {
  return createHash("sha256").update(value).digest();
}

function safeEqual(first: string, second: string): boolean {
  return timingSafeEqual(sha256(first), sha256(second));
}

function configuredPassword(): string | undefined {
  const password = process.env.BETA_ACCESS_PASSWORD;
  return password ? password : undefined;
}

export function isValidBetaPassword(candidate: string): boolean {
  const password = configuredPassword();
  return password !== undefined && safeEqual(candidate, password);
}

export function createBetaAccessToken(): string | undefined {
  const password = configuredPassword();
  return password === undefined
    ? undefined
    : createHmac("sha256", password).update(TOKEN_PAYLOAD).digest("hex");
}

export function hasBetaAccess(token: string | undefined): boolean {
  const expectedToken = createBetaAccessToken();
  return expectedToken !== undefined && token !== undefined && safeEqual(token, expectedToken);
}
