# Dev setup

## Test accounts

The core service seeds three reusable accounts on every startup outside Production, so a multiplayer round (one DJ, one active player, one other player) can be tested with three genuinely separate logged-in sessions instead of one account reused across tabs. Account 1 is an `ADMIN`, so it also opens the admin pages (catalog backlog, report queue); accounts 2 and 3 have the `TEST` role.

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
