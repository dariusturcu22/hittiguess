# Privacy Policy

Last updated: 2026-09-12

This policy describes what hittiguess collects, why, and what a user can do about it. It covers the app as it exists today, not features that haven't shipped yet.

## What gets collected

**Account data.** When an account is created, hittiguess stores a username, an email address, and, for accounts created through an OAuth provider (such as Google), the provider's own account identifier. A locally-created account also stores a password, stored as a hash, never in plain text.

**Playlist and song data.** hittiguess stores the playlists a user creates or joins, their membership details in each (role, permissions, a per-playlist display name and avatar), and the songs they submit to the catalog (title, artist, release year, a YouTube video ID, and related metadata).

**Nothing else, today.** hittiguess does not run any analytics or usage tracking yet, and does not use any third-party advertising or tracking service. If usage analytics is added later, this policy will be updated first, and a first-party analytics system is the only kind planned, no third-party trackers.

## Why this data is collected

Account data identifies who's playing and lets an account be recovered or managed. Playlist and song data is the actual content of the game: what's in a playlist, who added a song, and who's a member of what.

## Who else sees this data

hittiguess's core service and its AI microservice both operate on this data directly. The AI microservice calls OpenAI's API to help identify and describe submitted songs (title, artist, release year, and similar catalog metadata), not personal account data. Song playback uses the official YouTube API and links out to the real YouTube page or app, hittiguess never embeds a hidden player. No data is sold to anyone, and no data is shared with advertisers.

The database itself is currently hosted on Supabase (PostgreSQL). The migration target, if any, is undecided; if that changes, this section will be updated.

## Cookies

hittiguess sets cookies to keep a session logged in (an access token and a refresh token). These are functional, not tracking, cookies. No advertising or analytics cookies are set today. A separate cookie-consent notice will be added once first-party usage analytics ships, since that's the point at which a non-essential cookie would first exist to consent to.

## A user's rights over their own data

- **Access and export.** A logged-in user can download a full copy of their own account, playlist, and song data at any time through the account export feature.
- **Correction.** Username and email can be changed from the account settings.
- **Deletion.** A user can permanently delete their account. This removes the account row itself. Songs the account submitted stay in the catalog (a song is treated as belonging to the catalog rather than to the account that added it), but are no longer attributed to that account. Playlists the account was the sole member of are deleted; a shared playlist the account owned passes ownership to another member instead of being deleted.

## Data retention

There's no automatic retention or deletion schedule beyond what's described above. Data is kept until a user deletes their account or requests removal directly.

## Children

hittiguess isn't directed at children and asks that users meet the minimum age required by their own country to use an online service like this. There's no technical age verification today.

## Changes to this policy

This policy will be updated as the app's actual data practices change, most notably once usage analytics (see the Cookies section) ships. Material changes will be reflected here with an updated date at the top.

## Contact

Questions can be raised as an issue on the project's GitHub repository, or sent to dariusturcu22@gmail.com.

## Not legal advice

hittiguess is a solo-developer hobby and portfolio project, not a company with legal counsel. This document describes the app's actual data practices as plainly as possible, but it isn't a substitute for professional legal review, and shouldn't be treated as one.
