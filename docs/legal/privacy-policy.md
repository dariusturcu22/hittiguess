# Privacy Policy

Last updated: 2026-10-31

Status: draft, not yet published as a page. The release tasks in `docs/TASKS.md` (Release 1) finish and publish it after the project owner decides the controller, contact address, and minimum age.

This policy describes what hittiguess collects, why, and what a user can do about it. It covers the app as it exists today, not features that haven't shipped yet.

## What gets collected

**Account data.** When an account is created, hittiguess stores a username, an email address, and, for accounts created through an OAuth provider (such as Google), the provider's own account identifier. A locally-created account also stores a password, stored as a hash, never in plain text.

**Playlist and song data.** hittiguess stores the playlists a user creates or joins, their membership details in each (role, permissions, a per-playlist display name and avatar), and the songs they submit to the catalog (title, artist, release year, a YouTube video ID, and related metadata).

**Game and group data.** A group stores its members with a per-group display name and avatar, its settings, and its text chat messages for as long as the group exists. A finished or abandoned game saves a compact record of the players and results to a history database, which powers a player's history and statistics. Voice chat runs directly between players' browsers and is not recorded or stored; when a direct connection fails, Cloudflare relays the audio without storing it.

**Technical data.** Servers see the IP address of each request and use it for rate limiting. Error reports, traces, and logs go to Sentry and Grafana Cloud. Internal analytics events (a table of session starts, logins, failed logins, and similar counts) exist in a separate database with a 365-day retention, and the code that writes them is still being finished.

**No tracking.** hittiguess does not use any third-party advertising or tracking service. If usage analytics is added later, this policy will be updated first, and a first-party or cookieless analytics system is the only kind planned.

## Why this data is collected

Account data identifies who's playing and lets an account be recovered or managed. Playlist and song data is the actual content of the game: what's in a playlist, who added a song, and who's a member of what.

## Who else sees this data

hittiguess's core service and its AI microservice both operate on this data directly. The AI microservice calls DeepInfra's and OpenAI's APIs to help identify, describe, and reconcile submitted songs (title, artist, release year, and similar catalog metadata), not personal account data. Song playback uses the official YouTube API and links out to the real YouTube page or app, hittiguess never embeds a hidden player. No data is sold to anyone, and no data is shared with advertisers.

Hosting and services in production: the website runs on Vercel, the backend services on Microsoft Azure Container Apps, the databases on Neon (PostgreSQL), voice relay on Cloudflare, account and password-reset email through Resend, and sign-in with Google is optional. The backend is planned to move to a single Hetzner server; this section will be updated when that happens.

## Cookies and browser storage

hittiguess stores small pieces of state in the browser through HTTP cookies, local storage, and session storage. Every cookie and storage key used is strictly necessary to deliver the service, authenticate user sessions, protect against security vulnerabilities, or persist user interface preferences explicitly chosen by the visitor. Because hittiguess uses only strictly necessary cookies and functional storage, no tracking or advertising cookies are set, and no cookie consent banner is required under the ePrivacy Directive (Directive 2002/58/EC Article 5(3)) or the General Data Protection Regulation (GDPR). No advertising or analytics cookies are set today.

### HTTP cookies

- `access_token`: an encrypted, HTTP-only cookie containing the user JWT authentication token. Scoped to the API, it authenticates requests without exposing credentials to client-side scripts. Valid for 15 minutes.
- `refresh_token`: an encrypted, HTTP-only cookie scoped to `/api/auth/refresh`. It allows the browser to securely obtain a new access token without re-entering credentials. Valid for 7 days, or 30 days when "Remember me" is selected.
- `session_hint`: a functional indicator cookie (`1`) set at root path (`/`). It enables the Next.js routing proxy to confirm an active session and route directly to authenticated screens without exposing authentication secrets to client scripts.
- `XSRF-TOKEN`: an anti-Cross-Site Request Forgery (CSRF) token issued by the core backend. The frontend reads this token to attach an `X-XSRF-TOKEN` validation header to mutating HTTP requests, protecting user accounts from unauthorized cross-site actions.
- `sidebar_state`: a layout preference cookie recording whether the navigation sidebar is collapsed or expanded (`true` or `false`).
- `oauth2_auth_request`: a short-lived, HTTP-only cookie retained for at most 180 seconds during Google OAuth sign-in to prevent state forgery during the authorization handshake.

### Local storage

Keys in local storage persist across browser restarts to remember personal hardware and visual preferences:

- `theme`: the interface display theme (`light`, `dark`, or `system`), managed by the theme provider.
- `hittiguess-microphone-device-id`: the unique device identifier of the microphone chosen for voice communication.
- `hittiguess-speaker-device-id`: the unique device identifier of the audio output device selected for gameplay and voice audio.
- `hittiguess-microphone-volume`: the microphone volume percentage level.
- `hittiguess-speaker-volume`: the speaker volume percentage level.
- `hittiguess-song-volume`: the song playback audio volume percentage level.
- `hittiguess-noise-gate-sensitivity`: the sensitivity threshold for the voice chat noise gate.
- `hittiguess-rnnoise-enabled`: whether neural network background noise suppression is active.
- `hittiguess-active-playlist-import`: the identifier of an in-progress YouTube playlist import, ensuring background progress continues smoothly across page reloads.

### Session storage

Keys in session storage exist only for the duration of the active browser tab:

- `hittiguess-round-intro-seen-`: prefixed per session id (`hittiguess-round-intro-seen-[sessionId]`), tracks whether the intro animation for the current game round has already been displayed, preventing duplicate playback during a single game session.
- `hittiguess.audioShareWarningDismissed`: prefixed per warning kind, records when an informational notice about browser audio-sharing capabilities has been dismissed for the active session.
- `bulk-import-progress-events`: caches real-time WebSocket progress notifications for playlist import operations in the current tab.
- `bulk-import-connection-state`: records the WebSocket connection status of active bulk import operations.

### Error monitoring and analytics

- **Sentry SDK**: hittiguess uses Sentry for client-side error monitoring and unhandled exception logging. The client SDK does not set any cookies, does not persist identifiers in browser storage, and does not enable session replays or user tracking. Error payloads are transmitted directly to the Sentry ingestion endpoint over HTTPS.
- **No tracking or third-party cookies**: hittiguess sets zero advertising, profiling, or third-party tracking cookies. If privacy-friendly usage analytics is introduced in the future, it will use a cookieless tool, maintaining a zero-banner, privacy-respecting configuration.

## A user's rights over their own data

- **Access and export.** A logged-in user can download a full copy of their own account, playlist, and song data from the account export endpoint (`GET /api/users/me/export`). The account settings page that exposes it in the interface is still to be built.
- **Correction.** Username and email can be changed through the account update endpoint (`PATCH /api/users/me`); the interface for it is still to be built.
- **Deletion.** A user can permanently delete their account through the account deletion endpoint (`DELETE /api/users/me`); the interface for it is still to be built. This removes the account row itself. Songs the account submitted stay in the catalog (a song is treated as belonging to the catalog rather than to the account that added it), but are no longer attributed to that account. Playlists the account was the sole member of are deleted; a shared playlist the account owned passes ownership to another member instead of being deleted.

## Data retention

Data is retained according to the following category lifetimes:

- **Active accounts and playlists.** Kept until the user deletes their account through the deletion endpoint or requests removal directly.
- **Unverified accounts.** Accounts created locally that never complete email verification are automatically deleted after 7 days by a daily retention sweeper.
- **Authentication and reset tokens.** Refresh tokens (7 days by default, 30 days for remember-me sessions), password reset tokens (1 hour, or immediately upon use), and email verification tokens (24 hours) are automatically purged upon expiration by an hourly token retention sweeper.
- **Groups and chat messages.** Groups expire after at most 24 hours of inactivity or when deleted by an administrator. Chat messages belong to the group and are automatically purged when the group expires or is deleted. Voice presence is dropped after 20 seconds of disconnection.
- **Catalog ingestion and pipeline runs.** Background pipeline runs and stage jobs are retained for 30 days after completion or failure for operational inspection, then purged by a daily retention sweeper.
- **Internal analytics events.** Stored in the analytics database and purged after 365 days by a daily retention sweeper.
- **Server logs and telemetry.** Operational metrics, error reports, and server traces sent to Sentry and Grafana Cloud are retained for 30 days.

## Children

hittiguess isn't directed at children and asks that users meet the minimum age required by their own country to use an online service like this. There's no technical age verification today.

## Changes to this policy

This policy will be updated as the app's actual data practices change, most notably once usage analytics (see the Cookies section) ships. Material changes will be reflected here with an updated date at the top.

## Contact

Questions can be raised as an issue on the project's GitHub repository, or sent to dariusturcu22@gmail.com.

## Not legal advice

hittiguess is a solo-developer hobby and portfolio project, not a company with legal counsel. This document describes the app's actual data practices as plainly as possible, but it isn't a substitute for professional legal review, and shouldn't be treated as one.
