import { describe, expect, it } from "vitest";

import {
  INVALID_LOGIN_MESSAGE,
  RESEND_FAILED_MESSAGE,
  isEmailNotVerifiedError,
  loginErrorMessage,
  resendVerificationErrorMessage,
} from "./login-error";

const UNAUTHORIZED_STATUS = 401;
const FORBIDDEN_STATUS = 403;
const TOO_MANY_REQUESTS_STATUS = 429;
const SERVER_ERROR_STATUS = 500;
const NOT_VERIFIED_MESSAGE = "Email not verified, check your inbox for a verification link";
const LOCKED_MESSAGE = "Too many failed attempts, try again later";

function failedResponse(status: number, message?: string) {
  return { response: { status, data: message === undefined ? undefined : { message } } };
}

describe("loginErrorMessage", () => {
  it("keeps the generic message for wrong credentials", () => {
    expect(loginErrorMessage(failedResponse(UNAUTHORIZED_STATUS, "Invalid username or password"))).toBe(INVALID_LOGIN_MESSAGE);
  });

  it("shows the server's reason when the email is not verified", () => {
    expect(loginErrorMessage(failedResponse(FORBIDDEN_STATUS, NOT_VERIFIED_MESSAGE))).toBe(NOT_VERIFIED_MESSAGE);
  });

  it("shows the server's reason when the account is locked or rate limited", () => {
    expect(loginErrorMessage(failedResponse(TOO_MANY_REQUESTS_STATUS, LOCKED_MESSAGE))).toBe(LOCKED_MESSAGE);
  });

  it("falls back to the generic message when a refusal has no readable reason", () => {
    expect(loginErrorMessage(failedResponse(FORBIDDEN_STATUS))).toBe(INVALID_LOGIN_MESSAGE);
  });

  it("does not expose messages from unexpected server errors", () => {
    expect(loginErrorMessage(failedResponse(SERVER_ERROR_STATUS, "Internal detail"))).toBe(INVALID_LOGIN_MESSAGE);
  });

  it("falls back to the generic message when there is no response at all", () => {
    expect(loginErrorMessage(new Error("Network Error"))).toBe(INVALID_LOGIN_MESSAGE);
    expect(loginErrorMessage(undefined)).toBe(INVALID_LOGIN_MESSAGE);
  });
});

describe("isEmailNotVerifiedError", () => {
  it("recognises the unverified-account refusal", () => {
    expect(isEmailNotVerifiedError(failedResponse(FORBIDDEN_STATUS, NOT_VERIFIED_MESSAGE))).toBe(true);
  });

  it("ignores other refusals, other statuses, and failures without a response", () => {
    expect(isEmailNotVerifiedError(failedResponse(FORBIDDEN_STATUS, "Access denied"))).toBe(false);
    expect(isEmailNotVerifiedError(failedResponse(UNAUTHORIZED_STATUS, NOT_VERIFIED_MESSAGE))).toBe(false);
    expect(isEmailNotVerifiedError(failedResponse(FORBIDDEN_STATUS))).toBe(false);
    expect(isEmailNotVerifiedError(new Error("Network Error"))).toBe(false);
  });
});

describe("resendVerificationErrorMessage", () => {
  it("shows the server's reason when resending is rate limited", () => {
    expect(resendVerificationErrorMessage(failedResponse(TOO_MANY_REQUESTS_STATUS, LOCKED_MESSAGE))).toBe(LOCKED_MESSAGE);
  });

  it("uses a plain retry message for every other failure", () => {
    expect(resendVerificationErrorMessage(failedResponse(SERVER_ERROR_STATUS, "Internal detail"))).toBe(RESEND_FAILED_MESSAGE);
    expect(resendVerificationErrorMessage(new Error("Network Error"))).toBe(RESEND_FAILED_MESSAGE);
  });
});
