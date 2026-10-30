# Dev setup

## Test accounts

The core service seeds three reusable accounts on every startup outside Production, so a multiplayer round (one DJ, one active player, one other player) can be tested with three genuinely separate logged-in sessions instead of one account reused across tabs. Account 1 is an `ADMIN`, so it also opens the admin pages (backfill queue, song catalog, report queue); accounts 2 and 3 have the `TEST` role.

Credentials:

- `test-agent-1@hittiguess.local` / `HittiguessTestAgent1!2026`
- `test-agent-2@hittiguess.local` / `HittiguessTestAgent2!2026`
- `test-agent-3@hittiguess.local` / `HittiguessTestAgent3!2026`

The seed mechanism is idempotent per account. Running the application again with an account already present never creates a duplicate or errors; it only corrects the account's role if it differs, which is how an account 1 seeded before it became the admin gets promoted.

This account never exists in Production. The seeder itself is disabled by a Spring profile condition when `APP_ENV` is `prod`, and a separate startup check fails the application if a `TEST`-role row is ever found while running against Production, regardless of how it got there.

`CONTRIBUTING.md` links to this setup guide for the local-stack commands and test-account details.

## Running the backend on Windows

Starting the core service on Windows, with `./mvnw spring-boot:run` or any test that boots a Spring context, can fail at startup with `java.io.IOException: Unable to establish loopback connection` caused by `SocketException: Invalid argument: connect`. It comes from the JDK's own networking on Windows, not from the project, and `-Djava.net.preferIPv4Stack=true` does not fix it. The same failure stops `BackendApplicationTests` and `CsrfSecurityTest` locally; CI on Linux runs them.

Run the service in WSL instead. Unit tests that do not start Spring, such as mapper and service tests, run normally on Windows.

1. Use a WSL 2 Ubuntu distribution with JDK 25 installed (for example Amazon Corretto in `~/.jdks`). The repository is reachable from WSL at `/mnt/c/Users/<name>/Documents/hittiguess`.
2. Start the Postgres containers on Windows with `make db`. WSL reaches them on `localhost:5500` and `localhost:5501`.
3. From `backend/` in WSL, set `JAVA_HOME` to the Linux JDK explicitly, because the Windows `JAVA_HOME` leaks into WSL and breaks `mvnw`. Load `.env` with its carriage returns removed:

```
export JAVA_HOME="$HOME/.jdks/<jdk-25-directory>"
set -a
source <(tr -d '\r' < .env)
set +a
export JAVA_HOME="$HOME/.jdks/<jdk-25-directory>"
./mvnw spring-boot:run
```

4. The first start downloads dependencies and takes more than a minute. Check `http://localhost:8080/v3/api-docs` from inside WSL.
5. Depending on the WSL networking mode, Windows may not reach port 8080 of the WSL process. For tools that need the API from Windows, such as the client generator, save the spec from WSL (`curl localhost:8080/v3/api-docs -o openapi.json`) and point the tool at the file.

### Regenerating the API client

`npm run api:gen` in `frontend/` generates `hooks/generated`, `hooks/models`, and `hooks/zod` from the running backend's spec. The generator version is pinned by `package-lock.json`. Run `npm ci` in `frontend/` first: an older installed `orval` generates different code and rewrites well over a hundred unrelated files. With the pinned version the diff contains only real contract changes.

## Running the AI tests

Run `pytest` from `ai/` with the virtual environment (`.venv/Scripts/python.exe -m pytest tests -q` on Windows). Integration tests start Postgres through Testcontainers and need Docker running. On Windows, set `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1` first: `localhost` resolves to IPv6 there, and without the override each connection stalls for about a minute.

## Running the year benchmark

`ai/benchmark` measures the year lock against songs with known release years: `songs.csv` (65 songs) and `catalog.csv` (369 rows from the production catalog, 308 labelled). `captures/<source>/<slug>.json` holds each source's recorded answer, and `OBSERVATIONS.md` records the latest results. Run the commands from `ai/`.

Recording answers queries the live sources and the extraction model, so it makes real API calls that cost money and time. It needs the keys in `ai/.env` (OpenAI, Discogs, and the Wikidata and Wikipedia bot logins; see `ai/.env.example`). Run one process per source so a slow source does not hold the others up. Add `--songs benchmark/catalog.csv` for the catalog set and `--refresh` to record songs that already have a capture again:

```
.venv/Scripts/python.exe -m benchmark.capture --source musicbrainz
.venv/Scripts/python.exe -m benchmark.capture --source discogs --songs benchmark/catalog.csv
```

MusicBrainz pacing state lives in the `stage_pace` table of the local database, so start `make db` first. Without the database the pacer falls back to in-memory pacing, which waits longer between requests.

Scoring the recorded answers needs no network access. `--misses <rule>` lists the songs a rule gets wrong and `--unlabelled` prints the lock's year for songs without a known year:

```
.venv/Scripts/python.exe -m benchmark.evaluate --songs benchmark/catalog.csv --misses production --unlabelled
```

`tests/metadata/test_year_benchmark.py` replays `songs.csv` through the lock offline, and fails if the lock takes a wrong year or leaves more songs unresolved than allowed.

## Local stage pipeline

Admin backfill and patient rechecks always use the persisted stage queues. No cutover flag is required. A leftover `PIPELINE_STAGES_ENABLED` variable is ignored; the owner can remove it from production after the cleanup merges. The stage concurrency settings, polling intervals, and `PIPELINE_YOUTUBE_RESERVE_UNITS` remain supported. The sequential drain quota setting is removed.

The admin queue page is `/admin/backfill-queue`. The old `/admin/catalog-backlog` route and the catalog-seeding status and drain endpoints are removed. Enqueue still uses the stage pipeline. Historical `pending_imports` data and provisional and patient years remain available without a schema migration.

For a local startup smoke check, run core in WSL with `PIPELINE_STAGES_ENABLED` unset after loading the local environment. A successful startup must create the stage worker, completion service, HTTP client, and scheduler without the removed sequential-drain beans. Keep all startup checks on the local databases.

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
   embeds at build time. The dev server derives its allowed LAN origin
   from the same value, so no config edit is needed.
4. Other players open `http://<address>:3000`. Accept the Windows Firewall
   prompts for Node.js and Java.
5. Players register local accounts. Google sign-in is unavailable over the
   LAN. Configure Resend to deliver verification email, or use already verified
   development accounts. Without a Resend key, email is not sent. Verification
   tokens and links never appear in logs.
6. Voice always has STUN and also uses Cloudflare TURN when configured.
   Direct connections normally succeed on one subnet; one-sided silence
   requires checking the configured relay and actual media delivery.
7. Afterward, delete playtest accounts, playlists, groups, and sessions
   from the dev database.

### Voice and the DJ's audio need HTTPS

Browsers only allow the microphone, tab-audio capture, and the Clipboard API
on a secure origin, and a plain `http://<address>:3000` page isn't one. Over
plain HTTP, voice is listen-only and the DJ can't share YouTube audio. For a
playtest with voice, put both services behind one HTTPS origin:

1. Backend `.env`: set `FRONTEND_URL` to `https://<address>:3443` and add
   `https://<address>:3443` to `FRONTEND_ALLOWED_ORIGINS`. Restart the
   backend.
2. Frontend `.env.local`: set `NEXT_PUBLIC_API_URL` to
   `https://<address>:3443`. Restart the dev server.
3. Run `node scripts/lan-https-proxy.mjs <address>` from the repository
   root, next to the running backend and dev server. The first run creates
   a self-signed certificate for the address with the JDK's `keytool` in
   `.lan-https/` (gitignored); it's regenerated when the address changes.
4. Everyone opens `https://<address>:3443` and accepts the certificate
   warning once. API calls and sockets go through the same origin, so there
   is no second warning. Allow Node.js through the firewall for port 3443.
