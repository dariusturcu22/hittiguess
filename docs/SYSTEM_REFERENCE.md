# SYSTEM_REFERENCE.md: API Contracts, Entity Model, State Diagrams

Structured reference for what exists in the code today, distinct from [ARCHITECTURE.md](ARCHITECTURE.md)'s narrative blueprint of the target shape. Where a section describes something not yet built, it says so explicitly rather than blending planned and current state together. Regenerate the "current" sections by hand when the underlying code changes; nothing here is auto-generated.

## API contracts

### Core service (Spring Boot)

| Method | Path | Controller |
|---|---|---|
| POST | `/auth/register` | `AuthController` |
| POST | `/auth/login` | `AuthController` |
| POST | `/auth/refresh` | `AuthController` |
| POST | `/auth/logout` | `AuthController` |
| GET | `/api/enums/tags` | `EnumController` |
| GET | `/api/enums/countries` | `EnumController` |
| GET | `/api/playlists/{playlistId}/export/info` | `ExportController` |
| GET | `/api/playlists/{playlistId}/export/qr` | `ExportController` |
| GET | `/api/playlists/{playlistId}` | `PlaylistController` |
| PATCH | `/api/playlists/{playlistId}` | `PlaylistController` |
| GET | `/api/playlists/{playlistId}/songs/{songId}` | `PlaylistController` |
| POST | `/api/playlists/{playlistId}/songs` | `PlaylistController` |
| PATCH | `/api/playlists/{playlistId}/songs/{songId}` | `PlaylistController` |
| DELETE | `/api/playlists/{playlistId}/songs/{songId}` | `PlaylistController` |
| GET | `/api/metadata/song` | `SongMetadataController`, one-in-flight-request-per-user limit, see story 27 |
| GET | `/api/users/me` | `UserController` |
| GET | `/api/users/{userId}` | `UserController` |
| PATCH | `/api/users/me` | `UserController` |
| DELETE | `/api/users/me` | `UserController`, has the real bugs logged in `TASKS.md`'s Bug fixes section |
| POST | `/api/users/me/playlists` | `UserController` |
| GET | `/api/users/me/playlists` | `UserController` |
| POST | `/api/users/me/playlists/{playlistInviteCode}` | `UserController` |
| DELETE | `/api/users/me/playlists/{playlistId}` | `UserController` |

### AI microservice (FastAPI)

| Method | Path | Notes |
|---|---|---|
| POST | `/metadata/resolve` | Internal only, gated by `X-Internal-Api-Key`, called by the core service's `SongMetadataService`, never exposed publicly |

Every endpoint stories 9-13, 17, 30, 39-41 add (group, game session, WebSocket destinations, reports, admin backlog, bulk import) doesn't exist yet, see those stories in `TASKS.md` for the planned shape. This table only lists what's live today.

## Entity model

### Current (JPA entities, core service)

Four entities exist today: `User`, `Playlist`, `Song`, `RefreshToken`.

```
User
  ├── id, username, email, password, imageUrl
  ├── authProvider, authProviderId
  ├── role (USER only today, see stories 19/40 and 44 for ADMIN and TEST)
  └── playlists: Set<Playlist>  (@ManyToMany, EAGER)

Playlist
  ├── id, name, color, inviteCode (unique, immutable)
  ├── songs: List<Song>  (@OneToMany, cascade ALL, orphanRemoval — story 15 replaces this with a join table)
  └── users: Set<User>   (@ManyToMany, mappedBy "playlists")

Song
  ├── id, artist, title, releaseYear, youtubeId, gradientColor1, gradientColor2
  ├── songTag (single enum — story 30 needs genre/popularity fields story 23 may or may not cover)
  ├── country
  ├── playlist: Playlist  (@ManyToOne — story 15 replaces this with the join table above)
  └── addedBy: User       (@ManyToOne, no inverse mapping, no cascade — the DELETE /me bug in TASKS.md's Bug fixes)

RefreshToken
  ├── id, token (unique, hashed)
  ├── user: User  (@OneToOne)
  └── expiresAt
```

`Song` today has none of `verificationStatus`, `confidence`, or `metadataRaw`; a single `artist` string, not the ordered multi-artist list story 23 decides. See `ARCHITECTURE.md`'s Song and playlist database section and story 23 in `TASKS.md` for the target shape.

### Planned (not yet code, target shape per ARCHITECTURE.md and TASKS.md)

Listed here so the entity picture is in one place; each is still greenfield work under its own story.

- `Group`, `Member` (story 39)
- `GameSession`, `Player`, `Round`, `Guess` (story 10)
- `ChatMessage` (story 13)
- `SongReport`, `SongConfirmation` (story 17)
- `SongArtist` (ordered artist list, replaces `Song.artist`), story 23
- `PendingImport`, an alternate-YouTube-ID-to-`Song` mapping table (story 40)
- `SongDifficulty` aggregate view or table (story 30)
- `TEST`/`ADMIN` values on `User.role` (stories 44 and 19/40)

## State diagrams

### Song verification status (stories 18, 23)

```mermaid
stateDiagram-v2
    [*] --> UNVERIFIED: song submitted
    UNVERIFIED --> VERIFIED: MusicBrainz, Discogs, and Wikidata agree exactly (no LLM call)
    UNVERIFIED --> NEEDS_REVIEW: sources disagree, Wikipedia + four-source reconciliation runs
    UNVERIFIED --> MANUAL_ENTRY: no source, including Wikipedia, has any data
    NEEDS_REVIEW --> VERIFIED: never happens automatically, an admin's manual review is the only path
    VERIFIED --> VERIFIED: locked, no code path may overwrite the year, including a story 17 report
```

`VERIFIED` is a lock, not just a status: once set, nothing (including a community report) changes the year without going through the same manual review process, per the 2026-08/2026-09 `DECISIONS.md` entries. `MANUAL_ENTRY` is the least-trusted tier, distinct from `NEEDS_REVIEW`.

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
