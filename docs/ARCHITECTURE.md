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
| Hosting, backend | Azure Container Apps | Core and AI production apps are provisioned. Remaining cost, capacity, media, and deployment checks are in [TASKS.md](TASKS.md). |
| Hosting, frontend | Vercel | Unchanged. |
| Voice | WebRTC, mesh topology | Cloudflare TURN as fallback. See Voice and Text Chat below. |

## Two-service architecture

### Core service (Spring Boot)

Auth, playlist and song CRUD, the Song table (schema owner), game session and round logic, WebSocket/STOMP for real-time sync, voice signaling, and text chat. Calls the AI microservice internally when a song needs metadata processing.

### AI microservice (FastAPI)

Multi-source metadata fetch (YouTube, MusicBrainz, Discogs, Wikidata, Wikipedia), LLM synthesis with structured output, embedding generation and pgvector similarity search. Exposes a small internal API, for example `POST /metadata/resolve`, consumed only by the core service, not exposed publicly.

The two services run in the same hosting environment and reach each other over internal networking, on Azure Container Apps, see the Deployment section. The core service owns all database migrations; the AI microservice reads and writes rows but never alters schema.

## System components

### Database domain boundary

Core Postgres+pgvector stores data directly used by the product: accounts, groups, temporary gameplay, playlists, songs, embeddings, participant history, and prepared difficulty. The separate analytics project stores internal usage and research observations plus anonymous song aggregates. Core owns both migration histories. A durable core queue bridges research delivery; analytics availability is not required during gameplay. The AI service shares core data and never alters schema.

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
    no  → fetch and extract Wikipedia; the earliest year two sources report locks VERIFIED
          otherwise reconcile available evidence, verificationStatus NEEDS_REVIEW
    none of the four has any data at all → the video's upload year as a low-confidence
          NEEDS_REVIEW proposal; MANUAL_ENTRY only when there is no upload year either
    ↓
Confidence and status surfaced in the UI
    ↓
Core immediately saves new VERIFIED results without playlist membership
Other previews wait for explicit submission; imports persist their outcomes
```

Quota note: YouTube's `search.list` costs 100 units per call against a 100-call default daily budget. `videos.list` costs 1 unit and batches up to 50 IDs per call. Resolving `(artist, title) → youtubeId` from a known ID avoids `search.list` entirely.

The combined precheck already runs before source agreement is evaluated. Exact agreement avoids year reconciliation, not all LLM use. Duplicate reuse can return before the precheck; no separate injection detector runs before every LLM call.

The internal `/stages` endpoints expose the same identification, source lookup, and reconciliation functions as `/metadata/resolve`. Stage calls return provider failures for core to classify and retry; resolve retains its best-effort source and LLM fallbacks. Core workers call them for queued metadata stages. YouTube identification can finish with a rejection or verified duplicate before any source jobs are needed.

Core migration V45 owns `stage_pace`, shared by AI processes for source slot reservations and cooldowns. An atomic update reserves each slot, then the caller sleeps outside the database. A stage call returns 429 with `retryAfterSeconds` when a pace wait exceeds 30 seconds, reserving no slot; resolve waits without that cap. Database failures log a warning and fall back to in-memory pacing. Response-time averages are recorded in the background, and usage recording stays at the existing provider call sites.

Metadata stages run bounded bursts on a shared executor, with concurrency enforced across core processes through the stage control row. The stage worker, HTTP client, completion service, and scheduler are unconditional. Defaults are four YouTube identification jobs, three reconciliation jobs, and two jobs for each other metadata source. Stage completion signals the next stage after commit; retries get scheduled wake-ups. One combined due-work poll runs every five seconds while active and every ten minutes while idle. Startup polls immediately. Work missed by an in-process signal, including work created by another core process, can wait up to the idle interval. Other periodic database users may still prevent serverless suspension. A burst handles at most twenty jobs before releasing its slot. The executor has one thread per metadata slot plus two spare threads; a separate two-thread scheduler handles only polling and wake-up timers. The AI service runs one Uvicorn process with an AnyIO limit of 100 worker threads so paced stage calls can overlap without blocking imports and health requests. Source pacing locks protect slot reservations, not upstream calls or sleeps. The synchronous user fast tier remains unchanged; its patient rechecks enter the user lane with their provisional year. A user recheck promotes an existing active backfill run instead of creating a duplicate.

Admin enqueue creates a batch and a backfill run per new video ID. The stage pipeline has no daily song cap. Backfill YouTube jobs wait when remaining units fall below `pipeline.youtube.reserve-units` (default 1,500); user jobs ignore that reserve. Pace deferrals reschedule without an attempt. Ready-run persistence, patient-year history, and run completion share one transaction. A save failure rolls back the stage completion and retries it. Runs finish when the song is saved; the V41 trigger queue remains the embedding executor, with no new engine embedding job. Catalog status and pipeline fields use run data, and the dashboard includes the V41 embedding depth. The sequential drain, its daily quota, and its status and manual-drain endpoints are removed. The unused `PIPELINE_STAGES_ENABLED` environment variable is ignored and can be removed by the owner after merge. Historical `pending_imports` rows and patient years remain readable; no table or column is dropped. When a pipeline rerun completes on an existing verified song, if the resolved release year differs from the stored year, it is recorded as a proposal (`proposed_release_year` and `proposed_at`) while leaving the stored year and verification status unchanged. If the rerun resolves the same year as stored, any active proposal is cleared. Non-verified songs continue to be overwritten on completion. An admin recheck queues low-priority backfill runs for specified songs or the entire catalog, skipping songs currently used by active games. Admins review proposals in the catalog drawer to accept them by updating the release year or dismiss them, or select several rows under the Proposed year filter and accept or dismiss them in one request (`POST /api/admin/songs/proposals/accept` and `/dismiss`), which skips songs without a proposal and, for accept, songs in an active game.

A successful single-song preview is remembered in memory for ten minutes, keyed by authenticated user and submitted YouTube ID. Confirmation can reuse that result when the submitted title, main artists, and year still match. Expired or missing previews require another lookup. This cache does not delay verified catalog insertion, share results between users, or implement the dropped global pipeline cache.

### Data retention and background sweepers

Data retention is enforced by periodic Spring `@Scheduled` background sweepers:

- `AccountRetentionSweeper`: Runs daily, deleting unverified user accounts (`email_verified = false`) that are older than `retention.unverified-accounts-days` (default 7 days, minimum 1; a smaller value fails startup). Cascades to associated verification, password reset, and refresh tokens.
- `TokenRetentionSweeper`: Runs hourly, purging expired refresh tokens (`expires_at < now()`), expired or used password-reset tokens (`expires_at < now() OR used = true`), and expired email verification tokens (`expires_at < now()`).
- `PipelineRetentionSweeper`: Runs daily, purging done, failed, or cancelled pipeline runs older than `retention.pipeline-jobs-days` (default 30 days) and cascading to their stage jobs, as well as finished stage jobs of runs that are themselves terminal, and done or failed playlist import jobs with their items. Both retention values must be at least 1 day; the service refuses to start otherwise.
- `AnalyticsRetentionSweeper`: Runs daily, purging analytics events and song play observations older than `analytics.retention.days` (default 365 days).
- `GroupExpirySweeper`: Runs every 60 seconds, purging groups that reached their `expires_at` deadline. Foreign key cascades automatically remove all group chat messages and memberships. Runs every 20 seconds to drop stale voice presence.

### Account deletion and personal data export

Account deletion via `DELETE /api/users/me` removes all personal data across core and history databases. Foreign key cascades (`V56__cascade_user_deletions.sql`) remove two-factor backup codes, sent chat messages, saved playlist bookmarks, group memberships, and game player links, while nullifying `added_by` on submitted songs and `reporter_id` on song reports. If the user belongs to an active group, their departure is processed; if they are the sole remaining member of an open group, the group is removed. Playlist bans cascade from both the user and the playlist (`V57__cascade_playlist_ban_deletions.sql`). A playlist whose last member leaves or deletes their account goes through `PlaylistRemovalService`, the same cleanup as an owner deleting it (group links, saved bookmarks, bans). Deletion is rejected with a conflict while the user is a player in an active game session, because the session's rounds, bets, and guesses reference the player row until the session ends. The service publishes `AccountHistoryDeleted` to synchronously remove participant history rows in the history database and every analytics event that names the account.

Personal data export via `GET /api/users/me/export` compiles every category promised in the privacy policy: account fields, creation timestamp, custom avatar image bytes, two-factor authentication status, owned and joined playlists with member permissions, submitted songs, active group membership, sent chat messages, the account's own song reports and confirmations, saved playlists, daily import quota usage, playlist import jobs it started, completed game history summaries, and song research observations.

### YouTube API Services compliance checklist

hittiguess integrates with YouTube API Services under Google Developer Policies and YouTube Terms of Service:

1. **Privacy Policy notification**: The privacy policy states that hittiguess uses YouTube API Services and links to the Google Privacy Policy (https://www.google.com/policies/privacy). Satisfied by: `docs/legal/privacy-policy.md`.
2. **Terms of Service binding**: The terms of service state that by using hittiguess, users agree to be bound by the YouTube Terms of Service (https://www.youtube.com/t/terms). Satisfied by: `docs/legal/terms-of-service.md`.
3. **Player and playback rules**: No embedded or hidden iframe player is used. Audio playback occurs exclusively on the official YouTube website or in the official YouTube mobile app via explicit link-out. Satisfied by: `frontend/lib/youtube-link-out.ts`, `frontend/app/(app)/sessions/[sessionId]/page.tsx`, `backend/src/main/java/org/dariusturcu/backend/controller/GameSessionController.java`.
4. **Data storage and refresh limits**:
   - **Song durations**: Stored durations follow a 29-day refresh cycle. If an updated duration is not retrieved within 29 days, the expired value is wiped. Satisfied by: `backend/src/main/java/org/dariusturcu/backend/service/SongDurationRefreshService.java` (`RETENTION = Duration.ofDays(29)`), `backend/src/main/java/org/dariusturcu/backend/repository/SongRepository.java`.
   - **Video thumbnails**: Thumbnails are never stored or cached in the database. Client components construct URLs directly against YouTube's public image CDN (`https://i.ytimg.com/vi/{id}/hqdefault.jpg`). Satisfied by: `frontend/components/playlist-cover-mosaic.tsx`.
   - **Video titles and channel names**: Raw video titles and channel names captured during playlist imports are held temporarily in `playlist_import_job_items` and purged after 30 days. Permanent catalog song entities store clean metadata synthesized from multiple independent music sources, not raw YouTube API snippet records. Satisfied by: `backend/src/main/java/org/dariusturcu/backend/pipeline/PipelineRetentionService.java`, `backend/src/main/java/org/dariusturcu/backend/pipeline/PipelineJobRepository.java`.
5. **Branding and attribution**: Displays of YouTube content provide direct link-outs with clear text attribution ("Open on YouTube to play", "Open YouTube"). Satisfied by: `frontend/app/(app)/sessions/[sessionId]/page.tsx`, `frontend/components/audio-check/audio-check-parts.tsx`.
6. **Quota headroom and daily monitoring**: The application queries only single-unit endpoints (`videos.list` for metadata and durations, `playlistItems.list` for playlist expansion). High-cost search operations are not used. The standard 10,000 unit daily quota accommodates 10,000 video imports or up to 500,000 batched duration checks daily. Quota usage is tracked in Pacific time in `api_usage_daily`. Satisfied by: `ai/app/usage/recorder.py`, `ai/app/metadata/sources/youtube.py`, `backend/src/main/java/org/dariusturcu/backend/controller/AdminUsageController.java`.

### Group (core service)

A group is the persistent wrapper a game session lives inside. Anyone can create one, whoever does becomes its admin. Membership is invite-link based, capped at 8 members, and a user can belong to at most one group at a time.

The admin controls the game settings (playlist(s), DJ mode, win-condition card count); every member sees those settings change in real time, non-admins see them read-only. Chat and voice are live from the moment the group is created. Only the admin can start a game session. Start game opens an audio check first, a group status between the lobby and the game in which members can still join and the required DJs share and test their audio; once the game starts, the group locks, no new members can join.

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

A game session is the round-by-round gameplay itself, created only when the group's admin starts one. Temporary gameplay rows are purged when the session ends. Compact participant-only history is committed first; the latest normal result per group also remains available for export. An active-game admission limit (game.session.max-active-games, default 50) caps the number of sessions running concurrently in the single-replica container; starting a session beyond this ceiling returns HTTP 503 Service Unavailable with a clear capacity notification.

```
GameSession
  ├── id, groupId, status, difficultyTier, currentRoundNumber, currentGameRoundNumber
  ├── players[] → Player (userId, timeline[], tokenCount, isConnected)
  ├── currentRound → Round
  │     ├── activePlayerId (rotates each turn)
  │     ├── djPlayerId (fixed or rotating, per group setting)
  │     ├── currentSong
  │     ├── status
  │     ├── placedPosition, placementCorrect, timelineCardCount, validInsertionSlotCount
  │     ├── phase deadlines, guesses[], bets[], betting skip voters
  │     └── guesses[] → Guess (playerId, guessedTitle, guessedArtist, titleCorrect, artistCorrect)
  └── songQueue[]
```

If every player disconnects and none reconnect within 10 minutes, the session is torn down as abandoned and produces no results export. A single player's disconnect never ends the session while anyone else is still connected.

Sync through WebSocket/STOMP for both the group and the game session. REST for group and session creation and join; WebSocket for real-time state changes.

### DJ playback

The DJ is never shown an embedded YouTube player.

- Remote sessions: every DJ shares the entire screen with system audio once, on the audio check before the game, where an analyser on the shared track confirms it carries sound; on their turn they open the real YouTube page in a new browser tab. The captured audio travels on its own session-scoped peer connections, one per listener, separate from the voice mesh, so a player hears the song without joining the voice call. The capture lives in a page-independent module for the whole match, and its track is swapped into the song senders with `replaceTrack` only during that player's DJ turn until the reveal and swapped out the rest of the time, so the connections stay up and a change of DJ never renegotiates. A share that stops is reported to the server, which pauses the game until a new share passes the test. The capture is requested with `restrictOwnAudio`, which keeps the game tab's own output (the other players' voices) out of the captured audio. Each player's card bars follow the song stream, read by an analyser that never sees the microphone. Only Chromium on Windows, ChromeOS, and macOS from Chrome 141 is offered the share (`canShareSongAudio` in `frontend/lib/youtube-link-out.ts`, table in [GAME_DESIGN.md](GAME_DESIGN.md)); each browser reports the result when it joins a group (`POST /api/groups/{groupId}/audio-capability`), and the DJ rotation skips a player who cannot share whenever another player can. A dismissible `AudioShareWarning` banner tells a player whose browser cannot share, and tells listeners when the DJ cannot.
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

### Audio quality

The song travels on its own WebRTC connections rather than the voice mesh, so voice compression never touches the music and a player who never joins the call still hears it. One connection per listener means the DJ's upload is the song bitrate paid once per listener, which is why the bitrate is capped and named in a single constant: stereo Opus at 128 kbps with in-band FEC, marked as music, and the capture opened with echo cancellation, noise suppression, and auto gain off. Raising the cap to 192 kbps adds 64 kbps per listener per direction.

Voice stays on the group's mesh: the microphone opens with echo cancellation, noise suppression, and auto gain on, marked as speech, and capped at 40 kbps. Voice passes through an adjustable noise gate and an optional RNNoise filter, both of which run as AudioWorklets and fall back to the plain microphone path when their worklet or WebAssembly does not load.

### Verification

Players can report a song's year as incorrect, with a message, the year they believe is correct, and one or more sources. Exact agreement among MusicBrainz, Discogs, and Wikidata locks the year without Wikipedia extraction or year reconciliation. Otherwise Wikipedia extraction runs, and the earliest year that at least two sources report locks VERIFIED. The voting sources are MusicBrainz, Discogs, Wikidata's track date, Wikidata's album date (only when it equals MusicBrainz's year), and the Wikipedia extraction. A later year never outvotes an earlier release, and a lone earlier year does not count. Remaining evidence is reconciled to `NEEDS_REVIEW`. When no source has an answer, the YouTube video's upload year is stored as a low-confidence `NEEDS_REVIEW` proposal, and `MANUAL_ENTRY` is returned only when the video has no upload year either. Promo and regional pre-releases do not count as the release year. High LLM confidence alone never promotes a song to VERIFIED. Admin imports follow the same pipeline and verification rules as user submissions.

### RAG and deduplication (AI microservice)

Before the precheck for a new submission, the AI service normalizes the cleaned artist/title, generates a `text-embedding-3-small` embedding, and searches existing verified songs by pgvector cosine distance. A high-confidence match carries `canonical_song_id` alongside the reused metadata. Core links the new upload in `AlternateYoutubeId` and returns the existing Song. Known primary or alternate IDs resolve to the existing song without creating a row. Transaction locks serialize inserts, links, and playlist confirmations for the same submitted ID. Duplicate reuse never replaces the existing song's metadata with another upload's duration. The check excludes the song that already owns the video being checked, as its main or an alternate ID, so a rerun of a catalog video is not matched against itself.

Similarity matching requires a stored embedding. Core migration V41 queues verified inserts, status upgrades, and artist/title edits, and backfills verified songs with missing embeddings. A separate core scheduler checks for due work every ten seconds and calls the authenticated AI indexing endpoint only when needed, continuing for at most twenty successful indexing calls per burst. The AI service leases one job, embeds the normalized main artists and title, and stores the result only if its metadata generation and lease attempt still match. Failures retry with capped exponential backoff; expired leases recover interrupted workers. Metadata changes invalidate old embeddings, and indexing never changes playlist membership or song metadata. Similarity reuse becomes available after indexing completes.

### Frontend API client and CSRF

The frontend's shared axios instance copies the `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` header on every request. Parallel first requests can each receive a fresh cookie, so a mutation can carry a token the server has already replaced and fail with 403. The response interceptor retries such a request once when the 403 message names the CSRF token, and the request interceptor sends the current cookie on the retry. Other 403 responses are not retried. A 401 triggers one `/auth/refresh` and a retry of the original request.

### Admin tools

Admin backfill runs through persisted stage queues, with a run-now action to raise one video above other backfill work. Admin origin grants no special verification status. User imports identify videos concurrently and resolve provisional years through the fast tier; songs appear as they settle and are queued for patient rechecks. A verified duplicate reuses the existing song without another year lookup. Stage leases prioritize user work over backfill. Review queue for reports: built, story 17, ranked by a five-tier priority order (converging reports first, then non-converging reports, then confirmed-but-unreported cards, then unconfirmed cards, `VERIFIED` cards with no report never appear).

A persisted job engine for the stage pipeline exists in core (story 51, V44): import batches, one run per YouTube ID, one job per stage, leasing with `FOR UPDATE SKIP LOCKED`, lazy lease reclaim, retry backoff, failure classification, per-stage pause, and the stage dependency rules. The engine handles all admin backfill and patient rechecks. Core V47 records embedding claim and storage timestamps for queue counts, duration averages, and throughput; embeddings still run through the V41 executor.

## Deployment

Beta runs the frontend on Vercel and both backend containers in one Azure Container Apps Consumption environment. The Spring core has external HTTPS and WebSocket ingress. The FastAPI AI service has internal-only ingress and is reached through the environment's service discovery. Both apps run exactly one always-on replica (minimum and maximum 1), and the Spring core stays capped at one replica until the STOMP broker, presence registries, and session caches move out of process.

- Database: two Neon projects in eu-central-1 are the production data host, connecting over TLS with sslmode=require. Neon suspends idle compute after five minutes and wakes it within 0.3 seconds; HikariCP's default 30-second connection timeout absorbs cold starts without failing requests. The transactional Postgres instance with pgvector 0.8.6 and HNSW indexing is shared by the core and AI services. A separate analytics Postgres instance holds only append-heavy event data, accessible solely to core; the AI service has no analytics database credentials, and the frontend client holds no database credentials.
- Frontend: Next.js on Vercel.
- Health probes: both apps run HTTP startup, liveness, and readiness probes. Core uses `/actuator/health/readiness` for startup and readiness and `/actuator/health/liveness` for liveness on port 8080; the AI service uses `/health` on port 8000. `scripts/azure/apply-health-probes.ps1` applies them and `scripts/azure/create-container-apps.ps1` calls it after creating the apps.
- Telemetry: both apps send errors to Sentry and traces and logs to Grafana Cloud through the OpenTelemetry endpoint, configured as Container Apps secrets by `scripts/azure/set-observability-secrets.ps1` from the local `.env` files. Core also holds the Cloudflare TURN key id and token. Metrics are not shipped from Azure yet because no Grafana Alloy container runs there.
- Cost guardrails: Consumption plan only, no Azure Database for PostgreSQL, virtual network, private endpoint, Container Registry, dedicated workload profile, or Azure log ingestion. A $20 monthly Azure budget sends email alerts, and the Log Analytics workspace is capped at 0.25 GB per day with 30-day retention. An automatic shutdown Action Group is not set up yet.
- Migration path: Azure hosts the backend while the free trial credit lasts, then the two backend containers and a reverse proxy move to one fixed-price Hetzner server. Vercel and both Neon projects stay unchanged. Scale-to-zero was measured at a first-request wait of about 35 seconds (about 19 s for Azure to start the container, 13 s for Spring, 3 s for probes), so both apps keep one always-on replica instead.

### Hetzner single-server target deployment

The target host is a single Hetzner Cloud virtual server (CPX31: 4 vCPU, 8 GiB RAM, 160 GiB NVMe, €13.60 monthly in Germany, below the $20 monthly ceiling). All backend components run under Docker Compose (`scripts/hetzner/docker-compose.yml`), managed by systemd and Docker CE.

1. Service topology:
   - `caddy`: reverse proxy (Caddy 2.9) listening on public ports 80 and 443 (TCP and UDP for HTTP/3). Manages automatic TLS certificates via Let's Encrypt / ZeroSSL for `api.hittiguess.com`. Proxies API and WebSocket requests (`/ws`) to Core. Blocks external access to `/actuator/prometheus` with HTTP 403. Exposes local `/caddy-health` for health monitoring.
   - `core`: Spring Boot backend container. Binds only to `internal_network` on port 8080 (no published host ports). Limits capped at 2.0 vCPU and 2048 MiB RAM. Reaches AI service at `http://ai:8000`. Connects to external Neon transactional and analytics databases over TLS.
   - `ai`: FastAPI microservice container. Binds only to `internal_network` on port 8000 (no published host ports). Limits capped at 1.0 vCPU and 1024 MiB RAM. Reaches external Neon transactional database over TLS.
   - `alloy`: Grafana Alloy metrics agent. Binds to `internal_network`, scrapes `core:8080/actuator/prometheus` and `ai:8000/metrics` (with `X-Internal-Api-Key`), and remote-writes metrics to Grafana Cloud Prometheus.
   - Network isolation: an internal bridge network (`internal_network`) connects all four containers. Only Caddy binds to host network interfaces.

2. Configuration and secrets:
   - Host secrets template lives at `scripts/hetzner/.env.hetzner.example` and is copied to `.env.hetzner` on the host with production secrets.
   - Contains database connection strings for both Neon instances, JWT secret, internal API key, OAuth2 credentials, Resend key, third-party API keys, TURN credentials, and Grafana Cloud metrics credentials.

3. Isolated rehearsal procedure:
   - Run `python scripts/hetzner/rehearse_isolation.py` to validate Docker Compose structure, Caddyfile routing rules, Alloy target configuration, and environment variable definitions.
   - Run `ai/.venv/Scripts/pytest scripts/test_hetzner_deployment.py` to verify parser logic and constraint validations.
   - Run isolated local stack rehearsal: build images and bring up the compose stack with dummy environment values, verifying container health states and confirming that `/actuator/prometheus` is inaccessible externally through Caddy.

4. DNS cutover runbook:
   - Step 1 (T-24 hours): reduce DNS TTL for `api.hittiguess.com` from default (e.g. 300 or 3600 seconds) to 60 seconds at Cloudflare / DNS provider.
   - Step 2: provision Hetzner server with Ubuntu 24.04 LTS, Docker CE, and Docker Compose plugin. Configure UFW firewall to allow only inbound SSH (port 22), HTTP (port 80), and HTTPS (port 443).
   - Step 3: transfer repository and deploy stack using `scripts/hetzner/deploy.sh` with production `.env.hetzner`.
   - Step 4: verify local health on the server via `curl -H "Host: api.hittiguess.com" http://localhost/actuator/health/readiness`.
   - Step 5: update DNS A (IPv4) and AAAA (IPv6) records for `api.hittiguess.com` to point to the Hetzner server public IP addresses. Caddy immediately provisions Let's Encrypt TLS certificates upon receiving the first external HTTPS handshake.
   - Step 6: execute `scripts/smoke-test-production.sh` against the production domain to verify health probes, HTTPS API, WebSocket STOMP handshake, session hint proxy redirect, and AI reachability.
   - Step 7 (T+24 hours): restore DNS TTL to normal values. Maintain Azure Container Apps as an idle hot standby for 48 hours before deprovisioning Azure resources.

5. Rollback runbook:
   - If cutover validation or post-deployment monitoring detects failures, revert the DNS A / AAAA / CNAME records for `api.hittiguess.com` immediately back to the Azure Container Apps ingress endpoint.
   - Because TTL was set to 60 seconds, global DNS propagation restores Azure routing within one to two minutes. Both Azure Container Apps remain active and running during the cutover window to allow instant reversion.

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
- Multi-source metadata pipeline in the AI microservice, LLM synthesis with structured output through Pydantic; YouTube, MusicBrainz, Discogs, Wikidata, and Wikipedia are all live, each returning candidate data for the LLM synthesis step to reconcile (story 25). Genius, Last.fm, and iTunes were reviewed and dropped for good, not paused. The source-agreement flow is built (story 18): after the combined precheck, exact agreement among MusicBrainz, Discogs, and Wikidata locks the year to `VERIFIED` without Wikipedia extraction or reconciliation, disagreement consults Wikipedia for a lock on the earliest year two sources report before reconciliation to `NEEDS_REVIEW`, and a total no-answer proposes the video's upload year for review, landing at `MANUAL_ENTRY` only without one. The combined precheck detects prompt injection, non-music, and compilations; rejected submissions stop before source gathering and return `REJECTED` (story 41). The pipeline's source fetches run concurrently in a thread pool (story 24). The pipeline persists verification status, confidence, artists, title, year, color, sitelinks, and duration; `metadataRaw` remains unused. Pgvector-based duplicate detection runs before the combined precheck (story 16).
- Catalog seeding queue and user-facing bulk import (story 40): admin backfill through persisted stage queues and an immediate on-the-spot fast tier whose patient rechecks use the user lane, a batch YouTube-ID lookup against the database as the cheap first step, the `AlternateYoutubeId` and `PendingImport` tables, the `ADMIN` role and `AdminAccessGuard`.
- Spring Boot backend: auth, playlist CRUD, song CRUD, song search by link or keyword (story 14); a song's release year is edit-gated by `verificationStatus`, only `UNVERIFIED` and `MANUAL_ENTRY` songs stay editable (story 23). A song belongs to any number of playlists through a join table, not a single owning playlist (story 15). Playlist membership carries a real owner/admin, independently revocable per-member grants, kick versus ban, and a per-playlist join identity (story 46).
- Group (story 39) and Game session (story 10) backends, synced in real time over WebSocket (story 11): lobby lifecycle, settings, membership, rounds, guesses, betting, scoring, and the two session-long leaderboards are all built server-side; story 28 now implements the lobby and gameplay frontend, including drag-and-drop timeline placement, animated guess feedback, persistent connection, turn notification, and the admin-crown indicator. Visual and route verification remain open.
- DJ real YouTube link-out backend (story 9): `GET /api/sessions/{sessionId}/link-out` returns the current round's video id and canonical watch URL to that round's DJ only, refused once the round is revealed. Story 28 implements the DJ view, the "Open YouTube Link" action and its audio-sharing warning, WebRTC tab-audio capture, and the guess-lock-in audio cutoff. The visual matrix and broader interaction verification remain open.
- Voice chat backend (story 12): STOMP signaling relay on `/app/groups/{groupId}/voice/signal`, voice-presence broadcast on join and leave, and a member-gated TURN-credentials endpoint returning STUN-only ICE servers until a real Cloudflare key is provisioned. Story 28 implements the WebRTC mesh and join/leave/mute UI. Cloudflare credential minting and STUN fallback are implemented and tested. Production credential configuration and real relay use remain open.
- Group-scoped text chat backend (story 13): `ChatMessage`/`chat_messages` (`V14`), member-only STOMP send and REST history, 500-character and 5-per-10-second limits, deletion cascade on group deletion. Story 28 implements the chat overlay UI; state and accessibility verification remain open.
- Community song reports and confirmations backend (story 17): `SongReport`/`SongConfirmation` entities with per-user-per-song uniqueness, submission endpoints, and an admin review queue ranked by a five-tier priority order reusing story 40's `AdminAccessGuard`. Report resolution stays manual. Story 28 implements the report button, thumbs-up, and review-surface UI; broader action and route verification remain open.
- Playlist-to-playlist song import backend (story 45): a copy endpoint linking every song from a source playlist the requester can read into a target playlist they can write to, reusing story 15's join table; duplicates are skipped. Story 28 implements the frontend picker, with tested choose-source and existing-playlist loading, empty, and error states. Full rendered verification remains open.
- Difficulty-tuned generation (story 30): Confirm saves the tier; Start selects an indexed, varied pool from prepared core scores. Core history stores compact game and participant summaries before session purge. Durable core delivery sends research observations to analytics, where retry-safe anonymous aggregates survive the 365-day raw retention window. Background calculation publishes global scores every six hours. Custom playlist and pasted-link starts, public playlist browsing, and saved playlists are implemented. Personalized modeling remains deferred.
- Rate limiting across both services' public-facing endpoints, including auth and the internal AI-microservice endpoint (story 27).
- A separate analytics/event-store database for usage and event data, isolated from the transactional Postgres+pgvector instance (stories 33, 42, 43).
- Privacy policy, terms of service, and a GDPR personal-data export endpoint (story 37); all cookies and browser storage keys are audited and strictly necessary under the ePrivacy Directive and GDPR, requiring no cookie consent banner.
- Observability: Actuator health and Prometheus endpoints on the core service, FastAPI health and Prometheus endpoints on the AI service, OpenTelemetry tracing and logging, Grafana Alloy configuration, Sentry initialization, request-id correlation, internal management port isolation (port 8081), dashboard import tooling, and uptime monitoring are implemented (story 38). Telemetry delivery verification across Sentry and OpenTelemetry is covered by automated tooling and tests. The free-tier usage check is implemented in `FreeTierUsageCheckService`.
- A dedicated `TEST` role and seeded test-account fixture, gated off in a Production environment (story 44).
- Open-source collaboration files: `CONTRIBUTING.md`, `LICENSE`, `CODE_OF_CONDUCT.md`, issue/PR templates (story 36).
- Next.js frontend with AI-assisted song submission, deployed on Vercel.
- PDF/QR card generation, paper-size-aware (A4/Letter) page layout.
- OAuth2 + JWT auth.

## Not yet built

Automatic verified-song embedding indexing is implemented and merged in PR #274. Remaining work includes two-factor frontend states, missed-event reconnect recovery, the complete desktop visual/state matrix, live TURN and DJ audio acceptance, production cost/capacity/backup checks, and first-party usage analytics. Azure Container Apps and both Neon projects are provisioned; their remaining verification is tracked in TASKS.md. Account/legal views and personalized models remain deferred. Story 30's Difficulty-Based and Custom-mode session-start endpoints and the sitelinks count are implemented. The frontend implementation for backend stories 9, 10, 11, 12, 13, 14, 17, 39, 40, 41, 45, and 46 is present through story 28's Batches A through E; its verification gates remain open.
