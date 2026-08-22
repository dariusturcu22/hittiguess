# ARCHITECTURE.md: Technical Blueprint

## Stack

| Layer | Technology | Notes |
|---|---|---|
| Backend, core | Spring Boot (Java) | Auth, playlist/song CRUD, game session, WebSocket/STOMP. Owns the DB schema. |
| Backend, AI microservice | Python + FastAPI | Metadata pipeline, LLM synthesis, embeddings. Calls OpenAI directly. |
| Frontend | Next.js (TypeScript) | Dashboard, playlist/song management, game UI. Deployed on Vercel. |
| Mobile | Flutter | Deprioritized. |
| Database | PostgreSQL + pgvector | Target host: Azure Database for PostgreSQL Flexible Server. Current host to be confirmed, see [PROJECT_STATE.md](PROJECT_STATE.md). |
| Auth | OAuth2 + JWT | Refresh tokens, owned by the core service. |
| Realtime | Spring STOMP/WebSocket | Game session sync, voice signaling, and text chat, core service. |
| AI/LLM | OpenAI API | Called directly from the AI microservice, structured output through Pydantic. |
| Embeddings | text-embedding-3-small | Deduplication and RAG, generated in the AI microservice. |
| Hosting, backend | Azure Container Apps | Both services, same environment. |
| Hosting, frontend | Vercel | Unchanged. |
| Voice | WebRTC, mesh topology | Cloudflare TURN as fallback. See Voice and Text Chat below. |

## Two-service architecture

### Core service (Spring Boot)

Auth, playlist and song CRUD, the Song table (schema owner), game session and round logic, WebSocket/STOMP for real-time sync, voice signaling, and text chat. Calls the AI microservice internally when a song needs metadata processing.

### AI microservice (FastAPI)

Multi-source metadata fetch (YouTube, MusicBrainz, Wikipedia, Genius, Discogs), LLM synthesis with structured output, embedding generation and pgvector similarity search. Exposes a small internal API, for example `POST /metadata/resolve`, consumed only by the core service, not exposed publicly.

The two services run in the same Azure Container Apps environment and reach each other over internal networking. The core service owns all database migrations; the AI microservice reads and writes rows but never alters schema.

## System components

### Song and playlist database

Every song has: `youtubeId`, `artist`, `title`, `releaseYear`, `submittedYear` and `verifiedYear` as separate fields, `verificationStatus`, `confidence` (persisted), `metadataRaw` (full pipeline output, for auditability), multi-value `tags`. The exact current shape of this table needs reconciling against the real implementation, see [PROJECT_STATE.md](PROJECT_STATE.md).

### Metadata pipeline (AI microservice)

```
YouTube URL
    ↓
YouTube Data API, title, artist, channel info
    ↓
Parallel: MusicBrainz, Wikipedia, Genius, Discogs
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

### Game session (core service)

Sessions are ephemeral, similar to a Gartic Phone round. A session is created with an invite link, players join it live, and everyone who joins is a full player, there's no spectating and no joining a session already in progress. When the session ends, nothing about it persists except a downloadable results export; the session, its roster, and its chat are all gone.

```
GameSession
  ├── id, playlist(s), status, inviteLink
  ├── players[] → Player (userId, timeline[], tokenCount, isConnected)
  ├── currentRound → Round
  │     ├── activePlayerId (rotates each round)
  │     ├── djPlayerId (fixed or rotating, per session setting)
  │     ├── currentSong
  │     ├── status
  │     └── guesses[] → Guess (playerId, guessedYear, placedPosition, isCorrect)
  └── history[]
```

Sync through WebSocket/STOMP. REST for session creation and join; WebSocket for real-time state changes.

### DJ playback

The DJ is never shown an embedded YouTube player.

- Remote sessions: the DJ opens the real YouTube page in a new browser tab. That tab is captured through WebRTC tab audio capture and streamed to the other players.
- In-person sessions: the DJ opens the real YouTube app through a deep link (Android intent, iOS universal link, falling back to a plain browser link if the app isn't installed) and plays through the device speaker.
- Physical cards: the QR code encodes the YouTube video ID directly. Scanning opens the real YouTube app or site.
- Playback control is entirely manual, on the DJ's device. There's no remote play or pause.
- Round reveal is a manual trigger over WebSocket, since there's no programmatic access to a page we don't control.
- Ads play unmodified in every mode.

### Voice and text chat

Both are scoped to the session's lifetime: available from the moment someone joins through the invite link until the session ends, then gone along with the rest of the session.

Voice: mesh peer-to-peer, no media server. Signaling rides the existing WebSocket layer. Capped at 8 participants per session. Cloudflare TURN, pay-as-you-go, used only when a direct connection between two peers fails, most connections never touch it. Video is out of scope; mesh video's bandwidth and CPU cost breaks down at realistic group sizes, and a media server was ruled out on cost and operational grounds.

Text: plain messages over the same WebSocket connection, stored only for the life of the session, not persisted after it ends.

### Verification

Players can report a song's year as incorrect, with a message, the year they believe is correct, and one or more sources. What promotes a reported or new song to fully verified is undecided. Admin-submitted songs are trusted immediately.

### RAG and deduplication (AI microservice)

Before running the full pipeline for a new submission: normalize `artist + title`, generate an embedding, check pgvector similarity against existing verified songs. On a high-confidence match, reuse the existing data and skip the LLM call. Goals: keep the database free of duplicate rows, and avoid unnecessary LLM cost. Exact matching thresholds, and how this interacts with the playlist/song relational model, are still being worked out.

### Admin tools

Bulk import mechanism: to be designed. Admin-submitted songs skip the pipeline and are trusted immediately. Review queue for reports: to be designed.

## Deployment

- Core service and AI microservice: containerized, deployed to Azure Container Apps, same environment.
- Database: Azure Database for PostgreSQL Flexible Server, pgvector enabled.
- Frontend: Next.js on Vercel, unchanged.
- Azure Container Apps scales to zero on the Consumption plan; AWS Fargate and App Runner don't. At the expected usage pattern, bursty and mostly idle, this is the better cost fit.
- Migrating away from Fly.io. See [PROJECT_STATE.md](PROJECT_STATE.md) for current status.

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
Host creates a session, selects playlist(s), shares an invite link
Players join live, before the session starts
Session starts, DJ and active player assigned for round 1
DJ opens the real YouTube page (remote) or app (in-person)
Other players hear the stream (remote) or the room (in-person), see game UI only
Active player guesses; other players may bet after the guess locks
Any player triggers reveal manually
Backend scores the round, updates tokens
Next round: active player rotates, DJ follows the session's fixed or rotating setting
Game ends when a player completes their timeline
Results become downloadable; the session and its chat are gone
```

## What's built

- Two-service split: Spring Boot core service (`backend/`) and Python/FastAPI AI microservice (`ai/`).
- Multi-source metadata pipeline in the AI microservice, LLM synthesis with structured output through Pydantic; only the YouTube source is live, MusicBrainz, Wikipedia, and Genius are paused pending an API compliance and cost review.
- Spring Boot backend: auth, playlist CRUD, song CRUD.
- Next.js frontend with AI-assisted song submission, deployed on Vercel.
- PDF/QR card generation.
- OAuth2 + JWT auth.

## Not yet built

Azure deployment, database migration, game session model, WebSocket layer, DJ link-out playback flow, voice and text chat, song search by link or keyword, community reporting flow, pgvector deduplication, playlist/song relational fix, Discogs integration, confidence-gating UI, admin bulk import, admin review queue, scheduled re-verification, rate limiting, UI redesign, auto-generated featured playlists, test coverage.
