# TASKS.md: Active work

Only unfinished work belongs here. [PROJECT_STATE.md](PROJECT_STATE.md) defines story readiness; [ROADMAP.md](ROADMAP.md) sets the order and the release milestone. Completed implementation and audit evidence belong in [ARCHIVE.md](ARCHIVE.md).

## How to pick the next task

Read the sections top to bottom. The next task is the first unticked `- [ ]` item that is not skipped. Skip every item that starts with `Owner:` (it needs a decision, an account, or a manual step from the project owner), every section whose heading starts with `In progress` (another session owns that branch), and any item whose `Depends on:` line names work that is still unticked. Tick the item in the same pull request as the work. Sections run in dependency order: the in-progress branches, catalog accuracy, gameplay fixes, the frontend completion, production operations, and finally the Release sections that gate the public launch.

Sub-bullets (`Where`, `Done when`, `Depends on`, `Tests`) belong to their task and are not separate tasks. Every task is meant to be doable by one agent alone from its text; when it is not, fix the text first.

Frontend implementation and tests belong to story 28. Feature work requires a Ready story and tasks checked against current code. Story 34 replaces the existing abuse-event log stubs with analytics writes; it does not change enforcement.

## Story 55: Catalog accuracy

- [x] Bulk accept proposed release years. A verified song whose rerun finds a different year keeps its year and stores `proposedReleaseYear` and `proposedAt` (`SongCatalogService.java`, around lines 77 to 88), and today an admin accepts each one separately with `PUT /api/admin/songs/{id}` or dismisses it with `DELETE /api/admin/songs/{id}/proposal` (`AdminSongPipelineController`, `AdminSongPipelineService`). After a whole-catalog recheck that is hundreds of single clicks.
  - Where: `AdminSongPipelineController`, `AdminSongPipelineService`, the admin songs page `frontend/app/(app)/admin/songs/page.tsx` and its detail drawer, the generated API client in `frontend/hooks/generated`.
  - Backend: `POST /api/admin/songs/proposals/accept` taking `songIds`, which in one transaction sets each song's release year to its proposed year and clears the proposal; songs without a proposal and songs in an active game are skipped and reported by id; a matching dismiss endpoint clears proposals without changing years. Admin only.
  - Frontend: a "Proposed year" filter on the song catalog showing current and proposed year side by side, a checkbox per row, select all on the page, "Accept selected" and "Dismiss selected" with a confirmation that states the count, and a toast with accepted and skipped counts.
  - Tests: service unit tests (accept, skip without proposal, skip in active game), MockMvc admin and non-admin access, a real-row integration test that years change and proposals clear, and frontend tests for selection, confirmation and the toast.
  - Depends on: nothing. Most useful after `feature/recheck-whole-catalog` merges.
- [ ] Owner: after the recheck branch merges, run the whole-catalog recheck in production, then bulk accept the proposals that are correct and fix the remaining wrong years from the year audit by hand in the song drawer
- [ ] Owner: merge the 12 known duplicate song pairs in production with `POST /api/admin/songs/{keepId}/merge/{dropId}` (the endpoint exists; the duplicate list is in the admin catalog)
- [ ] Add the uniqueness constraints once the duplicates are merged: a unique index on `songs(youtube_id)` and a partial unique index on `pending_imports(youtube_id)` for rows still pending or processing, in a new Flyway migration after V52
  - Where: `backend/src/main/resources/db/migration`, `PendingImportStatus`, `SongCatalogService`, the import services that catch constraint violations.
  - Done when: the migration applies on a database with no duplicates, a concurrent double insert fails with a handled conflict instead of a 500, and an integration test proves that cleanup keeps playlist and alternate-ID links and that the indexes reject concurrent duplicates.
  - Depends on: the Owner merge task above, since the migration fails on a database that still has duplicate pairs.
- [ ] Owner: run the per-tier eligible-song count query from PR 318 against the production database and confirm each tier holds enough verified songs (`sitelinks >= 5`) for the game sizes players use; if a tier is short, import more songs

## Story 57: Gameplay fixes and acceptance

- [x] Pause the game when a required DJ's tab closes and never comes back. Today the pause starts only when the player's browser reports that the share ended, so a closed tab leaves the game running without that DJ's audio. Use the session's presence or socket disconnect (with a short grace period for reloads) to mark the DJ's audio status as not shared and pause, and resume through the existing re-share and test flow.
  - Where: `GameSessionService` pause logic, the STOMP disconnect handling, `frontend/hooks/use-song-mesh.ts`.
  - Tests: backend test that a disconnected required DJ pauses the session after the grace period and a reconnect within it does not.
- [x] Notice a stopped share while the player is on another page. The share's track `ended` event is only reported from the session page, so a DJ who stops sharing elsewhere in the app is noticed only on return. Report the status from wherever the long-lived capture lives (the app shell), so the pause starts at once.
  - Tests: Vitest that the track ending outside the session page reports the not-shared status.
- [x] Keep a staged song list across a backend restart during the audio check. Start with songs and start custom stage their songs in memory, so a restart during the check loses them and the start falls back to the group's playlists. Persist the staged list with the group's audio check (migration) and clear it on start or cancel.
  - Tests: integration test that a staged list survives reloading the group from the database.
- [x] Regenerate the API client with orval against a running backend and replace the hand-written audio check and recheck candidate types; the generated output must match without behaviour changes.
- [x] Light mockup `AdminSongRecheckLight.dc.html` for the admin recheck page, registered in the canvas.
- [x] Recover missed events after a reconnect. On connect, `use-game-session-realtime.ts` invalidates only the session query and `use-group-realtime.ts` invalidates the group, so a player who was offline across a phase change can keep a stale guess state, DJ link-out, active membership, chat history, or results.
  - Where: `frontend/hooks/use-game-session-realtime.ts`, `frontend/hooks/use-group-realtime.ts`, `frontend/components/group-chat-overlay.tsx`, the session page.
  - Done when: every STOMP (re)connect refetches session, guess state, current round link-out, group, active membership, chat history, and results once the game is completed; a game that completed while offline sends the player to its results; a group removal that happened while offline shows the removed state.
  - Tests: Vitest for each refetch on reconnect (extend `use-game-session-realtime.test.ts`, add one for `use-group-realtime.ts`), and a Playwright case that drops and restores the socket across a phase change.
- [x] Stop `GET /api/playlists/{playlistId}/import-jobs/active` from logging a 404 in the browser console when no job is active. It currently answers 404 for "no active job" (`PlaylistImportJobController`). Return 204 No Content, update `SYSTEM_REFERENCE.md`, regenerate the client, adjust `frontend/lib/playlist-import-job.ts` callers and their tests, and append the contract change to `DECISIONS.md`.
- [x] Playwright coverage of the game screens. Extend the existing lobby and gameplay specs to the 8-member lobby, each lobby popup, round intro, betting, reveal, between rounds, and game over at 1366x768, 1440x900, and 1920x1080 and at 125% and 150% zoom, including a check that the round subtitle is never covered by the guessing card; compare screenshots side by side with the design artboards. The e2e suite does not run in CI, so run it locally and record the result in the PR.
  - Where: `frontend/e2e`.
  - Depends on: the audio check branch, since it changes the lobby and the first round.
- [ ] Check in a real browser that attaching the song track at the start of the DJ's turn has no audible start-up delay, and pre-attach shortly before the song starts if there is one (`frontend/hooks/use-song-mesh.ts`)
- [ ] Check the sound-bar canvas at several zoom levels in a real browser: `devicePixelRatio` handling, a static resting state without audio, and `prefers-reduced-motion` respected (`drawSoundBars` in `frontend/components/gameplay/game-pieces.tsx`)
- [ ] Owner: two real Chrome windows with system audio, one DJ and one listener who never joined voice, over the course of a whole game, and report the result; an attempt from Firefox and Safari confirms they are shown the cannot-share warning
- [ ] Owner: Cloudflare TURN check. Both credentials are set on core; connect two devices on different networks with forced relay and confirm voice and the song stream still arrive. Add backend integration coverage if the HTTP-boundary tests leave gaps.

## Story 28: Frontend completion

The backend for account management and two-factor sign-in exists: `PATCH /api/users/me`, `GET /api/users/me/export`, `DELETE /api/users/me`, and `/auth/2fa/setup`, `/auth/2fa/confirm`, `/auth/2fa/disable`, `/auth/2fa/verify`. The frontend has no screens for any of them; only the generated hooks exist.

- [ ] Account settings route `/settings` (linked from the user menu in `frontend/components/nav-user.tsx`) with three parts: edit username and email (`PATCH /api/users/me`), download a personal data export (`GET /api/users/me/export`, saved as a JSON file), and delete the account behind a typed confirmation (`DELETE /api/users/me`, then clear the session and go to the landing page). Needs an approved mockup first: add dark and light account artboards to the design board and update `FRONTEND_CONTENT.md` and `FRONTEND_IMPLEMENTATION_GUIDE.md`.
  - Done when: each action works against the real backend, errors are shown in the page, the route is protected by the proxy, and unit tests cover permission, failure and confirmation paths.
- [ ] Two-factor screens on the settings route: setup with provisioning QR and secret, confirmation code entry, one-time backup codes shown once, disable with proof, and a second step on login that accepts a TOTP code or a backup code (`frontend/app/(marketing)/(auth)/(credentials)/login/page.tsx`)
  - Depends on: the account settings route above.
- [ ] Unit and backend-backed browser tests for two-factor: success, invalid code, disable proof, and one-use backup codes
- [ ] Compare all app routes and states at 1440x900 in both themes against the design board: identity and truncation, popups, playlist actions, tier selection, imports, gameplay and countdowns, results, admin, and history; fix every difference
- [ ] Compare only the landing page on mobile at 390x844 in both themes
- [ ] Review the exact Joined-tab copy

## Story 47: Mockup reconciliation

- [ ] Update the remaining source artboards and the published canvas to match the requirements approved during story 28's visual pass (account settings, audio check, recheck page), and validate that source and canvas are equal
  - Depends on: the Story 28 visual comparison.

## Story 7: Production hosting

Azure Container Apps hosts core and AI in one EU environment. Vercel hosts the frontend. Core stays single-replica because game and STOMP state are process-local. The whole-deployment ceiling is $20/month.

- [x] Restrict the new YouTube API key to the YouTube Data API; the exposed key is deleted
- [ ] Owner: add Azure cost anomaly alerts and a $12 shutdown Action Group that stops both Container Apps; the Log Analytics daily cap (0.25 GB, 30-day retention) and the $20 budget with email alerts are in place. An agent can write the script in `scripts/azure` once the Owner confirms what the action group does
- [ ] Owner: cost-control test, running the alerts and shutdown against an isolated resource group
- [ ] Load test core and AI: a repeatable script in `scripts/loadtest` that opens N concurrent games of 4 to 8 players (login, group, session, STOMP, placements and bets, voice and song signaling) and reports CPU, memory, WebSocket count and game-action latency; set the container resource limits from the result, and add an active-game admission limit in core with a tested busy response (HTTP 503 with a clear message when a session is started over the limit)
- [ ] Compare the C1-only JIT setting against the default under the same load test and keep the one that meets the latency target; both Azure apps run one always-on replica. Depends on: the load test above
- [x] Verify Vercel root frontend, production branch main, domain, API origin, and shared auth cookies
- [ ] Owner: confirm password-reset email arrives in production from the real sender domain (Google login, signup, verification, login, and the Resend domain and sender are working)
- [x] Add HTTP liveness/readiness probes to both apps
- [ ] Deployment smoke test script `scripts/smoke-test-production.sh` that checks the health endpoints, the HTTPS API, a WSS STOMP connect, the protected-route `session_hint` redirect, and that core reaches AI; run it against production and record the result
- [ ] Owner: production two-device acceptance of login, import, a full game, reconnect, voice, DJ audio, and results; do it after the audio check merges
- [ ] Plan the move to one Hetzner server before the Azure trial credit ends: write the deployment (compose file, reverse proxy with TLS, secrets) into `docs/ARCHITECTURE.md` and `scripts`, rehearse it in isolation, and prepare DNS cutover and rollback. Owner: the Hetzner account and the cutover date. Finish it before the public launch so the launch runs on its final host
- [ ] Owner: decide whether production deployments require GitHub environment reviewers

## Story 8: Production databases

Both Neon projects are provisioned. Core owns schema migrations; AI connects only to the transactional database.

- [ ] Inspect both production Flyway histories (core ends at V52 in the repo), core pgvector and indexes, and the analytics tables, and record any drift
- [ ] Verify intended TLS URLs and service boundaries, including deployed AI access to core and no browser credentials or AI access to analytics
- [ ] Measure the first request after Neon's idle suspend and change pool settings only if the wake time breaks a request
- [ ] Owner: configure Neon storage and compute notifications. Then document the backup export (point-in-time restore window, a scheduled logical dump) in `docs/DEV_SETUP.md` and rehearse restoring both databases into an isolated project; this is the restore test the Release 3 sign-off refers to
- [ ] Owner: decide whether the songs in the development database enter production; if yes, an agent performs only an approved catalog-only import

## Story 38: Production observability

- [ ] Production Sentry and OpenTelemetry settings are set on both apps; remaining: metrics through a Grafana Alloy container (core's `/actuator/prometheus` needs a login, so expose it on an internal-only management port first), import the dashboard, and add core uptime monitoring
- [ ] Verify real metrics, logs, traces, and error delivery; add a credential-gated delivery integration test without secret output

## Release 1: Legal and compliance

Story 56 covers the six Release sections, which hold everything the public launch needs before the first announcement. The app is already open to anyone (the closed-beta gate was removed, see `DECISIONS.md`), so these items are launch blockers for marketing, not for access. Drafts live in `docs/legal`; they describe the app as of 2026-09-12 and are out of date (they name Supabase as the database and omit voice, chat, game history, analytics tables, error reporting, email and the LLM providers). None of this is legal advice; the Owner items need a person to decide or sign off.

- [ ] Owner: decide the legal basics the other tasks in this section depend on and record them in `DECISIONS.md`: the person or entity named as data controller, a contact mailbox on the project's own domain (the drafts use a personal address), the minimum age (13 or 16, 16 being the EU default), the governing law, and whether a lawyer reviews the final texts
- [ ] Rewrite `docs/legal/privacy-policy.md` to match what the app does today, as plain statements of fact.
  - Where: `docs/legal/privacy-policy.md`; facts come from the entities in `backend/src/main/java/org/dariusturcu/backend/model`, `analytics`, `history`, `research`, and the auth and email config.
  - Must cover: account data (username, email, password hash, Google identifier, two-factor secret and backup codes), per-group display names and avatars, playlists and songs, group chat messages, voice (peer to peer, not recorded or stored by the server, relayed by Cloudflare TURN only when a direct link fails), game history and statistics, internal analytics events, IP addresses used for rate limiting and server logs, and error reports.
  - Must list the processors and what each receives: Vercel (frontend hosting), Microsoft Azure Container Apps (backend hosting), Neon (databases), Cloudflare (TURN relay), Resend (transactional email), Sentry and Grafana Cloud (error reports, traces, metrics, logs), Google (sign-in and the YouTube API), DeepInfra and OpenAI (song metadata only, never account data), and YouTube (playback on its own site; link to the Google Privacy Policy as the YouTube API Services terms require).
  - Done when: it states retention for every data category (see the retention task below), international transfers, the rights to access, correct, export and delete with the real routes, the age rule, and the cookie facts; the Supabase statement is gone.
  - Depends on: the Owner decision task above.
- [ ] Rewrite `docs/legal/terms-of-service.md`: acceptable use including chat and voice conduct, the licence users grant for names, playlists and avatars they publish, the copyright and takedown route (see the copyright task), suspension and termination, age, governing law, the YouTube Terms of Service reference, and a clear statement that song data comes from third parties and years can be wrong. Depends on: the Owner decision task above.
- [ ] Owner: have the final privacy policy and terms reviewed if a lawyer is wanted, then tell the agent the texts are approved
- [ ] Publish the policies as pages: `/privacy`, `/terms`, and a `/legal` index, rendered from the markdown in `docs/legal` (read at build time, so the repo files stay the single source), reachable without login.
  - Where: `frontend/app/(marketing)`, `PUBLIC_ROUTES` in `frontend/proxy.ts`, the landing footer in `frontend/app/(marketing)/(landing)/page.tsx` (its Legal links are plain text today), the user menu.
  - Done when: both pages render in both themes, show the last-updated date, are linked from the landing footer, the register page, and the user menu, and have tests for rendering and for being public.
  - Depends on: the two rewrite tasks above (publish placeholders only if the Owner approves).
- [ ] Ask for consent at sign-up: a required checkbox "I am at least N years old and accept the Terms and Privacy Policy" on `/register`, and the same acceptance for a first Google sign-in; store the accepted terms version and time on the user (new Flyway migration, `User` entity, `AuthController` registration request and the OAuth success handler), and re-prompt when the version changes.
  - Done when: registration without the box is rejected by the API, not only by the form; existing users are asked on next login; backend and frontend tests cover both.
  - Depends on: the Owner decision task (age) and the page publishing task (links).
- [ ] Data retention: define a period for each data category in the privacy policy and implement what is missing. Known gaps: no purge of accounts that never verified their email, and unclear lifetimes for expired refresh tokens, password-reset tokens, finished pipeline jobs, and chat history of deleted groups. Existing: analytics events 365 days (`AnalyticsRetentionSweeper`), group expiry (`GroupExpirySweeper`), Log Analytics 30 days.
  - Done when: a scheduled sweeper removes each category past its period, each sweeper has an integration test, and the policy states the same numbers.
- [ ] Account deletion and export completeness: an integration test that creates a user with playlists, group membership, chat messages, history rows, a two-factor secret and an avatar, runs `DELETE /api/users/me`, and asserts that no personal data remains anywhere (including the history database via `AccountHistoryDeleted`); another that asserts the export (`PersonalDataExportService`) contains every category the privacy policy promises. Fix every gap found.
- [ ] Cookie and storage audit: list every cookie and browser storage key the site sets (`access_token`, `refresh_token`, `session_hint`, `XSRF-TOKEN`, the theme and audio-device preferences, the Sentry SDK) and state in the privacy policy that they are strictly necessary. Add a consent banner only if the audit finds a non-essential cookie or tracker; if the launch analytics task adds one, use a cookieless tool so no banner is needed.
- [ ] Copyright and takedown route: add a section to the terms and a `/copyright` page (or a section of `/terms`) with the contact address, what a notice must contain, and what happens next; give admins a fast way to remove a reported playlist, avatar, or song and keep a record of the takedown. Depends on: the Owner decision task (contact address).
- [ ] YouTube API Services compliance review against the current Developer Policies and Terms of Service: the privacy policy links to the Google Privacy Policy and names YouTube API Services; the terms bind users to the YouTube Terms of Service; no embedded player (already true); stored YouTube API data respects the refresh and retention limits (durations use a 29-day cycle; check titles, channel names and thumbnails stored by the pipeline); display and branding rules are followed; the daily quota is enough for launch traffic.
  - Done when: a checklist of each policy item with the code that satisfies it is added to `docs/ARCHITECTURE.md`, and gaps are fixed.
- [ ] Owner: decide whether capturing YouTube playback audio through screen share and relaying it to the other players is acceptable under the YouTube Terms of Service and music licensing for a public product, and record the decision in `DECISIONS.md`. If it is not, the remote DJ flow needs a different design before launch.
- [ ] Credits and licences: add a `/credits` page, linked from the footer, that attributes every data source and states its licence and required notice: MusicBrainz (core data CC0, supplementary data CC BY-NC-SA), Discogs (API terms and attribution), Wikidata (CC0), Wikipedia (CC BY-SA, with a link to the article used), YouTube, and the open-source fonts and libraries shipped to the browser. Verify each against its current terms and fix `DECISIONS.md` if a licence changed. Depends on: the footer task in Release 2.

## Release 2: Site basics

- [ ] Shared public footer for the landing, legal, credits, about and contact pages with links to Privacy, Terms, Copyright, Credits, About, Contact, and GitHub; replace the plain-text spans in `frontend/app/(marketing)/(landing)/page.tsx`. Tests: the links render and point to public routes.
- [ ] Contact page `/contact` with the project mailbox, a link to GitHub issues, and how to report abuse or a wrong song year. Depends on: the Owner decision task in Release 1.
- [ ] About page `/about`: what hittiguess is, how a round works in four sentences, that it is free, open source and has no ads or trackers, and who builds it. Tests: renders, linked from the footer.
- [ ] 404 and error pages: `not-found.tsx`, `error.tsx` and `global-error.tsx` in `frontend/app`, styled with the design tokens in both themes, offering a way home or to login, with `global-error.tsx` reporting to Sentry. Tests: an unknown route shows the 404 page, a thrown render error shows the error page.
- [ ] SEO metadata: `metadata` in the root layout (title template, description, `metadataBase` from the production URL, canonical URL, theme color) and per-page titles and descriptions for the landing and public pages; authenticated app routes and invite links are `noindex`. Currently no page exports metadata.
- [ ] `sitemap.ts` and `robots.ts` in `frontend/app`: list the public pages, disallow the app routes, invite links and `/admin`, and point to the sitemap. Test the generated output.
- [ ] Open Graph and Twitter share images: `opengraph-image` and `twitter-image` at 1200x630 with the logo and tagline, plus `openGraph` and `twitter` metadata; invite links get a generic preview. The brand assets are in `logo-export/` on the owner's machine (untracked); an agent without them asks the Owner to commit the needed files first. Depends on: the SEO metadata task.
- [ ] Favicon and app icon set: `frontend/app` already has `icon.svg`, `favicon.ico` and `apple-icon.png`; add a web app manifest with 192 and 512 pixel icons (one maskable), `theme-color`, and check the icons in light and dark tab bars. Source files come from `logo-export/` as above.
- [ ] Owner: email deliverability for the sending domain used by Resend (verification and password reset mail): publish SPF, DKIM and a DMARC policy at the DNS provider, then send test mail to Gmail, Outlook and iCloud accounts and confirm it lands in the inbox
- [ ] Email content: verification and reset messages carry the product name, the contact address, a plain-text part, and a note that the message was triggered by a sign-up or reset request. Where: `backend/src/main/java/org/dariusturcu/backend/config/EmailConfig.java` and the email service. Test: the rendered message contains each part.

## Release 3: Security audit

Existing protections to build on: JWT cookie authentication (`JwtAuthenticationFilter`), CSRF tokens with a double-submit cookie, an IP and user rate limiter (`RateLimitingFilter`), `AdminAccessGuard`, STOMP channel and subscription interceptors, a Content-Security-Policy and Referrer-Policy on the frontend, and production start-up guards. The tasks below check each one, add what is missing, and leave a test behind.

- [ ] Endpoint authorization matrix: a generated test that walks every mapping in the Spring handler registry and asserts that each route outside the public allow-list in `SecurityConfig` answers 401 without a session, that every `/api/admin/**` route answers 403 for a normal user, and that the public allow-list is exactly the intended list; plus ownership tests (a user cannot read or change another user's playlists, groups, sessions, songs, exports or avatars by changing an id). Fix every gap. Where: `backend/src/test`, `SecurityConfig`, `AdminAccessGuard`, the services.
- [ ] CSRF and cookie review: re-read the `ignoringRequestMatchers` list in `SecurityConfig` (refresh and password-reset are exempt), confirm each exempt route is safe without a token, confirm the production cookies are `Secure`, `HttpOnly` where possible, and `SameSite` as intended with the right domain, and add an integration test that a state-changing request without the token is rejected on a sample of routes.
- [ ] CORS: production allowed origins are exactly the production frontend origins, no wildcard with credentials, and the preflight answer lists only the methods and headers used. Test the configured origin list against a foreign origin.
- [ ] Rate limiting review: confirm the limiter keys on the real client address behind Azure's proxy (forwarded header handling and spoofing), and add or tighten limits for registration, login, password reset and verification mail sends, song submission and import, reports, chat messages, STOMP sends (chat, drag preview, signaling), and the avatar upload. Tests: each limit returns 429 after the threshold and recovers.
- [ ] Input validation and injection: every request DTO has bean validation with length and format limits; the avatar upload checks type, size and dimensions; names and chat text are rendered as text only (search the frontend for raw HTML insertion); native SQL uses parameters; the URL and playlist import paths cannot be pointed at internal addresses. Add tests for the limits found missing.
- [ ] Secrets handling: scan the full git history with a secret scanner, rotate anything found, confirm `.env` files are untracked and `.env.example` holds placeholders, turn on GitHub secret scanning and push protection, and list every production secret with where it is stored and who can rotate it in `docs/DEV_SETUP.md`. Owner: perform the key rotations in the provider consoles.
- [ ] Dependency vulnerability scans in CI and a clean first run: `npm audit` for the frontend, a Maven dependency check (OWASP dependency-check or the GitHub dependency graph) for core, and `uv` with `pip-audit` for the AI service; add `.github/dependabot.yml` for all three ecosystems and GitHub code scanning; fail the PR check on high or critical findings and fix or document every current one.
- [ ] Security headers: the frontend sends only a CSP and Referrer-Policy (`frontend/next.config.ts`). Add `Strict-Transport-Security`, `X-Content-Type-Options`, `Permissions-Policy` (microphone and display capture for the site only), and `Cross-Origin-Opener-Policy`, check what the API answers, and replace `'unsafe-inline'` in `script-src` with nonces if Next allows it. Verify with an online header scanner and keep voice, screen share and the noise filter working. Tests: header values asserted in a config test.
- [ ] WebSocket security: confirm the handshake rejects unauthenticated and foreign-origin connections, that every subscription and send destination checks session or group membership (`StompSubscriptionAuthorizationInterceptor`, `StompAuthenticationChannelInterceptor`, the signaling controllers), that message sizes are bounded, and that a removed member's sockets close (`UserSocketCloser`). Tests: a non-member cannot subscribe to or send on another group's or session's destinations.
- [ ] Moderation of user content: chat messages, display names, group and playlist names, public playlists on Explore, and avatars can carry abuse. Add a report action for playlists, users and chat messages (songs already have reports: `SongReportController`, `AdminSongReportController`), an admin review queue for them, the ability for an admin to suspend an account or hide a public playlist, and per-player mute of chat. Owner decision first on the policy (what is removed, response time); record it in `DECISIONS.md`. Depends on: the Owner decision in Release 1 for the contact route.
- [ ] Admin hardening: require two-factor for admin accounts, add an audit log of admin actions (pipeline controls, merges, year changes, deletions, suspensions) with an admin view, and confirm the admin routes are not indexed or linked for normal users. Depends on: the two-factor screens in Story 28.
- [ ] Dynamic scan: run OWASP ZAP baseline against a staging or local full stack, triage the findings, and keep the report summary in the PR description. Depends on: the other tasks in this section.
- [ ] Owner: backups and restore are tracked under Story 8 (the Neon export and isolated restore rehearsal); sign off here that the restore worked and the recovery targets are acceptable before launch

## Release 4: Reliability and operations

Story 7 holds the cost alerts, load test and active-game admission limit, and Story 38 holds metrics and delivery checks. The tasks below add what a public launch needs on top of them.

- [ ] Alert rules and an uptime monitor: after Story 38, define alerts for 5xx rate, p95 latency, core or AI container restarts, database connection failures, a stalled pipeline stage, YouTube quota near the daily limit, and LLM spend, plus external uptime checks for the site, the API health endpoint, and a WebSocket connect. Owner: create the monitoring account and the notification route (email or phone), then an agent configures the rules as code or documents them in `docs/ARCHITECTURE.md`.
- [ ] Error tracking verified end to end for the frontend, core, and AI: release tagging, source maps uploaded for the frontend, personal data scrubbing (no default PII, no tokens, no chat text), sampling rates, and one alert rule per service. Test by triggering one error per service in a non-production environment.
- [ ] Log hygiene and retention: grep the logs of a full game for emails, tokens, cookies, IP addresses and chat text, remove or mask what appears, and set and document retention for application logs and traces. Test: a log-capture test on login, password reset and chat asserts none of those values is written.
- [ ] LLM and third-party spend limits: the pipeline tracks LLM cost but has no ceiling. Add configurable daily and monthly cost ceilings in core that pause the LLM stages and raise the dashboard banner when reached, with tests; document the YouTube, Resend, Cloudflare TURN and Neon plan limits and what happens at each. Owner: set hard monthly caps in the DeepInfra, OpenAI, Cloudflare and Resend consoles.
- [ ] Signup spike safeguards: confirm what happens when many accounts register at once (email send limits, database connections on the Neon plan, single-replica core) and add a switch that turns registration into a waitlist message without a deploy. Depends on: the Story 7 load test.
- [ ] Incident runbook `docs/RUNBOOK.md`: restart a service, roll back a deploy, pause the pipeline, rotate each secret, restore a database, disable registration, and where each dashboard lives. Depends on: the tasks above and the rollback task in Release 6.
- [ ] Owner: decide whether a public status page is wanted; if yes, an agent wires it to the uptime checks

## Release 5: Product polish

- [ ] Owner: decide the mobile scope for launch. Today only the landing page is responsive; phones cannot DJ because they cannot share audio, but they can play. Options: desktop only with a clear notice on phones, or phone support for joining, guessing and betting. Record the choice in `DECISIONS.md` and `GAME_DESIGN.md`.
- [ ] Implement the mobile decision: either a notice shown on phone-sized screens when entering the app or a lobby, with the supported browsers, or responsive lobby and gameplay layouts for the chosen screens with mockups first. Depends on: the Owner decision above.
- [ ] Onboarding: a "How to play" screen reachable from the landing page and the lobby that explains a round, tokens and betting, the DJ role and the audio check, plus a first-run prompt for new accounts that opens it once. Needs a mockup and copy in `FRONTEND_CONTENT.md` first. Tests: the prompt appears once per account and can be dismissed.
- [ ] Empty, loading, and error states: audit every list and page (playlists, explore, search, history, groups, import, admin) for all three states using `phantom-empty-state` and the shared alert style, and fix those missing. Tests: each page renders its empty and error state.
- [ ] Accessibility pass: keyboard-only play-through of sign-up, playlist creation, lobby, a full game and results; a keyboard alternative for dragging a card onto the timeline; focus trapping and return in dialogs; live regions for phase changes, countdowns and results; AA contrast in both themes; reduced-motion respected everywhere; names and roles on icon-only buttons. Add axe checks to the Playwright specs for every route and fix findings. Replaces the older contrast and keyboard task in Story 28.
- [ ] Performance budget: record Lighthouse results for the landing page and the lobby on a throttled mobile profile and desktop, set targets (landing LCP under 2.5 s, no layout shift, a JavaScript budget per route), lazy-load the noise filter WebAssembly and the Sentry replay code if not already, and add a bundle-size check to the frontend build in CI. Tests: the CI check fails when a budget is exceeded.
- [ ] Browser support notice: detect unsupported browsers for voice and screen share (Safari, Firefox, phones) and show the same kind of dismissible note the audio warning uses on sign-up and the lobby, with the supported list from `GAME_DESIGN.md`. Depends on: the mobile decision.

## Release 6: Launch

- [ ] Owner: confirm the launch date, the public domain redirects (apex and www), and that the production environment is the one being announced
- [ ] Catalog readiness: a launch needs enough verified songs for new groups. Set the target per tier (for example 300 songs each at Easy, Medium and Hard) with the Owner, import and verify until the counts are met, accept the proposed years, and check that the Explore page has at least a handful of good public playlists. Owner: curate the starter playlists. Depends on: the Catalog accuracy section.
- [ ] Landing page copy and structure: hero, how it works in three steps, screenshots, a short FAQ (free, ads, accounts, supported browsers, where the music plays), and calls to action to register, matching the design board. Mobile layout is already in scope for this page. Needs copy in `FRONTEND_CONTENT.md` first. Depends on: the onboarding and mobile tasks.
- [ ] Privacy-friendly analytics: Owner decides the tool (a cookieless hosted service or a self-hosted one) and the account; an agent then adds the script with the CSP updated in `frontend/next.config.ts`, records only page views and sign-up and game-start events, honours Do Not Track, and updates the privacy policy and credits. No consent banner may be needed. Depends on: the cookie audit in Release 1.
- [ ] Marketing assets in a `docs/launch` folder or the owner's own storage: screenshots at 1440x900 in both themes (lobby, round, reveal, results), a 30 to 60 second demo video script and shot list, and the logo kit from `logo-export/`. Owner: record and edit the video. Depends on: the visual pass in Story 28.
- [ ] LinkedIn post and launch copy: draft the post, a short announcement for other channels, and a one-paragraph project description in `docs/launch/announcement.md`, in the plain style of the repo. Owner: edit and publish. Depends on: the marketing assets task.
- [ ] Pre-launch checklist `docs/launch/checklist.md`: one line for every task in Releases 1 to 5 with its proof (page URL, test name, or console screenshot), the production smoke test (health, HTTPS API, WSS, login, import, full two-device game with voice and DJ audio, results), a mobile check of the landing page, and a final read-through of the legal pages. Depends on: all other tasks above.
- [ ] Rollback plan in `docs/RUNBOOK.md`: how to return core and AI to the previous Azure Container Apps revision, how to promote the previous Vercel deployment, why Flyway migrations are forward-only and the rule that a migration shipped in the launch release must be backward compatible with the previous app version, a database backup taken immediately before launch, and who decides to roll back. Rehearse the backend and frontend rollback once in the isolated environment. Depends on: the Story 7 Hetzner plan, which changes where the backend runs.
- [ ] Launch-day watch: a short written plan for the first 48 hours (who watches which dashboard, alert thresholds, the registration switch, where feedback arrives) added to the runbook. Owner: staff the watch.
- [ ] Owner: after launch, review the feedback channel and the issue templates in `.github/ISSUE_TEMPLATE` and decide what goes into the next roadmap round

## Story 34: Usage analytics, definition required

Core participant history/statistics and analytics difficulty research already exist. Raw analytics retention is 365 days. This story covers internal usage and abuse visibility with no third-party trackers.

- [ ] Confirm event fields against current code. `AnalyticsEventRecorder` and the eight `AnalyticsEventType` values (session start and end, login, failed login, playlist created, song submitted, rate limit exceeded, report submitted) exist, but only abuse-visibility stubs call them; list for each type where it must be written and which fields it carries
- [ ] Implement approved event writes, replace abuse-event stubs, and build internal aggregates/repeated-abuse visibility
- [ ] Define and implement the reviewed notice or consent behavior
- [ ] Integration tests: event records and dashboard/query aggregates

## Deferred work

- [ ] Story 30: after enough real research data exists, train personalized difficulty, schedule retraining, and monitor against the shipped aggregate baseline; test held-out predictions and retraining/staleness detection
- [ ] Story 41: tune source-match confidence using real submissions and route uncertain cases to manual review without rejecting niche tracks; define classifier/review tests with the approved thresholds
- [ ] Story 24: add a gather-boundary per-source timeout and unit tests when the latency budget requires it; request-level timeouts already exist
- [ ] Define curated metadata evidence fields, provenance, retention/refresh, access, and a Ready story with persistence/access/retention tests before populating metadataRaw
- [ ] Flutter: replace embedded playback with official YouTube app/site link-out and test the DJ action; mobile remains deprioritized
- [ ] Optional: removed-item exit motion beyond voice departure, with reduced-motion tests

Genre enrichment and automatic Topic-upload upgrades are dropped. Pattern-based user moderation remains an unscoped future idea after usage/abuse data exists.

## Story 52: Live playtest fixes

A Chrome and Firefox playtest on the production site found that DJ audio never reached the other players, a player cannot leave a running session, and several voice and layout problems. Voice chat, playlists, and song adding worked.

DJ audio (the most important batch):

- [x] Confirm in a two-Chrome production run that the DJ's shared audio reaches the other players (the third playtest found audio working end to end)
- [x] Share once per match: a DJ who has not yet shared is asked for the screen share with system audio first, and only then does the Open on YouTube button appear; the share stays alive for the rest of the match, so later rounds do not ask again
- [x] Share the DJ's audio only during that player's own DJ turn: outside it the captured audio is not mixed into the voice stream and nobody receives it, and the audio stops when the DJ locks in; only the current DJ's audio is sent to the other players, never to the DJ; unit tests for the gating
- [x] Detect browsers that cannot capture audio (Firefox, Safari), report the capability to the server when a player joins, and skip those players in DJ rotation; they still play and guess, and see a short notice explaining why; a rotation with no capable player shows the group a clear message; unit and backend integration tests
- [x] Open the YouTube link in a new tab of the same browser window, not a popup window
- [x] The card's sound wave moves for every player from the DJ's audio and ignores voice chat; the DJ's own wave follows the shared audio

Leaving and ending:

- [x] A player can leave a running session while the game continues without them: Leave game also leaves the group, because a member of a locked group is sent back into its game
- [x] The voice indicator shows a player in voice only after they joined; find and fix the state that marks a player as in voice without joining; unit test
- [x] A player whose tab closed or whose connection dropped is removed from voice after 2 minutes; backend scheduler and integration test

Voice controls:

- [x] Volume sliders for the microphone and the speaker next to the device pickers; the microphone level meter and the speaker test tone already existed

Visual:

- [x] Card colors that are too dark on the dark theme get a readable variant; check every song color
- [x] Investigated the `wss://api.hittiguess.com/ws` closed-before-established message: it appears when a page unmounts its socket while connecting and needs no change
