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

## LAN playtest with real devices

Same stack, opened to the local network so mates can play from their own
phones and laptops before any deployment. No code change is needed for
this; every API and WebSocket URL derives from `NEXT_PUBLIC_API_URL`,
invite links use the browser's own origin, and auth cookies stay correct
across ports on the same host.

1. Find the owner's LAN IP (`ipconfig`, the `IPv4 Address` on the Wi-Fi
   adapter, e.g. `192.168.1.50`).
2. Backend `.env`: set `FRONTEND_URL=http://<lan-ip>:3000` and add
   `http://<lan-ip>:3000` to `FRONTEND_ALLOWED_ORIGINS`. Restart the
   backend so verification and password-reset links use the LAN host.
3. Frontend `.env.local`: set
   `NEXT_PUBLIC_API_URL=http://<lan-ip>:8080`, then restart the dev
   server (or rebuild). This value inlines into the client bundle, so
   changing it without restarting does nothing.
4. Mates open `http://<lan-ip>:3000` in their browsers. Allow the Windows
   Firewall prompts for Node and Java if they appear.
5. Mates register local accounts; Google login is owner-only and won't
   work over LAN. Until a Resend key is set, verification links print in
   the backend log, relay the link to each mate by hand.
6. Voice is STUN-only with no TURN key provisioned. Same-subnet peers
   usually connect directly; if a mate hears nothing while others do,
   that is the expected signal that TURN is needed.
7. Afterward, delete playtest accounts, playlists, groups, and sessions
   from the dev database so leftover rows never pollute later testing.
