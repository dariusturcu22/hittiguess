# Dev setup

## Test accounts

The core service seeds three reusable accounts with a `TEST` role on every startup outside Production, so a multiplayer round (one DJ, one active player, one other player) can be tested with three genuinely separate logged-in sessions instead of one account reused across tabs.

Credentials:

- `test-agent-1@hittiguess.local` / `HittiguessTestAgent1!2026`
- `test-agent-2@hittiguess.local` / `HittiguessTestAgent2!2026`
- `test-agent-3@hittiguess.local` / `HittiguessTestAgent3!2026`

The seed mechanism is idempotent per account. Running the application again with an account already present does nothing for that one, it never creates a duplicate or errors.

This account never exists in Production. The seeder itself is disabled by a Spring profile condition when `APP_ENV` is `prod`, and a separate startup check fails the application if a `TEST`-role row is ever found while running against Production, regardless of how it got there.

This section belongs in `CONTRIBUTING.md` once story 36's open-source-readiness branch merges. Until then it lives here.

## Running the Playwright end-to-end suite

The `frontend/e2e` suite runs against a real, running backend and frontend,
not a mocked stack. Start the local stack first with `make dev` from the
repository root (or the equivalent manual two-terminal backend and frontend
start), then run the suite from `frontend`:

```
npm run e2e
```

This targets `http://localhost:3000` by default. The seeded test accounts
above are what the suite logs in with; it does not register its own users.

## Local-network playtest

To let other devices on the same network play against this machine:

1. Find this machine's LAN address (`ipconfig`, the Wi-Fi adapter's
   `IPv4 Address`).
2. Backend `.env`: set `FRONTEND_URL` and `FRONTEND_ALLOWED_ORIGINS` to
   `http://<address>:3000`. Restart the backend.
3. Frontend `.env.local`: set `NEXT_PUBLIC_API_URL` to
   `http://<address>:8080`. Restart the dev server or rebuild; the value
   embeds at build time.
4. Other players open `http://<address>:3000`. Accept the Windows Firewall
   prompts for Node.js and Java.
5. Players register local accounts. Google sign-in is unavailable over the
   LAN. Without a Resend key, verification links print in the backend log;
   forward them manually.
6. Voice uses STUN only. Direct connections normally succeed on one
   subnet; one-sided silence indicates TURN is required.
7. Afterward, delete playtest accounts, playlists, groups, and sessions
   from the dev database.
