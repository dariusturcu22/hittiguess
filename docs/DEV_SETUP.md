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
