import assert from "node:assert/strict";
import { test } from "node:test";

import { BACKEND_PORT, FRONTEND_PORT, targetPortFor } from "./lan-https-proxy.mjs";

test("API, auth, WebSocket, and OAuth handshake paths go to the backend", () => {
  for (const backendUrl of ["/api/users/me", "/auth/login", "/ws", "/oauth2/authorization/google", "/login/oauth2/code/google", "/v3/api-docs"]) {
    assert.equal(targetPortFor(backendUrl), BACKEND_PORT, backendUrl);
  }
});

test("pages, Next assets, and the frontend's own OAuth redirect page go to the dev server", () => {
  for (const frontendUrl of ["/", "/login", "/playlists/7", "/_next/static/chunk.js", "/_next/webpack-hmr", "/oauth2/redirect?token=x", "/wsx"]) {
    assert.equal(targetPortFor(frontendUrl), FRONTEND_PORT, frontendUrl);
  }
});
