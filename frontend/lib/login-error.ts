import type { AxiosError } from "axios";

const FORBIDDEN_STATUS = 403;
const TOO_MANY_REQUESTS_STATUS = 429;

export const INVALID_LOGIN_MESSAGE = "Invalid email or password.";

type ErrorBody = { message?: string };

export function loginErrorMessage(error: unknown): string {
  const response = (error as AxiosError<ErrorBody> | undefined)?.response;
  const isExplainedRefusal =
    response?.status === FORBIDDEN_STATUS || response?.status === TOO_MANY_REQUESTS_STATUS;
  return isExplainedRefusal && response?.data?.message ? response.data.message : INVALID_LOGIN_MESSAGE;
}
