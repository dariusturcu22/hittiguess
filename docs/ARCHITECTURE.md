# ARCHITECTURE.md: Technical Blueprint

## Stack

| Layer | Technology | Notes |
|---|---|---|
| Backend, core | Spring Boot (Java) | Auth, playlist/song CRUD, game session, WebSocket/STOMP. Owns the DB schema. |
| Backend, AI microservice | Python + FastAPI | Metadata pipeline, LLM synthesis, embeddings. Calls OpenAI directly. |
| Frontend | Next.js (TypeScript) | Dashboard, playlist/song management, game UI. Deployed on Vercel. |
| Mobile | Flutter | Deprioritized. |
| Database | PostgreSQL + pgvector | Development host: Supabase. Production host: Neon, see [DECISIONS.md](DECISIONS.md). |
| Auth | OAuth2 + JWT | Refresh tokens, owned by the core service. |
| Realtime | Spring STOMP/WebSocket | Game session sync, voice signaling, and text chat, core service. |
| AI/LLM | DeepInfra and OpenAI APIs | DeepInfra handles precheck/extraction; OpenAI handles reconciliation and embeddings. LLM output uses Pydantic schemas. |
| Embeddings | text-embedding-3-small | Deduplication and RAG, generated in the AI microservice. |
| Hosting, backend | Currently Fly.io | Migrating away; target platform undecided, see [PROJECT_STATE.md](PROJECT_STATE.md). |
| Hosting, frontend | Vercel | Unchanged. |
| Voice | WebRTC, mesh topology | Cloudflare TURN as fallback. See Voice and Text Chat below. |

## Two-service architecture

### Core service (Spring Boot)

Auth, playlist and song CRUD, the Song table (schema owner), game session and round logic, WebSocket/STOMP for real-time sync, voice signaling, and text chat. Calls the AI microservice internally when a song needs metadata processing.

### AI microservice (FastAPI)

Multi-source metadata fetch (YouTube, MusicBrainz, Discogs, Wikidata, Wikipedia), LLM synthesis with structured output, embedding generation and pgvector similarity search. Exposes a small internal API, for example `POST /metadata/resolve`, consumed only by the core service, not exposed publicly.

The two services run in the same hosting environment and reach each other over internal networking, wherever that ends up being, see the Deployment section. The core service owns all database migrations; the AI microservice reads and writes rows but never alters schema.

## System components

### Database domain boundary

One split is explicit: the transactional Postgres+pgvector instance versus a separate append-heavy analytics/event store ([PROJECT_STATE.md](PROJECT_STATE.md) story 33). Every entity either service reads or writes today, users, groups, sessions, rounds, guesses, songs, playlists, and pgvector embeddings, lives in the transactional instance; only usage/event data (games played, session length, rate-limit-exceeded, report-submitted, and similar counters) goes in the analytics store. No entity is planned to live in both, or move between them. This is the only database split in the architecture, there's no separate database per service.

### Song and playlist database

Songs retain their primary `youtubeId`, title, release year, verification status, confidence, color, nullable Wikidata sitelink count, and official upload duration/freshness. Artists are ordered `SongArtist` rows with `MAIN` or `FEATURED` roles. The pipeline extracts both lists and persistence keeps every artist, with main artists first and featured artists afterward. Naming any one credited artist correctly satisfies the artist guess. Manual text entry still maps its artist field to one MAIN row. Verified years remain protected from ordinary user editing; reports and patient processing have their own resolution paths.

Every genuinely new fully verified pipeline result enters the shared catalog immediately, including a single-song preview abandoned before Add. A preview never adds playlist membership. Unverified preview results wait for explicit submission; imports and admin processing retain their existing persistence rules. A duplicate match returns the existing song ID and links the submitted upload as an alternate YouTube ID, preserving the existing song's primary upload, duration, and metadata.

The nullable `genre` and `metadataRaw` columns remain for compatibility; neither is populated by the current pipeline. Genre enrichment and genre game modes are dropped. Raw-evidence storage remains outside this change. Any future field storing external API output must retain only curated, actually-used evidence rather than a full source payload.

### Metadata pipeline (AI microservice)

```
YouTube URL
    ↓
YouTube Data API, title, channel, description, category, duration
    ↓
Deterministic category/duration filter
    ↓ obvious non-music: REJECTED, stop
    ↓
pgvector similarity check against existing verified songs
    ↓ high-confidence match: return verified metadata and canonical song ID,
      link alternate upload in core, skip everything below
Combined structured LLM precheck: title, MAIN/FEATURED artists, color,
    injection flag, song/compilation classification
    ↓ safety evaluation rejects flagged submissions before source gathering
Query MusicBrainz, Discogs, and Wikidata
    ↓
All three agree exactly?
    yes → lock the year, skip Wikipedia and reconciliation, VERIFIED
    no  → fetch and extract Wikipedia; corroborating source years can lock VERIFIED
          otherwise reconcile available evidence, verificationStatus NEEDS_REVIEW
    none of the four has any data at all → verificationStatus MANUAL_ENTRY
    ↓
Confidence and status surfaced in the UI
    ↓
Core immediately saves new VERIFIED results without playlist membership
Other previews wait for explicit submission; imports persist their outcomes
```

Quota note: YouTube's `search.list` costs 100 units per call against a 100-call default daily budget. `videos.list` costs 1 unit and batches up to 50 IDs per call. Resolving `(artist, title) → youtubeId` from a known ID avoids `search.list` entirely.

The combined precheck already runs before source agreement is evaluated. Exact agreement avoids year reconciliation, not all LLM use. Duplicate reuse can return before the precheck; no separate injection detector runs before every LLM call.

A successful single-song preview is remembered in memory for ten minutes, keyed by authenticated user and submitted YouTube ID. Confirmation can reuse that result when the submitted title, main artists, and year still match. Expired or missing previews require another lookup. This cache does not delay verified catalog insertion, share results between users, or implement the dropped global pipeline cache.

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
- Playback itself is manual, on the DJ's device, there's no remote play or pause on YouTube's own player. The DJ holds no other in-app controls: pause, play, and closing the tab or app all happen on YouTube itself, not mirrored into the game. "Open YouTube Link" is the DJ's only in-app action.
- The active player's audio stream cuts off immediately once they lock in their guess, regardless of what's still playing on the DJ's end.
- Round reveal fires automatically once the betting window closes, off the timer that window already runs on, artist, title, and year broadcast to everyone with no DJ or player trigger; the active player role then advances automatically once scoring resolves.
- Ads play unmodified in every mode.

### Voice and text chat

Both are scoped to the group's lifetime, not the game session's: available from the moment the group is created until the group is deleted, spanning any number of game sessions played inside it.

Voice: mesh peer-to-peer, no media server, a standing room members can join or leave at any time, not a call anyone starts. Signaling rides the existing WebSocket layer. Capped at 8 participants per group. Cloudflare TURN, pay-as-you-go, used only when a direct connection between two peers fails, most connections never touch it. Video is out of scope; mesh video's bandwidth and CPU cost breaks down at realistic group sizes, and a media server was ruled out on cost and operational grounds.

Text: plain messages over the same WebSocket connection, stored for the life of the group, not persisted after it's deleted.

### Verification

Players can report a song's year as incorrect, with a message, the year they believe is correct, and one or more sources. Exact agreement among MusicBrainz, Discogs, and Wikidata locks the year without Wikipedia extraction or year reconciliation. Otherwise Wikipedia extraction can establish a corroborated source lock. Three matching years, or at least three available years spanning at most one year, lock VERIFIED; the latter chooses the earliest. Remaining evidence is reconciled to `NEEDS_REVIEW`, or returns `MANUAL_ENTRY` when no source has an answer. High LLM confidence alone never promotes a song to VERIFIED. Admin imports follow the same pipeline and verification rules as user submissions.

### RAG and deduplication (AI microservice)

Before the precheck for a new submission, the AI service normalizes the cleaned artist/title, generates a `text-embedding-3-small` embedding, and searches existing verified songs by pgvector cosine distance. A high-confidence match carries `canonical_song_id` alongside the reused metadata. Core links the new upload in `AlternateYoutubeId` and returns the existing Song. Known primary or alternate IDs resolve to the existing song without creating a row. Transaction locks serialize inserts, links, and playlist confirmations for the same submitted ID. Duplicate reuse never replaces the existing song's metadata with another upload's duration.

Similarity matching requires a stored embedding. The existing embedding writer has no production caller connecting catalog saves to indexing; automatic indexing and backfill remain explicit follow-ups in [TASKS.md](TASKS.md).

### Admin tools

The admin catalog backlog drains through the patient pipeline on its schedule or an explicit run-now action. Admin origin grants no special verification status. User imports identify videos concurrently and resolve provisional years through the fast tier; songs appear as they settle and are queued for patient rechecks. A verified duplicate reuses the existing song without another year lookup. The patient drain yields to immediate user work. Review queue for reports: built, story 17, ranked by a five-tier priority order (converging reports first, then non-converging reports, then confirmed-but-unreported cards, then unconfirmed cards, `VERIFIED` cards with no report never appear).

## Deployment

Beta runs the frontend on Vercel and both backend containers in one Azure Container Apps Consumption environment. The Spring core has external HTTPS and WebSocket ingress. The FastAPI AI service has internal-only ingress and is reached through the environment's service discovery. Both apps scale from zero, but the Spring core is capped at one replica until the STOMP broker, presence registries, and session caches move out of process.

- Database: two new Neon projects are the production data host. Neon suspends idle compute after five minutes and wakes it on the next query, so the Spring connection pool retires idle connections before Neon closes them. The transactional Postgres+pgvector project is shared by the core and AI services. A separate analytics Postgres project holds only append-heavy event data.
- Frontend: Next.js on Vercel.
- Cost guardrails: Consumption plan only, no Azure Database for PostgreSQL, virtual network, private endpoint, Container Registry, dedicated workload profile, or Azure log ingestion. Azure budget alerts trigger a shutdown workflow before the whole-deployment cost ceiling is reached.
- Migration path: sustained Azure cost above the project's ceiling moves the two backend containers and reverse proxy to one fixed-price Hetzner server. Vercel and both Neon projects stay unchanged.

## Data flow: adding a song

```
User searches by link or by keyword (artist, title, year)
  Already in the database: return existing data
  Not in the database: core service forwards the URL to the AI microservice
AI microservice filters the submission and checks pgvector
  Match: return existing verified metadata and song ID
  No match: combined precheck, source gathering, verification
Core saves new VERIFIED results or links an alternate upload immediately
Frontend shows metadata and verification status
User confirms playlist membership or submits an unverified result explicitly
Core reuses known songs and matching cached previews
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
The server reveals the song automatically once the betting window closes
Backend scores the round, updates tokens
Next round: active player rotates, DJ follows the group's fixed or rotating setting
Game ends when a player completes their timeline, or the session is abandoned after 10 minutes with zero connected players
A completed session's results become downloadable; an abandoned one produces none
Group returns to its lobby state: admin starts another session within 30 minutes, or the group is deleted and every member removed
```

## What's built

- Two-service split: Spring Boot core service (`backend/`) and Python/FastAPI AI microservice (`ai/`).
- Multi-source metadata pipeline in the AI microservice, LLM synthesis with structured output through Pydantic; YouTube, MusicBrainz, Discogs, Wikidata, and Wikipedia are all live, each returning candidate data for the LLM synthesis step to reconcile (story 25). Genius, Last.fm, and iTunes were reviewed and dropped for good, not paused. The source-agreement flow is built (story 18): after the combined precheck, exact agreement among MusicBrainz, Discogs, and Wikidata locks the year to `VERIFIED` without Wikipedia extraction or reconciliation, disagreement consults Wikipedia for a corroborated lock before reconciliation to `NEEDS_REVIEW`, and a total no-answer lands at `MANUAL_ENTRY`. A content-safety gate rejects prompt injection, non-music, and compilation submissions before the pipeline runs, returning `REJECTED` (story 41). The pipeline's source fetches run concurrently in a thread pool (story 24). The pipeline persists verification status, confidence, artists, title, year, color, sitelinks, and duration; `metadataRaw` remains unused, as has pgvector-based duplicate detection before a song re-enters the pipeline at all (story 16).
- Catalog seeding queue and user-facing bulk import (story 40): a patient admin backlog draining on a scheduled sweep and an immediate on-the-spot import path that never share a queue, a batch YouTube-ID lookup against the database as the cheap first step, the `AlternateYoutubeId` and `PendingImport` tables, the `ADMIN` role and `AdminAccessGuard`.
- Spring Boot backend: auth, playlist CRUD, song CRUD, song search by link or keyword (story 14); a song's release year is edit-gated by `verificationStatus`, only `UNVERIFIED` and `MANUAL_ENTRY` songs stay editable (story 23). A song belongs to any number of playlists through a join table, not a single owning playlist (story 15). Playlist membership carries a real owner/admin, independently revocable per-member grants, kick versus ban, and a per-playlist join identity (story 46).
- Group (story 39) and Game session (story 10) backends, synced in real time over WebSocket (story 11): lobby lifecycle, settings, membership, rounds, guesses, betting, scoring, and the two session-long leaderboards are all built server-side; story 28 now implements the lobby and gameplay frontend, including drag-and-drop timeline placement, animated guess feedback, persistent connection, turn notification, and the admin-crown indicator. Visual and route verification remain open.
- DJ real YouTube link-out backend (story 9): `GET /api/sessions/{sessionId}/link-out` returns the current round's video id and canonical watch URL to that round's DJ only, refused once the round is revealed. Story 28 implements the DJ view, the "Open YouTube Link" action and its audio-sharing warning, WebRTC tab-audio capture, and the guess-lock-in audio cutoff. The visual matrix and broader interaction verification remain open.
- Voice chat backend (story 12): STOMP signaling relay on `/app/groups/{groupId}/voice/signal`, voice-presence broadcast on join and leave, and a member-gated TURN-credentials endpoint returning STUN-only ICE servers until a real Cloudflare key is provisioned. Story 28 implements the WebRTC mesh and join/leave/mute UI. The Cloudflare mint remains deferred behind the key, and accessibility verification remains open.
- Group-scoped text chat backend (story 13): `ChatMessage`/`chat_messages` (`V14`), member-only STOMP send and REST history, 500-character and 5-per-10-second limits, deletion cascade on group deletion. Story 28 implements the chat overlay UI; state and accessibility verification remain open.
- Community song reports and confirmations backend (story 17): `SongReport`/`SongConfirmation` entities with per-user-per-song uniqueness, submission endpoints, and an admin review queue ranked by a five-tier priority order reusing story 40's `AdminAccessGuard`. Report resolution stays manual. Story 28 implements the report button, thumbs-up, and review-surface UI; broader action and route verification remain open.
- Playlist-to-playlist song import backend (story 45): a copy endpoint linking every song from a source playlist the requester can read into a target playlist they can write to, reusing story 15's join table; duplicates are skipped. Story 28 implements the frontend picker, with choose-source and existing-playlist loading, empty, and error states still open.
- Difficulty-tuned game session generation, backend slice (story 30): per-song aggregate difficulty scoring from real `Round` data, the three group-scoring strategies (easy protects the weakest player, hard averages, medium takes the median), sitelinks-based popularity weighting through a fallback seam pending the sitelinks column itself, and public playlists (`Playlist.isPublic`, owner-only publish/unpublish, a public-browse endpoint, and a `SavedPlaylist` save/unsave capability distinct from membership). The Difficulty-Based and Custom-mode session-start endpoints, persisting the sitelinks count on `Song`, and the personalized collaborative-filtering layer remain unbuilt, see below.
- Rate limiting across both services' public-facing endpoints, including auth and the internal AI-microservice endpoint (story 27).
- A separate analytics/event-store database for usage and event data, isolated from the transactional Postgres+pgvector instance (stories 33, 42, 43).
- Privacy policy, terms of service, and a GDPR personal-data export endpoint (story 37); a cookie-consent notice stays deliberately out of scope until first-party analytics (story 34) ships.
- Observability: Actuator health and Prometheus endpoints on the core service, FastAPI health and Prometheus endpoints on the AI service, OpenTelemetry tracing and logging, Grafana Alloy configuration, Sentry initialization, and request-id correlation are present (story 38). Dashboard import and production uptime monitoring remain open. The free-tier usage check is implemented in `FreeTierUsageCheckService`.
- A dedicated `TEST` role and seeded test-account fixture, gated off in a Production environment (story 44).
- Open-source collaboration files: `CONTRIBUTING.md`, `LICENSE`, `CODE_OF_CONDUCT.md`, issue/PR templates (story 36).
- Next.js frontend with AI-assisted song submission, deployed on Vercel.
- PDF/QR card generation, paper-size-aware (A4/Letter) page layout.
- OAuth2 + JWT auth.

## Not yet built

Hosting migration (story 7), database migration (story 8), story 28's remaining visual, route-smoke, representative-state, and accessibility verification plus open import states, and first-party usage analytics (story 34). Story 30's Difficulty-Based and Custom-mode session-start endpoints and the sitelinks count are implemented. The frontend implementation for backend stories 9, 10, 11, 12, 13, 14, 17, 39, 40, 41, 45, and 46 is present through story 28's Batches A through E; its verification gates remain open.
