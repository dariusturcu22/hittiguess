import { afterEach, describe, expect, it } from "vitest";
import type { AxiosAdapter, InternalAxiosRequestConfig } from "axios";
import { AXIOS_INSTANCE } from "./axios-instance";

const FORBIDDEN_STATUS = 403;
const OK_STATUS = 200;
const STALE_TOKEN = "stale-token";
const CURRENT_TOKEN = "current-token";

function setCsrfCookie(token: string) {
  document.cookie = `XSRF-TOKEN=${token}; path=/`;
}

function respond(config: InternalAxiosRequestConfig, status: number, data: unknown) {
  const response = { data, status, statusText: "", headers: {}, config };
  if (status >= 400) {
    return Promise.reject(Object.assign(new Error("Request failed"), { config, response, isAxiosError: true }));
  }
  return Promise.resolve(response);
}

describe("AXIOS_INSTANCE CSRF handling", () => {
  const originalAdapter = AXIOS_INSTANCE.defaults.adapter;

  afterEach(() => {
    AXIOS_INSTANCE.defaults.adapter = originalAdapter;
  });

  it("retries a mutation once with the current token after a CSRF rejection", async () => {
    setCsrfCookie(STALE_TOKEN);
    const sentTokens: unknown[] = [];
    const adapter: AxiosAdapter = (config) => {
      sentTokens.push(config.headers["X-XSRF-TOKEN"]);
      if (sentTokens.length === 1) {
        setCsrfCookie(CURRENT_TOKEN);
        return respond(config, FORBIDDEN_STATUS, { message: "Invalid CSRF Token 'stale-token' was found on the request" });
      }
      return respond(config, OK_STATUS, { saved: true });
    };
    AXIOS_INSTANCE.defaults.adapter = adapter;

    const response = await AXIOS_INSTANCE.post("/api/groups/4/audio-capability", { canShareAudio: false });

    expect(response.data).toEqual({ saved: true });
    expect(sentTokens).toEqual([STALE_TOKEN, CURRENT_TOKEN]);
  });

  it("does not retry a forbidden response that is not a CSRF rejection", async () => {
    setCsrfCookie(CURRENT_TOKEN);
    let attempts = 0;
    AXIOS_INSTANCE.defaults.adapter = (config) => {
      attempts += 1;
      return respond(config, FORBIDDEN_STATUS, { message: "Only the group admin can do that" });
    };

    await expect(AXIOS_INSTANCE.post("/api/groups/4/settings", {})).rejects.toThrow();
    expect(attempts).toBe(1);
  });
});
