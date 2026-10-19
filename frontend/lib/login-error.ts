import type { AxiosError } from "axios";

const FORBIDDEN_STATUS = 403;
const TOO_MANY_REQUESTS_STATUS = 429;

export const INVALID_LOGIN_MESSAGE = "Invalid email or password.";
export const RESEND_FAILED_MESSAGE = "Couldn't send the email. Try again in a moment.";
const EMAIL_NOT_VERIFIED_MESSAGE_PREFIX = "Email not verified";

type ErrorBody = { message?: string };

export function loginErrorMessage(error: unknown): string {
  const response = (error as AxiosError<ErrorBody> | undefined)?.response;
  const isExplainedRefusal =
    response?.status === FORBIDDEN_STATUS || response?.status === TOO_MANY_REQUESTS_STATUS;
  return isExplainedRefusal && response?.data?.message ? response.data.message : INVALID_LOGIN_MESSAGE;
}

export function isEmailNotVerifiedError(error: unknown): boolean {
  const response = (error as AxiosError<ErrorBody> | undefined)?.response;
  return response?.status === FORBIDDEN_STATUS && !!response.data?.message?.startsWith(EMAIL_NOT_VERIFIED_MESSAGE_PREFIX);
}

export function resendVerificationErrorMessage(error: unknown): string {
  const response = (error as AxiosError<ErrorBody> | undefined)?.response;
  return response?.status === TOO_MANY_REQUESTS_STATUS && response.data?.message
    ? response.data.message
    : RESEND_FAILED_MESSAGE;
}
