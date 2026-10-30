# Privacy Policy

Last updated: 2026-10-30

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

## Cookies

hittiguess sets cookies to keep a session logged in (an access token, a refresh token, a session hint cookie, and a CSRF token that protects requests). These are functional, not tracking, cookies. The site also stores display preferences such as the theme and audio device choices in the browser. No advertising or analytics cookies are set today. A separate cookie-consent notice will be added once first-party usage analytics ships, since that's the point at which a non-essential cookie would first exist to consent to.

## A user's rights over their own data

- **Access and export.** A logged-in user can download a full copy of their own account, playlist, and song data from the account export endpoint (`GET /api/users/me/export`). The account settings page that exposes it in the interface is still to be built.
- **Correction.** Username and email can be changed through the account update endpoint (`PATCH /api/users/me`); the interface for it is still to be built.
- **Deletion.** A user can permanently delete their account through the account deletion endpoint (`DELETE /api/users/me`); the interface for it is still to be built. This removes the account row itself. Songs the account submitted stay in the catalog (a song is treated as belonging to the catalog rather than to the account that added it), but are no longer attributed to that account. Playlists the account was the sole member of are deleted; a shared playlist the account owned passes ownership to another member instead of being deleted.

## Data retention

Internal analytics events are deleted after 365 days. A group, its members, and its chat messages are deleted when the group expires or its last member leaves. Other data is kept until a user deletes their account or requests removal directly; the release tasks add retention periods for the remaining categories.

## Children

hittiguess isn't directed at children and asks that users meet the minimum age required by their own country to use an online service like this. There's no technical age verification today.

## Changes to this policy

This policy will be updated as the app's actual data practices change, most notably once usage analytics (see the Cookies section) ships. Material changes will be reflected here with an updated date at the top.

## Contact

Questions can be raised as an issue on the project's GitHub repository, or sent to dariusturcu22@gmail.com.

## Not legal advice

hittiguess is a solo-developer hobby and portfolio project, not a company with legal counsel. This document describes the app's actual data practices as plainly as possible, but it isn't a substitute for professional legal review, and shouldn't be treated as one.
