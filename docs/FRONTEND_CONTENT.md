# FRONTEND_CONTENT.md: Frontend Content Specifications

What data, state, and actions each frontend view needs, independent of visual design. Story 28's mockups design how these look; this file specifies what they have to show and do regardless of that design. Existing pages are grounded in the real API contracts in [SYSTEM_REFERENCE.md](SYSTEM_REFERENCE.md); planned gameplay screens are grounded in [GAME_DESIGN.md](GAME_DESIGN.md) and [ARCHITECTURE.md](ARCHITECTURE.md), since none of that code exists yet.

## Existing pages

### Landing (`/`)

Public, unauthenticated. Content: product name and one-line explanation, a call to action into login/register. No live data.

### Login, Register, Forgot password (`(auth)`)

Public, unauthenticated. Login: username/email and password fields, an OAuth2 login option, a link to register and to forgot-password. Register: username, email, password fields. Forgot password: currently a non-functional placeholder that says so honestly rather than silently doing nothing (`ARCHIVE.md`'s pre-split-polish batch 5); a real flow is a future feature, not specified here until it exists.

### OAuth2 redirect (`/oauth2/redirect`)

No visible content of its own, a transient handoff screen while the OAuth2 flow completes and redirects into the dashboard.

### Playlist list (`/playlists`, dashboard)

Authenticated. Data: the current user's playlists, each as a `PlaylistSummaryDTO` (id, name, color, song count). States: empty (no playlists yet, distinct call to action), loaded. Actions: create a playlist, open a playlist, join a playlist by invite code or link.

### Playlist detail (`/playlists/[playlistId]`)

Authenticated, playlist-access-gated. Data: a `PlaylistDetailDTO`, name, color, invite code, song count, the full song list (each a `SongDTO`: artist, title, release year, YouTube ID, tag, country, who added it), and the member list (`UserSummaryDTO`s). States: empty (no songs yet), loaded, search-with-no-results (distinct message from the empty state, `ARCHIVE.md`'s QA-pass fixes). Actions: rename the playlist, change its color, add a song, open a song, remove a song, copy the invite link/code, search/filter songs by title (client-side today).

### Song detail (`/playlists/[playlistId]/songs/[songId]`)

Authenticated, playlist-access-gated. Data: a single `SongDTO`'s full fields, plus who added it. Actions: edit the song's fields, delete it from the playlist. Once story 17 ships, this view also needs a report affordance (available on every card regardless of `verificationStatus`) and, on `NEEDS_REVIEW`/`MANUAL_ENTRY` cards only, a thumbs-up confirmation affordance; not built yet, noted here so the eventual content addition isn't a surprise.

### Add song (`/playlists/[playlistId]/songs/add`)

Authenticated, playlist-access-gated. Fields: YouTube link or ID, with a "get details" action that calls the metadata pipeline and pre-fills artist/title/release year/gradient colors/tag/country for the submitter to review and adjust before saving. States: idle, fetching, fetched-and-editable, error (a failed fetch surfaces a real error, not a silent no-op, `ARCHIVE.md`'s story-6 frontend fix).

### Join by invite (`/playlists/join/[inviteCode]`)

Authenticated. No content beyond a brief in-progress state; on success, redirects into the newly-joined playlist. On failure, shows a real error rather than getting stuck (`ARCHIVE.md`'s QA-pass fix for the stuck-forever bug this route used to have).

## Planned gameplay screens (not yet built)

None of the following exist as frontend code today; content is derived from `GAME_DESIGN.md` and `ARCHITECTURE.md`, not from an implementation.

### Group lobby

Data: the group's member list (per-group display name and avatar, not the account profile), the admin marked distinctly (crown icon per `GAME_DESIGN.md`), the invite link and 4-letter join code, current settings (playlist(s) or generation mode, DJ mode, win-condition card count), read-only for non-admins. Actions (admin only): edit settings, promote another member to admin, start the game session. Actions (everyone): copy the invite link/code, leave the group, open text chat and voice (see their own sections below, available from this screen since both are group-scoped, not session-scoped).

### Game session / timeline (per player)

Data: the active player's own timeline (ordered cards, each showing artist/title/year once revealed), whose turn it is, the DJ, each player's token count, the current round's phase (playing, guess-lock-in, countdown, betting, reveal, scored). Actions: drag-and-drop a card to place a guess (before/after/between existing cards), lock in, submit an artist/title guess in a box available for the whole turn (every player except the DJ, story 10), place a bet during the betting window if holding a token, skip-betting. Feedback content: a lock-in sound cue, a token-earned animation on a correct guess, a distinct animation for an incorrect one (`GAME_DESIGN.md`'s Interaction and animation section covers the visual side, not the content).

### DJ view

Data: the current song's real YouTube page or app link-out, not an embedded player. Actions, DJ only: "Open YouTube Link" (paired with the audio-sharing UI warning, story 9), pause, play, close the tab/app, end the current turn, reveal (only enabled once the betting window has closed). Non-DJ players never see this screen's playback controls at all, only the shared game UI.

### Voice sidebar

Data: each connected group member as an avatar with name, a speaking indicator, mute/deafen state. Actions: join or leave the voice room at any time, mute/deafen self. Persists across the "playing while away" minimized state (`GAME_DESIGN.md`).

### Text chat overlay

Data: message history for the group (sender's per-group display name, message body, timestamp), loaded on join or reconnect. Actions: send a message (500-character limit, rate-limited to 5 per 10 seconds, story 13), toggle the overlay open/closed.

### Turn notification

Content: a sound cue plus a clickable visual banner, shown only when it's the player's turn and the game screen isn't focused. Action: clicking the banner (or the sound's implicit prompt) returns the player to the game screen.

### Playing-while-away widget

Data: a minimized summary of session state (whose turn it is, own token count) while the player uses another part of the app. Actions: click to return to the full game screen. The voice sidebar and turn notification both stay available in this state.

### Results / leaderboard

Data, on a normal session end: the main card-count ranking (winner and placement order), plus the two separate session-long tallies, "Most Artists Guessed" and "Most Titles Guessed" (story 10). Actions: download the results export, return to the group lobby.

## Content this file deliberately excludes

Colors, typography, spacing, component styling, and layout are story 28's scope, not this one's. Where a screen's exact copy (button labels, error message text, empty-state wording) isn't already fixed by a decision in `DECISIONS.md` or `GAME_DESIGN.md`, it's left to be written during story 28's design and implementation passes rather than guessed at here.
