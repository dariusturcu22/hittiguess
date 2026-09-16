# SYSTEM_REFERENCE.md: API Contracts, Entity Model, State Diagrams

Structured reference for what exists in the code today, distinct from [ARCHITECTURE.md](ARCHITECTURE.md)'s narrative blueprint of the target shape. Where a section describes something not yet built, it says so explicitly rather than blending planned and current state together. Regenerate the "current" sections by hand when the underlying code changes; nothing here is auto-generated.

## API contracts

Every rate-limited request the core service rejects, whichever limiter caught it, returns 429 with the same body every other error response uses: `ErrorResponse` (`status`, `message`, `timestamp`), never Spring's default `ProblemDetail`. `RateLimitingFilter` applies a 60-requests-per-minute limit to every request, keyed by authenticated user where one exists and by client IP otherwise, except `/auth/login` and `/auth/register`, which always share a stricter 5-requests-per-minute bucket keyed by IP regardless of authentication state. See story 27 in `DECISIONS.md`.

### Core service (Spring Boot)

| Method | Path | Controller |
|---|---|---|
| POST | `/auth/register` | `AuthController`, rate-limited, see story 27 |
| POST | `/auth/login` | `AuthController`, rate-limited, see story 27 |
| POST | `/auth/refresh` | `AuthController` |
| POST | `/auth/logout` | `AuthController` |
| GET | `/api/enums/countries` | `EnumController` |
| GET | `/api/playlists/{playlistId}/export/info` | `ExportController`, `paperSize` query param (`A4`/`LETTER`, default `A4`) |
| GET | `/api/playlists/{playlistId}/export/qr` | `ExportController`, same `paperSize` query param |
| GET | `/api/playlists/{playlistId}` | `PlaylistController`, requires `canRead` |
| PATCH | `/api/playlists/{playlistId}` | `PlaylistController`, owner only |
| GET | `/api/playlists/{playlistId}/songs/{songId}` | `PlaylistController`, requires `canRead` |
| POST | `/api/playlists/{playlistId}/songs` | `PlaylistController`, requires `canWrite` |
| PATCH | `/api/playlists/{playlistId}/songs/{songId}` | `PlaylistController`, requires `canWrite` |
| DELETE | `/api/playlists/{playlistId}/songs/{songId}` | `PlaylistController`, requires `canDelete` |
| GET | `/api/playlists/{playlistId}/members` | `PlaylistController`, requires `canRead`, story 46 |
| PATCH | `/api/playlists/{playlistId}/members/{userId}` | `PlaylistController`, owner only, updates a member's grants, story 46 |
| DELETE | `/api/playlists/{playlistId}/members/{userId}` | `PlaylistController`, owner only, kicks a member, story 46 |
| POST | `/api/playlists/{playlistId}/members/{userId}/ban` | `PlaylistController`, owner only, bans a member, story 46 |
| POST | `/api/playlists/{playlistId}/members/{userId}/promote` | `PlaylistController`, owner only, transfers ownership, previous owner stays a member, story 46 |
| POST | `/api/playlists/{playlistId}/publish` | `PlaylistController`, owner only, sets `isPublic` true, story 30 |
| POST | `/api/playlists/{playlistId}/unpublish` | `PlaylistController`, owner only, sets `isPublic` false, story 30 |
| GET | `/api/playlists/public` | `PlaylistController`, any authenticated user, every playlist with `isPublic` true, story 30 |
| POST | `/api/playlists/{playlistId}/save` | `PlaylistController`, any authenticated user, saves a public playlist into the caller's own library without creating a membership, rejects a non-public playlist, the caller's own playlist, or an already-saved playlist, story 30 |
| DELETE | `/api/playlists/{playlistId}/save` | `PlaylistController`, any authenticated user, unsaves a previously saved playlist, story 30 |
| GET | `/api/metadata/song` | `SongMetadataController`, one-in-flight-request-per-user limit plus the general time-window rate limit, see story 27 |
| GET | `/api/users/me` | `UserController` |
| GET | `/api/users/{userId}` | `UserController` |
| PATCH | `/api/users/me` | `UserController` |
| DELETE | `/api/users/me` | `UserController`, story 37 fixed the FK violation and playlist-orphaning bugs formerly logged in `TASKS.md`'s Bug fixes section |
| GET | `/api/users/me/export` | `UserController`, `PersonalDataExportService`, GDPR personal-data export: account fields, every playlist membership, every submitted song, story 37, distinct from `ExportController`'s playlist-content PDFs |
| POST | `/api/users/me/playlists` | `UserController` |
| GET | `/api/users/me/playlists` | `UserController` |
| POST | `/api/users/me/playlists/{playlistInviteCode}` | `UserController`, optional body carries a per-playlist display name and avatar, rejects a banned user, story 46 |
| DELETE | `/api/users/me/playlists/{playlistId}` | `UserController`, the owner can leave at any time, leadership passes to the earliest-joined remaining member, story 46 |
| GET | `/api/users/me/saved-playlists` | `UserController`, the current user's saved public playlists, distinct from `/api/users/me/playlists`, story 30 |
| POST | `/api/groups` | `GroupController`, creates a group, the creator becomes its admin, story 39 |
| POST | `/api/groups/join` | `GroupController`, join by invite link, rejects a banned or already-in-a-group user, story 39 |
| GET | `/api/groups/active` | `GroupController`, the caller's current active group, story 39 |
| GET | `/api/groups/{groupId}` | `GroupController`, story 39 |
| PATCH | `/api/groups/{groupId}/settings` | `GroupController`, admin only, story 39 |
| POST | `/api/groups/{groupId}/start-session` | `GroupController`, admin only, locks the group to new members, story 39 |
| POST | `/api/groups/{groupId}/leave` | `GroupController`, an explicit leave, admin leave passes leadership to the earliest-joined member, story 39 |
| POST | `/api/groups/{groupId}/disconnect` | `GroupController`, marks the caller disconnected without ending membership, story 39 |
| POST | `/api/groups/{groupId}/reconnect` | `GroupController`, story 39 |
| POST | `/api/groups/{groupId}/members/{memberId}/promote` | `GroupController`, admin only, promotes another member to admin, story 39 |
| POST | `/api/groups/{groupId}/voice/join` | `GroupController`, sets the caller's `isInVoice` presence flag and broadcasts voice presence on the group's voice topic, story 39 and story 12 |
| POST | `/api/groups/{groupId}/voice/leave` | `GroupController`, clears the `isInVoice` presence flag and broadcasts voice presence, story 39 and story 12 |
| GET | `/api/groups/{groupId}/voice/turn-credentials` | `GroupController`, member only, the ICE server list a client feeds `RTCPeerConnection`, STUN always, Cloudflare TURN only when a key is configured, story 12 |
| GET | `/api/sessions/{sessionId}` | `GameSessionController`, story 10 |
| GET | `/api/sessions/{sessionId}/link-out` | `GameSessionController`, the current round's YouTube watch URL for the round's DJ only, refused after reveal, story 9 |
| GET | `/api/sessions/groups/{groupId}/results` | `GameSessionController`, a completed session's downloadable results export, story 10 |
| POST | `/api/admin/catalog-seeding/enqueue` | `AdminCatalogSeedingController`, admin only via `AdminAccessGuard`, body is `AdminCatalogSeedingRequest` (`playlistLink`, `youtubeIds`, both nullable), expands a submitted playlist link through the AI microservice and merges it with any submitted IDs or links before enqueueing whatever the catalog does not already have, story 40 |
| GET | `/api/admin/catalog-seeding/status` | `AdminCatalogSeedingController`, admin only, the backlog view (pending, done, failed counts), story 40 |
| POST | `/api/bulk-import` | `BulkImportController`, any authenticated user, immediate on-the-spot resolution of `BulkImportRequest` (`playlistLink`, `videoIdsOrLinks`), a submitted playlist link is expanded through the AI microservice and merged with any submitted IDs or links, never shares the admin backlog's queue, story 40 |
| POST | `/api/songs/{songId}/reports` | `SongReportController`, any authenticated user reports a song's metadata with a message, suggested correct year, and sources, one report per user per song, story 17 |
| POST | `/api/songs/{songId}/confirmations` | `SongReportController`, any authenticated user confirms a low-confidence card, one confirmation per user per song, story 17 |
| GET | `/api/admin/song-reports/queue` | `AdminSongReportController`, admin only via `AdminAccessGuard`, the review queue ranked by the five-tier priority order, story 17 |
| POST | `/api/admin/song-reports/{songId}/resolve` | `AdminSongReportController`, admin only, body is `ResolveReportRequest` (`correctedYear` nullable, `verificationStatus` required, one of `VERIFIED`/`NEEDS_REVIEW`/`MANUAL_ENTRY`); marks the song's open reports upheld and applies the chosen year and status unconditionally, overriding even a `VERIFIED` song's lock, story 17 |
| POST | `/api/admin/song-reports/{songId}/dismiss` | `AdminSongReportController`, admin only, dismisses a song's open reports and changes nothing about the song, story 17 |
| GET | `/actuator/health` | Actuator, unauthenticated at the top-level status; component detail gated by `management.endpoint.health.show-details=when-authorized`, story 38 |
| GET | `/actuator/prometheus` | Actuator, Prometheus scrape format via `micrometer-registry-prometheus`, story 38 |

### AI microservice (FastAPI)

| Method | Path | Notes |
|---|---|---|
| POST | `/metadata/resolve` | Internal only, gated by `X-Internal-Api-Key`, called by the core service's `SongMetadataService`, never exposed publicly. Independently rate-limited at 30 requests per minute per client address, evaluated before the internal-key check, since anyone holding that shared key could otherwise call it directly. See story 27 |
| POST | `/metadata/playlist-video-ids` | Internal only, same `X-Internal-Api-Key` gate and rate limit as `/metadata/resolve`, called by the core service's `PlaylistExpansionService`. Body `{playlist_url_or_id}`, crawls the playlist through YouTube Data API's `playlistItems.list`, paginating on `nextPageToken`, and returns `{video_ids}`. A link or id that doesn't parse to a valid playlist returns 400; an upstream fetch failure on the first page returns 502. Story 40 |
| GET | `/health` | Unauthenticated, story 38 |
| GET | `/metrics` | Prometheus scrape format via `prometheus-fastapi-instrumentator`, story 38 |

### Correlation id (story 38)

Both services accept and echo an `X-Request-Id` header on every request: the core service's `CorrelationIdFilter` and the AI microservice's `CorrelationIdMiddleware` reuse an incoming value or generate one, attach it to the active OpenTelemetry span as a `request.id` attribute, include it in every structured log line emitted while handling that request, and echo it back on the response. The core service's `CorrelationIdPropagatingInterceptor` forwards the current request's id onto the outgoing RestClient call to the AI microservice, so one user action stays traceable across both services' logs and correlates with a single trace.

### Bulk import progress (story 40)

The on-the-spot bulk-import path (`POST /api/bulk-import`) reports live per-song progress over a per-user STOMP destination rather than only its final response: `BulkImportService` publishes a `BulkImportProgressEvent` (`username`, `youtubeId`, `outcome`, one of `ALREADY_KNOWN`/`RESOLVED`/`UNRESOLVED`) for every submitted video id as its resolution loop reaches it, and `BulkImportProgressListener` forwards each one to `/user/queue/bulk-import-progress` through `SimpMessagingTemplate#convertAndSendToUser`, targeting the submitting user's own session rather than a shared group or session topic. `WebSocketConfig` registers `/queue` as a broker prefix alongside `/topic` so this per-user routing has somewhere to deliver to. The admin catalog-seeding backlog does not get this treatment: its own screen shows a backlog-count and quota summary, not live per-item progress, so its enqueue endpoint stays a synchronous request-response.

Story 39's group endpoints, story 10's game-session endpoints, story 40's admin catalog-seeding and bulk-import endpoints, and story 17's report, confirmation, and admin review endpoints are live and listed above. Story 10 and 11 also add STOMP client-to-server destinations for in-session actions, handled by `GameActionController`:

| Destination | Controller | Notes |
|---|---|---|
| `/app/sessions/{sessionId}/place` | `GameActionController` | Lock in a card placement, story 10 |
| `/app/sessions/{sessionId}/guess` | `GameActionController` | Submit a title/artist guess, story 10 |
| `/app/sessions/{sessionId}/bet` | `GameActionController` | Place a bet after the active player's guess locks, story 10 |
| `/app/sessions/{sessionId}/skip-betting` | `GameActionController` | Skip the betting window, story 10 |

The endpoints stories 9, 13, and 30 add (DJ link-out, group text chat, difficulty-tuned generation) do not exist on `dev` yet. See those stories in `TASKS.md` for the planned shape. Story 12's voice signaling relay is live as a STOMP mapping on `/app/groups/{groupId}/voice/signal`, which forwards one WebRTC offer, answer, or ICE candidate onto the group's `/topic/groups/{groupId}/voice` topic for member-to-member routing; the WebRTC mesh it drives is frontend, deferred to story 28. This table lists the REST surface live on `dev` today.

## Entity model

### Current (JPA entities, core service)

Current JPA entities: `User`, `Playlist`, `PlaylistMembership`, `PlaylistBan`, `SavedPlaylist`, `Song`, `SongArtist`, `RefreshToken`, plus `Group` and `Member` (story 39), `GameSession`, `Player`, `Round`, and `Guess` (story 10), `AlternateYoutubeId` and `PendingImport` (story 40), and `SongReport` and `SongConfirmation` (story 17). The core seven are detailed below; the game and group entities follow the shapes in `ARCHITECTURE.md` and their own story sections in `TASKS.md`.

```
User
  ├── id, username, email, password, imageUrl
  ├── authProvider, authProviderId
  └── role (USER, TEST, ADMIN)

Playlist
  ├── id, name, color, inviteCode (unique, immutable)
  ├── isPublic (default false, story 30; only the owner can publish/unpublish; a public playlist is
  │     readable by any authenticated user through requireRead, independent of ownership or membership)
  ├── owner: User  (@ManyToOne, set to the creator on creation, story 46; only the owner can rename,
  │     change color, delete the playlist, or manage members)
  ├── songs: List<Song>  (@ManyToMany, owning side, joins through song_playlists; removing a song here
  │     only ever unlinks it, a Song is never deleted as a side effect of playlist membership)
  └── memberships: List<PlaylistMembership>  (@OneToMany, cascade ALL, orphanRemoval, story 46 replaces
        the old plain users many-to-many)

PlaylistMembership
  ├── id, canRead, canWrite, canDelete  (independently revocable, story 46; an owner has all three
  │     implicitly and holds no override here)
  ├── displayName, avatarUrl  (per-playlist identity, defaults to the account's own at join time)
  ├── joinedAt
  ├── playlist: Playlist  (@ManyToOne, owns the FK)
  └── user: User  (@ManyToOne)

PlaylistBan
  ├── id, bannedAt
  ├── playlist: Playlist  (@ManyToOne)
  └── user: User  (@ManyToOne; existence of a row blocks that user's future join-by-invite attempts
        against this playlist, independent of PlaylistMembership, story 46)

SavedPlaylist
  ├── id, savedAt
  ├── user: User  (@ManyToOne)
  └── playlist: Playlist  (@ManyToOne; unique on (user, playlist), story 30. A bookmark into the
        user's own library, not a membership: grants no read/write/delete access and creates no
        per-playlist identity, distinct from PlaylistMembership and unrelated to PlaylistBan. Only
        a currently public playlist can be saved, and the owner cannot save their own playlist)

Song
  ├── id, title, releaseYear, youtubeId, gradientColor1, gradientColor2
  ├── artists: List<SongArtist>  (@OneToMany, ordered by displayOrder; today always one MAIN entry,
  │     the submission flow has no multi-artist entry UI yet, see story 40's featured-artist extraction)
  ├── genre  (nullable String, populated by the metadata pipeline once it runs, not user-submitted,
  │     same as confidence/metadataRaw below; replaces the old SongTag/PLAYLIST/SPECIAL/ANIME enum,
  │     which had no analog in the settled design mockups, see DECISIONS.md's 2026-09 entry)
  ├── country
  ├── verificationStatus (UNVERIFIED default, VERIFIED, NEEDS_REVIEW, MANUAL_ENTRY; see the state
  │     diagram below; story 18's lock-evaluation pipeline that moves it is built)
  ├── confidence, metadataRaw (populated by story 18's pipeline; both nullable until a song runs through it)
  ├── playlists: Set<Playlist>  (@ManyToMany, mappedBy "songs"; a song can belong to more than one
  │     playlist since story 15, and to zero, a song is a standalone catalog entity independent of
  │     any playlist, see DECISIONS.md's 2026-09 "Song deletion reversed" entry)
  └── addedBy: User       (@ManyToOne, nullable since story 37, no inverse mapping; cleared, not blocked
        or cascaded, when the submitting account is deleted)

SongArtist
  ├── id, name, role (MAIN/FEATURED), displayOrder
  └── song: Song  (@ManyToOne, owns the FK back to Song)

RefreshToken
  ├── id, token (unique, hashed)
  ├── user: User  (@OneToOne)
  └── expiresAt
```

Schema changes now go through Flyway migrations (`backend/src/main/resources/db/migration/`), not Hibernate's `ddl-auto` (moved to `validate`); `spring-boot-flyway` is a required dependency alongside the third-party `flyway-core`/`flyway-database-postgresql` libraries for Spring Boot's own autoconfiguration to actually run it. Migrations on `dev` run through V15 (`V15__add_public_playlists_and_saved_playlists`, story 30; `V14__add_chat_messages`, story 13; `V13__add_song_reports_and_confirmations`, story 17; `V12__add_alternate_youtube_ids_and_pending_imports`, story 40).

### Planned (not yet code, target shape per ARCHITECTURE.md and TASKS.md)

Listed here so the entity picture is in one place; each is still greenfield work under its own story.

- `ChatMessage` (story 13)
- `SongDifficulty` aggregate view or table (story 30): not built as an entity. Story 30's backend computes the per-song play-derived difficulty signal on the fly through `RoundRepository.aggregatePlacementStatsBySong`, a grouped aggregate query over scored rounds, so no stored table or view exists

### Analytics store (story 33)

A separate database from the transactional one above, not JPA-mapped: its own Flyway history under `db/analytics-migration`, its own `DataSource`/`JdbcTemplate` wired in `AnalyticsDataSourceConfig` (`org.dariusturcu.backend.analytics`). One table, `analytics_events`:

```
analytics_events
  ├── id (bigserial)
  ├── event_type (text, matches AnalyticsEventType's enum names)
  ├── occurred_at (timestamptz, defaults to insertion time)
  └── payload (jsonb, one typed record per AnalyticsEventType)
```

`AnalyticsEventType` values: `GAME_SESSION_STARTED`, `GAME_SESSION_ENDED`, `LOGIN`, `PLAYLIST_CREATED`, `SONG_SUBMITTED`, `RATE_LIMIT_EXCEEDED`, `REPORT_SUBMITTED`, `FAILED_LOGIN_ATTEMPT`, each with its own payload record in the same package. `AnalyticsEventRecorder.recordEvent(AnalyticsEventType, Object)` is the write API; nothing calls it yet, story 34 instruments the real event-producing call sites. `AnalyticsRetentionService.purgeExpiredEvents()`, swept daily by `AnalyticsRetentionSweeper`, deletes events older than `analytics.retention.days` (180 by default).

## State diagrams

### Song verification status (stories 18, 23)

```mermaid
stateDiagram-v2
    [*] --> UNVERIFIED: song submitted
    UNVERIFIED --> VERIFIED: MusicBrainz, Discogs, and Wikidata agree exactly (no LLM call)
    UNVERIFIED --> NEEDS_REVIEW: sources disagree, Wikipedia + four-source reconciliation runs
    UNVERIFIED --> MANUAL_ENTRY: no source, including Wikipedia, has any data
    NEEDS_REVIEW --> VERIFIED: never happens automatically, an admin's manual review is the only path
    VERIFIED --> VERIFIED: locked against every path except an admin resolving a story 17 report
    VERIFIED --> NEEDS_REVIEW: an admin resolves a report and chooses this status instead
    VERIFIED --> MANUAL_ENTRY: an admin resolves a report and chooses this status instead
```

`VERIFIED` is a lock against every automatic path and every other manual path, but not an absolute one: an admin resolving a song's open reports through `POST /api/admin/song-reports/{songId}/resolve` can set a corrected year and change the verification status even on a `VERIFIED` song, per the 2026-09 "Admin report resolution can override a locked song" `DECISIONS.md` entry. No other path, including a report simply being upheld with nothing else, overwrites a locked year. `MANUAL_ENTRY` is the least-trusted tier, distinct from `NEEDS_REVIEW`.

### Group and game session lifecycle (stories 10, 39)

```mermaid
stateDiagram-v2
    [*] --> Lobby: group created, creator becomes admin
    Lobby --> Lobby: member joins/leaves, admin changes settings
    Lobby --> Deleted: 30 minutes pass with no game session started
    Lobby --> InSession: admin starts a game session, group locks to new members
    InSession --> Lobby: session ends normally (results exported) or is abandoned (10 minutes, zero connected players, no export)
    Lobby --> Deleted: 30 minutes pass with no next session started
    Deleted --> [*]
```

A group can cycle through `Lobby` → `InSession` → `Lobby` any number of times before being deleted. `GameSession` itself is ephemeral within `InSession`: purged entirely once it ends or is abandoned, except for a downloadable results export on a normal end.

### Admin catalog backlog item (story 40)

```mermaid
stateDiagram-v2
    [*] --> pending: YouTube ID enqueued, batch lookup found it genuinely new
    pending --> processing: scheduled drain picks it up (or pauses if on-the-spot traffic is active)
    processing --> done: patient pipeline resolves it
    processing --> failed: pipeline errors out
    failed --> pending: retried on a later drain
    done --> [*]
```

A `PendingImport` row can also be created indirectly: the on-the-spot fast tier resolves a song provisionally, then re-enqueues it here at low priority so the patient pipeline re-verifies it properly afterward.
