# Dev setup

## Test account

The core service seeds one reusable account with a `TEST` role on every startup outside Production. Agents and contributors running tests locally, manual or automated, log in with this account instead of registering a new one per run.

Credentials:

- Email: `test-agent@hittiguess.local`
- Password: `HittiguessTestAgent!2026`

The seed mechanism is idempotent. Running the application again with the account already present does nothing, it never creates a duplicate or errors.

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
