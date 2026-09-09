# ARCHITECTURE.md: Technical Blueprint

## Stack

| Layer | Technology | Notes |
|---|---|---|
| Backend, core | Spring Boot (Java) | Auth, playlist/song CRUD, game session, WebSocket/STOMP. Owns the DB schema. |
| Backend, AI microservice | Python + FastAPI | Metadata pipeline, LLM synthesis, embeddings. Calls OpenAI directly. |
| Frontend | Next.js (TypeScript) | Dashboard, playlist/song management, game UI. Deployed on Vercel. |
| Mobile | Flutter | Deprioritized. |
| Database | PostgreSQL + pgvector | Current host: Supabase. Migration target undecided, see [PROJECT_STATE.md](PROJECT_STATE.md). |
| Auth | OAuth2 + JWT | Refresh tokens, owned by the core service. |
| Realtime | Spring STOMP/WebSocket | Game session sync, voice signaling, and text chat, core service. |
| AI/LLM | OpenAI API | Called directly from the AI microservice, structured output through Pydantic. |
| Embeddings | text-embedding-3-small | Deduplication and RAG, generated in the AI microservice. |
| Hosting, backend | Currently Fly.io | Migrating away; target platform undecided, see [PROJECT_STATE.md](PROJECT_STATE.md). |
| Hosting, frontend | Vercel | Unchanged. |
| Voice | WebRTC, mesh topology | Cloudflare TURN as fallback. See Voice and Text Chat below. |

## Two-service architecture

### Core service (Spring Boot)

Auth, playlist and song CRUD, the Song table (schema owner), game session and round logic, WebSocket/STOMP for real-time sync, voice signaling, and text chat. Calls the AI microservice internally when a song needs metadata processing.

### AI microservice (FastAPI)

Multi-source metadata fetch (YouTube, MusicBrainz, Discogs, Wikidata), LLM synthesis with structured output, embedding generation and pgvector similarity search. Exposes a small internal API, for example `POST /metadata/resolve`, consumed only by the core service, not exposed publicly.

The two services run in the same hosting environment and reach each other over internal networking, wherever that ends up being, see the Deployment section. The core service owns all database migrations; the AI microservice reads and writes rows but never alters schema.

## System components

### Database domain boundary

One split is explicit: the transactional Postgres+pgvector instance versus a separate append-heavy analytics/event store ([PROJECT_STATE.md](PROJECT_STATE.md) story 33). Every entity either service reads or writes today, users, groups, sessions, rounds, guesses, songs, playlists, and pgvector embeddings, lives in the transactional instance; only usage/event data (games played, session length, rate-limit-exceeded, report-submitted, and similar counters) goes in the analytics store. No entity is planned to live in both, or move between them. This is the only database split in the architecture, there's no separate database per service.

### Song and playlist database

Every song has: `youtubeId`, `title`, `releaseYear`, `verificationStatus`, `confidence` (persisted), `metadataRaw` (full pipeline output, for auditability), multi-value `tags`. Two parts of the target shape are undecided, not just unconfirmed against code: whether release year is one field or split into `submittedYear`/`verifiedYear`, and how multiple or featured artists are stored and guessed, today's schema still assumes a single `artist` string. See [PROJECT_STATE.md](PROJECT_STATE.md)'s open questions for both.

`metadataRaw`, and any other field that persists external API output, holds the curated, actually-used subset of a source's response, never the full raw payload. A single source's raw response can run to tens of KB per song; at that size the database's free-tier size cap holds a small fraction of the catalog a curated version would. Any future field storing external API output follows the same rule.

### Metadata pipeline (AI microservice)

```
YouTube URL
    ↓
YouTube Data API, title, artist, channel info
    ↓
Parallel: MusicBrainz, Discogs, Wikidata
    ↓
pgvector similarity check, if a high-confidence match exists, skip the LLM call
    ↓
LLM synthesis (Pydantic structured output), metadata response
    ↓
Confidence gating, surfaced in the UI
    ↓
Core service stores the song as unverified
```

Quota note: YouTube's `search.list` costs 100 units per call against a 100-call default daily budget. `videos.list` costs 1 unit and batches up to 50 IDs per call. Resolving `(artist, title) → youtubeId` from a known ID avoids `search.list` entirely.

### YouTube source quality

Search with `videoCategoryId=10` and look for a channel ending in `" - Topic"`, an official auto-generated upload. Suggest an upgrade when a high-confidence match is found.

### Group (core service)

A group is the persistent wrapper a game session lives inside. Anyone can create one, whoever does becomes its admin. Membership is invite-link based, capped at 8 members, and a user can belong to at most one group at a time.

The admin controls the game settings (playlist(s), DJ mode, win-condition card count); every member sees those settings change in real time, non-admins see them read-only. Chat and voice are live from the moment the group is created. Only the admin can start a game session; once started, the group locks, no new members can join.

Lifecycle runs on fixed timers, not activity tracking:

- 30 minutes from group creation to the admin starting a game session, otherwise the group is deleted.
- 30 minutes from a game session ending to the admin starting another, otherwise the group is deleted and every member removed.
- A group isn't single-use: it can run any number of game sessions across its lifetime.

A disconnect, closed tab, network drop, never ends membership, only an explicit leave does. If the admin explicitly leaves, the next-earliest-joined member is promoted to admin; if no members remain, the group is deleted. Reconnecting isn't link-based, the invite link is for joining a group for the first time. A logged-in user who's still a member of an active group is prompted on app load to return to it or leave it, checked against their account, not against the link.

```
Group
  ├── id, adminUserId, inviteLink, status, settings (playlist(s), djMode, winConditionCards)
  ├── members[] → Member (userId, isConnected)
  └── gameSessions[] → GameSession (see below)
```

### Game session (core service)

A game session is the round-by-round gameplay itself, created only when the group's admin starts one. Ephemeral: purged entirely when it ends, except for a downloadable results export.

```
GameSession
  ├── id, groupId, status
  ├── players[] → Player (userId, timeline[], tokenCount, isConnected)
  ├── currentRound → Round
  │     ├── activePlayerId (rotates each round)
  │     ├── djPlayerId (fixed or rotating, per group setting)
  │     ├── currentSong
  │     ├── status
  │     └── guesses[] → Guess (playerId, guessedYear, placedPosition, isCorrect)
  └── history[]
```

If every player disconnects and none reconnect within 10 minutes, the session is torn down as abandoned and produces no results export. A single player's disconnect never ends the session while anyone else is still connected.

Sync through WebSocket/STOMP for both the group and the game session. REST for group and session creation and join; WebSocket for real-time state changes.

### DJ playback

The DJ is never shown an embedded YouTube player.

- Remote sessions: the DJ opens the real YouTube page in a new browser tab, only from an explicit "Open YouTube Link" action paired with a UI warning that doing so starts broadcasting their tab or system audio. That tab is captured through WebRTC tab audio capture and streamed to the other players.
- In-person sessions: the DJ opens the real YouTube app through a deep link (Android intent, iOS universal link, falling back to a plain browser link if the app isn't installed) and plays through the device speaker.
- Physical cards: the QR code encodes the YouTube video ID directly. Scanning opens the real YouTube app or site.
- Playback itself is manual, on the DJ's device, there's no remote play or pause on YouTube's own player. The DJ does control the round's flow over WebSocket: pause, play, close the YouTube tab or app, end the current turn, and trigger the reveal. No general player holds any of these controls.
- The active player's audio stream cuts off immediately once they lock in their guess, regardless of what's still playing on the DJ's end.
- Round reveal happens only after the betting window closes, and only the DJ can trigger it, there's no programmatic access to a page we don't control, so this stays a manual DJ action, not an automatic one and not any player's.
- Ads play unmodified in every mode.

### Voice and text chat

Both are scoped to the group's lifetime, not the game session's: available from the moment the group is created until the group is deleted, spanning any number of game sessions played inside it.

Voice: mesh peer-to-peer, no media server, a standing room members can join or leave at any time, not a call anyone starts. Signaling rides the existing WebSocket layer. Capped at 8 participants per group. Cloudflare TURN, pay-as-you-go, used only when a direct connection between two peers fails, most connections never touch it. Video is out of scope; mesh video's bandwidth and CPU cost breaks down at realistic group sizes, and a media server was ruled out on cost and operational grounds.

Text: plain messages over the same WebSocket connection, stored for the life of the group, not persisted after it's deleted.

### Verification

Players can report a song's year as incorrect, with a message, the year they believe is correct, and one or more sources. What promotes a reported or new song to fully verified is undecided. Admin-submitted songs are trusted immediately.

### RAG and deduplication (AI microservice)

Before running the full pipeline for a new submission: normalize `artist + title`, generate an embedding, check pgvector similarity against existing verified songs. On a high-confidence match, reuse the existing data and skip the LLM call. Goals: keep the database free of duplicate rows, and avoid unnecessary LLM cost. Exact matching thresholds, and how this interacts with the playlist/song relational model, are still being worked out.

### Admin tools

Bulk import mechanism: to be designed. Admin-submitted songs skip the pipeline and are trusted immediately. Review queue for reports: to be designed.

## Deployment

Deployment platform is deliberately undecided until the app is close to feature-complete locally, see [PROJECT_STATE.md](PROJECT_STATE.md)'s open questions.

- Core service and AI microservice: containerized, deployed together, same environment. Target platform not yet chosen.
- Database: currently Supabase-hosted Postgres. Whether to migrate at all, and to what platform, is undecided; pgvector needs to be enabled wherever it ends up.
- Frontend: Next.js on Vercel, unchanged.
- Migrating away from Fly.io for backend hosting. See [PROJECT_STATE.md](PROJECT_STATE.md) for current status.

## Data flow: adding a song

```
User searches by link or by keyword (artist, title, year)
  Already in the database: return existing data
  Not in the database: core service forwards the URL to the AI microservice
AI microservice checks pgvector for a match
  Match: return existing verified data
  No match: parallel metadata fetch, then LLM synthesis
AI microservice returns structured metadata and confidence
Frontend shows a pre-filled form with a confidence indicator
User confirms or edits
Core service saves the song as unverified
Background: AI microservice checks for a Topic-channel upgrade
```

## Data flow: playing a game

```
A player creates a group and becomes its admin, shares the invite link
Members join live; chat and voice are available immediately
Admin configures settings (playlist(s), DJ mode, win-condition card count), members see changes live, read-only
Admin starts a game session within 30 minutes of group creation, or the group is deleted
DJ and active player assigned for round 1
DJ opens the real YouTube page (remote) or app (in-person)
Other players hear the stream (remote) or the room (in-person), see game UI only
Active player guesses; other players may bet after the guess locks
Any player triggers reveal manually
Backend scores the round, updates tokens
Next round: active player rotates, DJ follows the group's fixed or rotating setting
Game ends when a player completes their timeline, or the session is abandoned after 10 minutes with zero connected players
A completed session's results become downloadable; an abandoned one produces none
Group returns to its lobby state: admin starts another session within 30 minutes, or the group is deleted and every member removed
```

## What's built

- Two-service split: Spring Boot core service (`backend/`) and Python/FastAPI AI microservice (`ai/`).
- Multi-source metadata pipeline in the AI microservice, LLM synthesis with structured output through Pydantic; only the YouTube source is live, MusicBrainz, Wikipedia, and Genius are paused pending an API compliance and cost review.
- Spring Boot backend: auth, playlist CRUD, song CRUD.
- Next.js frontend with AI-assisted song submission, deployed on Vercel.
- PDF/QR card generation.
- OAuth2 + JWT auth.

## Not yet built

Hosting migration, database migration, group model, game session model, WebSocket layer, DJ link-out playback flow, voice and text chat, song search by link or keyword, community reporting flow, pgvector deduplication, playlist/song relational fix, Discogs integration, confidence-gating UI, admin bulk import, admin review queue, scheduled re-verification, rate limiting, UI redesign, auto-generated featured playlists, test coverage.
