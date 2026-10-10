# FRONTEND_CONTENT.md: Frontend Content Specifications

What data, state, and actions each frontend view needs, independent of visual design. Story 28's mockups design how these look; this file specifies what they have to show and do regardless of that design. The implemented views use the contracts in [SYSTEM_REFERENCE.md](SYSTEM_REFERENCE.md) and the rules in [GAME_DESIGN.md](GAME_DESIGN.md). TASKS.md distinguishes missing features from remaining verification. App screens have desktop scope; only the landing page has mobile design scope.

## Existing pages

### Landing (`/`)

Public, unauthenticated. Content: product name and one-line explanation, a call to action into login/register. No live data.

### Login, Register, Forgot password (`(auth)`)

Public, unauthenticated. Login: username/email and password fields, an OAuth2 login option, a link to register and to forgot-password. Register: username, email, password fields. Forgot password requests a reset email and the reset-password route accepts the token and new password. Verify-email handles registration tokens. Both flows show pending, success, and failure states. Two-factor setup and the second login step remain frontend work in TASKS.md.

### OAuth2 redirect (`/oauth2/redirect`)

A transient handoff screen shows loading while the OAuth2 flow completes and redirects into the app; a failed handoff returns to login with feedback.

### Playlist list (`/playlists`, dashboard)

Authenticated. The library combines membership playlists and saved public playlists without duplicate cards. All, Owned, Joined, and Saved filters combine with name search. Join sits beside Create playlist. All and Owned offer creation, Joined offers joining, and Saved offers Explore. Loading, error, empty-library, empty-filter, and no-search-results states are distinct. Saving a public playlist does not grant membership.

### Playlist detail (`/playlists/[playlistId]`)

Authenticated and playlist-access-gated. Playlist detail shows name, description, song count, owner, creation date, invite controls, songs, and member circles opening a popup. Editing belongs to the dedicated Edit playlist route. Add song opens a menu with individual song entry and Import playlist; import has YouTube and accessible-playlist sources. Each song shows artist names, title, release year, and official YouTube video duration. Legacy creation dates and missing or expired durations show unavailable text. Empty, loaded, importing, attention-needed, and no-search-results states remain distinct.

### Song detail (`/playlists/[playlistId]/songs/[songId]`)

Authenticated and playlist-access-gated. Song detail shows metadata, verification status, and attribution. Reports are available regardless of status; NEEDS_REVIEW and MANUAL_ENTRY also offer confirmation. UNVERIFIED and MANUAL_ENTRY allow metadata editing only when the caller has write access to every playlist containing the shared song. VERIFIED and NEEDS_REVIEW remain read-only. Removing a song from a playlist is separate from editing its shared metadata.

### Add song (`/playlists/[playlistId]/songs/add`)

Authenticated, playlist-access-gated. The primary path searches the shared catalog and queues matches for explicit addition to the playlist. The secondary path accepts a YouTube link or ID and fetches title, main/featured artist lists, year, color, confidence, and verification status. A new fully verified result is already saved to the shared catalog when details return; Add still controls playlist membership. Other preview results remain temporary until submission. Matching previews can be reused for ten minutes without another pipeline call. States distinguish idle, fetching, verified, review-needed, manual input, rejected submissions, and transport failures. Genre enrichment is dropped; country is not supplied by this metadata response.

### Join by invite (`/playlists/join/[inviteCode]`)

Authenticated. The route previews the playlist before joining, including its cover, name, song count, member preview, and a per-playlist display-name and avatar step. On success it redirects into the joined playlist. On failure it shows an error. A banned user is refused.

## Playlist discovery, import, and editing

### Explore public playlists

Authenticated. Public playlist cards show their owner and song count and open playlist detail. All, Saved, and Not saved filters combine with name search. Save adds a public playlist to the library without granting membership. Loading, error, empty-catalog, empty-filter, and no-search-results states remain distinct. The desktop grid uses four columns.

### Import playlist

Reached from Playlist detail's Add song menu. Two sources:

- **From YouTube**: paste a playlist link and start the background import. Rows begin with raw video titles/channel names, then show identifying, dating, resolved, already-known, or unresolved outcomes. Identification runs concurrently; fast-tier years are provisional and patient processing rechecks them. Songs join the playlist as each resolves. Leaving the page does not cancel the job; the sidebar progress icon and toast reopen it. Polling runs no faster than every five seconds. The existing 200-song import cap and 500-song daily quota produce explicit limit feedback. Duplicate uploads reuse the existing song.
- **From an existing playlist** (story 45): pick a playlist the player owns, is a member of, or that's published publicly, and every song copies over immediately, no fetching, since it's already a resolved catalog row. Synchronous, no background/progress state needed for this path.

### Edit playlist

Reached from Playlist detail, owner only (story 46). Data: cover image, name, title color (the same `color` field used elsewhere, not a separate cover color), description, `isPublic` (story 30), and the full member list, each member's `canRead`/`canWrite`/`canDelete` grants, and whether they're kicked/banned-eligible. Actions: change the cover (click-through on the cover itself, not a separate control), edit the name in the form, pick a title color, edit the description, toggle public, save/cancel, delete the playlist. Member-row actions: toggle each of the three grants independently, kick (membership ends, can rejoin via invite), ban (membership ends, can't rejoin). Name and color editing belong to this route.

## Gameplay and group views

These views implement the approved group, session, voice, and away-state requirements. Live media delivery and the complete visual matrix remain verification tasks.

### Group lobby

The lobby shows group-specific member identities, the admin crown, invite link, and four-letter join code. Admin Settings contains DJ mode, a member selector for fixed DJ, and cards to win. A separate playlist picker offers Easy, Medium, Hard, and Custom on one row, with icons and a divider before Custom. Confirm saves the tier without generating songs. Start selects the prepared difficulty pool and starts the session; song identities are not previewed. Custom selects multiple accessible playlists and displays their combined song count before confirmation. Settings, playlist selection, and chat are mutually exclusive and dismiss on outside click. Only the current admin can start, change settings, remove members, or transfer admin status. Make group admin requires confirmation, refreshes permissions and the crown on success, and keeps the dialog open with failure feedback. Removal is available only while the group is open, prevents the removed account from rejoining that group, and closes its sockets after commit. Group display names are 1 to 30 characters with no control characters. Avatar links are HTTPS Google profile images, at most 1024 characters. Everyone can copy the invite and leave with confirmation. Chat and voice remain group-scoped.

### Game session / timeline (per player)

Data: the active player's own timeline (ordered cards, each showing artist/title/year once revealed), whose turn it is, the DJ, each player's token count, the current round's phase (playing, guess-lock-in, countdown, betting, reveal, scored). Actions: drag-and-drop a card to place a guess (before/after/between existing cards), lock in, submit an artist/title guess in a box available during placement until lock-in (every player except the DJ, story 10), place a bet during the betting window if holding a token, skip-betting. Feedback content: a lock-in sound cue, a token-earned animation when the active player actually receives a new token, a distinct animation for an incorrect one (`GAME_DESIGN.md`'s Interaction and animation section covers the visual side, not the content).

### DJ view

Data: the current song's real YouTube page or app link-out, not an embedded player. Actions, DJ only: "Open YouTube Link" (paired with the audio-sharing UI warning, story 9). That's the DJ's only action; there's no in-app pause, play, close, end-turn, or reveal, playback happens entirely on YouTube and the round's flow (betting countdown, reveal, advancing to the next player) runs automatically from placement lock-in. Opening YouTube does not start those timers. Non-DJ players see the same shared game UI, just without that link-out.

### Voice sidebar

Data: each voice member as a per-group avatar with name, a microphone speaking ring, and synchronized mute/deafen state. The rail is 76px wide, has no collapse control, and appears on the lobby or during an active call. The join control is at the top when outside a call. Member departure closes the gap with motion that respects reduced-motion preferences. Actions: join or leave the voice room at any time, mute/deafen self. Persists across the "playing while away" minimized state (`GAME_DESIGN.md`).

### Text chat overlay

Data: message history for the group (sender's per-group display name, message body, timestamp), loaded on join. Explicit history recovery for chat events missed during reconnect remains in TASKS.md. Actions: send a message (500-character limit, rate-limited to 5 per 10 seconds, story 13), toggle the overlay open/closed.

### Turn notification

Content: a sound cue plus a clickable visual banner, shown while the player has an unlocked placement turn and uses another app page. The sound plays once per session and turn, including across reconnects, after a browser interaction enables audio. Action: clicking the banner (or the sound's implicit prompt) returns the player to the game screen.

### Playing-while-away widget

Data: a minimized summary of session state (active player, round number, own token count, and a countdown only for the current phase's server deadline) while the player uses another part of the app. Actions: click to return to the full game screen. The voice sidebar and turn notification both stay available in this state.

### Results / leaderboard

Data, on a normal session end: the main card-count ranking (winner and placement order), plus the two separate session-long tallies, "Most Artists Guessed" and "Most Titles Guessed" (story 10). Actions: download the results export, return to the group lobby.

## Admin views

The backlog and report routes exist and require the ADMIN role.

### Catalog backlog status

Data: pending count, how many processed today, remaining daily drain quota and recent provisional/patient rechecks (story 40). Actions: submit a YouTube playlist link or a raw list of video IDs to bulk-enqueue into the backlog; the batch YouTube-ID lookup runs first so already-known songs are never enqueued, this view doesn't need to show that filtering step, only its result.

### Report review queue (story 17)

Data: every reviewable card ranked by the five-tier priority order in `SYSTEM_REFERENCE.md` (converging reports first, then non-converging, then confirmed-but-unreported, then unconfirmed, `VERIFIED` cards with no report never appear), and for each one the actual signals behind its rank, report count, whether they converge and on what year, confirmation count, not a single opaque score. Actions: resolve a report by setting the correct year and `verificationStatus`, the review stays a manual admin judgment call, nothing here auto-applies a suggested year.

## Retained history

### Game history (`/history`, `/history/[summaryId]`)

The list shows the account's completed games, wins, win rate, and paginated summaries. Each row shows the group snapshot, completion time, mode, duration, and the participant's result. Interrupted games are labeled and do not count as wins. Details show final card, artist, and title ranks, attempts and correct counts, bets placed and won, departure status, and tied winners. Deleted accounts appear as Deleted player. Only original participants have access, including after group expiry. Loading, empty, unavailable, and retry states are explicit. The account menu uses the same core statistics rather than placeholders.

## Content this file deliberately excludes

Colors, typography, spacing, component styling, and layout are story 28's scope, not this one's. Where a screen's exact copy (button labels, error message text, empty-state wording) isn't already fixed by a decision in `DECISIONS.md` or `GAME_DESIGN.md`, it's left to be written during story 28's design and implementation passes rather than guessed at here.

## Deferred account and legal views

Profile/settings account controls and privacy/terms routes remain deferred. Their future copy must reflect the actual providers, stored data, retention, and export scope. History and summary statistics are already implemented and are not placeholder data.
