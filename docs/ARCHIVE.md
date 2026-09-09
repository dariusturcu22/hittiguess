# ARCHIVE.md: Completed Stories

Stories move here from [PROJECT_STATE.md](PROJECT_STATE.md) once their status reaches Implemented and every task under them in [TASKS.md](TASKS.md) is checked off. Kept for history, not read during normal sessions.

## Story 1: User authentication with refresh tokens

Area: Backend. JWT-based authentication with refresh tokens, plus OAuth2 login, in `AuthController`, `AuthResult`, and `AuthResponse`. Predates `TASKS.md` tracking, no original task breakdown exists to archive alongside it.

## Story 2: Playlist creation and management

Area: Backend. Playlist CRUD and invite-link joining in `PlaylistController`, backed by `Playlist`, `PlaylistDetailDTO`, and `PlaylistSummaryDTO`. Predates `TASKS.md` tracking.

## Story 3: Song submission and CRUD

Area: Backend. Playlist-scoped song submission and editing in `PlaylistController`, backed by `Song`, `CreateSongRequest`, and `UpdateSongRequest`. Predates `TASKS.md` tracking.

## Story 4: Multi-source metadata pipeline synthesized by an LLM

Area: Backend / AI. Originally implemented in the core service, later moved to the AI microservice by story 6's two-service split below. Predates `TASKS.md` tracking.

## Story 5: Printable PDF/QR card generation

Area: Backend. PDF and QR export in `ExportController` and `CardGenerator`. Predates `TASKS.md` tracking.

## Story 6: Two-service split

Area: Infra. Split the backend into a Spring Boot core service and a Python/FastAPI AI microservice (`ai/`). The AI microservice owns the full metadata pipeline: the source integrations (YouTube live; MusicBrainz, Wikipedia, and Genius all paused, see below), prompt construction, and LLM synthesis through OpenAI's structured output mode with Pydantic validation, replacing the previous regex-stripped manual JSON parse. It exposes a single internal endpoint, `POST /metadata/resolve`, gated by a shared secret header (`X-Internal-Api-Key`). The core service's `SongMetadataService` calls that endpoint over a `RestClient`, keeping `SongMetadataController`'s public contract, `GET /api/metadata/song` and the `AiResponse` shape, unchanged. The one-in-flight-request-per-user rate limit stays in the core service. Nine files whose logic moved to the AI microservice were deleted from the core service, along with the Spring AI dependency.

AI microservice:
- [x] Scaffold the FastAPI project structure, with pytest configured
- [x] Port the four metadata source integrations (YouTube Data API, MusicBrainz, Wikipedia, Genius) to Python. Only YouTube makes live calls right now; MusicBrainz, Wikipedia, and Genius all return no result pending a review to confirm each one's API usage is official, legal, and ethical, see the 2026-08 "Pause MusicBrainz, Wikipedia, and Genius" entry in `docs/DECISIONS.md`
- [x] Port `MetadataPromptBuilder`'s prompt-construction logic
- [x] Replace the regex-stripped LLM response parsing with OpenAI's structured output / JSON schema mode and Pydantic model validation
- [x] Add an internal endpoint, `POST /metadata/resolve`, that gathers the available sources and returns the synthesized result
- [x] Add basic tests: prompt building, URL building, response parsing/validation
- [x] Own `OPENAI_API_KEY` and `YOUTUBE_API_KEY` in its own config

Core service:
- [x] Rewrite `SongMetadataService` to call the AI microservice's internal endpoint over HTTP, keeping `SongMetadataController`'s public contract unchanged
- [x] Keep the one-in-flight-request-per-user rate limit in the core service
- [x] Authenticate between the two services with a shared secret header
- [x] Remove the Spring AI dependency
- [x] Delete the files whose logic moved entirely to the AI microservice: `YouTubeMetadataService`, `MusicBrainzService`, `WikipediaService`, `GeniusService`, `MetadataParser`, `MetadataPromptBuilder`, `UrlBuilder`, `HttpUtils`, `FlexibleYearDeserializer`
- [x] Drop `OPENAI_API_KEY` and `YOUTUBE_API_KEY` from the core service's env

Frontend fix (found while confirming these tasks):
- [x] `AddSongForm.tsx`'s `handleGetDetails` now treats a 200 response with `status: "ERROR"` the same as a thrown error

## Audit fixes

A full security and bug audit of the pre-split monolith found 51 findings, batched into reviewable groups below. No story needed, this is fix work on the current codebase, same as any other bug. Each batch is its own PR. Every `// TODO` and `// FIXME` comment found in the backend during the audit gets resolved somewhere in these batches too, either fixed or, where the code was already correct, replaced with a real explanation. None should be left by the time batch 6 is done.

- [x] Batch 1: auth cookie and token security, CSRF, refresh token storage, login and registration enumeration
- [x] Batch 2: OAuth2 hardening plus backend exception handling and logging, insecure deserialization, account linking, missing-photo crash, replace System.out/System.err/IO.println with real SLF4J logging (`@Slf4j` from Lombok, already a dependency), stop leaking internal errors, fix wrong status codes. Also closes out every `// TODO`/`// FIXME` in the backend exception-handling and security code: replaces the generic `RuntimeException` throws with a proper `ConflictException` for already-exists cases and Spring Security's own `AccessDeniedException` for access-denied cases (both then need no custom handler for the 403 case, Spring's `ExceptionTranslationFilter` already does that), fixes the deprecated `DaoAuthenticationProvider` constructor usage in `SecurityConfig`, and replaces the two confused TODOs in `JwtAuthenticationFilter` with a short explanation now that they're understood, both turned out to be correct code.
- [x] Batch 3: backend input validation, injection hardening, and metadata pipeline safety. Adds `@Pattern` validation for `youtubeId` on create and update, makes `MetadataParser`'s fallback return null instead of the raw unvalidated input (closes the last remaining TODO), URL-encodes the YouTube API call, escapes MusicBrainz's Lucene query, makes `FlexibleYearDeserializer` reject malformed years instead of silently corrupting them, strengthens the LLM prompt's framing around untrusted video-description text, and rate-limits `/api/metadata/song` to one in-flight request per user rather than letting one user queue unlimited concurrent slow requests. Release year bounds corrected after review: 1000 instead of an initial 1860 (which was wrong for classical music), and a dynamic not-future check instead of a fixed 2100. Not included: the regex-stripped LLM parsing is left as is, since it gets replaced properly by Pydantic structured output during the story 6 split, fixing it twice isn't worth it.
- [x] Batch 4: export/PDF fixes plus backend dead code and minor correctness. The real bug wasn't that `/export/info` returns the wrong PDF, it already returns the right content, it's that the bare `/export` endpoint is an unused duplicate of it (the frontend only ever calls `/export/info` and `/export/qr`) with a mismatched Swagger summary on top. Removing the dead `/export` endpoint entirely rather than keeping three routes for two behaviors, and fixing the summaries on the two that remain. Also: a size cap on export so a huge playlist can't tie up the server, `@BatchSize` on `Playlist.songs` to fix the N+1 in `UserService`, deleting `UserMapper.updateEntity` since it's dead code that does nothing, simplifying `SongMapper`'s releaseYear check now that batch 3's validation already guarantees it's never actually 0, and removing the no-op `assert` in `SongMetadataService`.
- [x] Batch 5: frontend auth/routing plus forms and data quality. Fixes the dead processQueue so queued requests actually resolve when a token refresh completes. Makes proxy.ts actually redirect unauthenticated visitors instead of always calling next(), and along the way fixes its route list: "/landing" matched nothing real since (landing) is a route group stripped from the URL, the real landing page is "/", and "/forgot-password" was missing entirely. Clears the query cache on logout. Adds real youtubeId, release year, and hex color validation to both song forms, with visible error messages instead of silent failures on submit. Makes the non-functional forgot-password form honest about not being implemented yet instead of silently doing nothing, building a real password-reset flow is a new feature, not a bug fix, out of scope here. Also removes the leftover console.log in AddSongForm.tsx while already in that file, closing out that separately-tracked bug below.
- [x] Batch 6: frontend small bugs and cleanup. Mounts sonner's Toaster, it existed as a component but was never actually rendered anywhere, so toast() calls would have silently done nothing. Uses it to replace the silent failures: login and register's placeholder onError comments, join-playlist's redirect-with-error-query-param that nothing ever read, and data-table's export which never checked response.ok before treating a failed download as a real file. Fixes the export filename collision, info and qr downloads overwrote each other. Fixes RedirectHandler not URI-encoding the error param it pushes into a URL string. Removes the dead "Account" menu item, there's no account page to link it to, building one is a feature not a bug fix. Fixes site-header initializing color state from the title prop. Fixes the join-playlist effect's dependency array and adds a guard against double-firing under StrictMode. Removes both dead rewrites from next.config.ts, /backend and /login/oauth2, neither is used anywhere, the whole OAuth2 flow goes straight to the backend domain and never touches these frontend paths, and drops /backend from proxy.ts's route list to match.

## Dependency upgrades

No story required for these. Each upgrade is its own `chore` branch.

- [x] Backend: Spring Boot 3.5.10 → 4.1.1 (3.5.x reached OSS end of life 2026-06-30): bump `spring-boot-starter-parent`, `spring-ai.version` (Spring AI 2.0.x), and `springdoc-openapi-starter-webmvc-ui` (3.0.x); swap `jjwt-jackson` for `jjwt-gson` since jjwt doesn't support Jackson 3 yet; migrate the ten files that import Jackson directly from `com.fasterxml.jackson.*` to the Jackson 3 `tools.jackson.*` API; confirm the Spring Security 7 OAuth2 client property namespace still resolves. Also renamed the two starters Boot 4 deprecated (`spring-boot-starter-oauth2-client`, `spring-boot-starter-web`), updated `BackendApplication`'s `SecurityAutoConfiguration` import for Boot 4's autoconfigure package split, and adjusted a Spring AI 2.0 `ChatClient.options()` call to its new builder-accepting signature. The OAuth2 client property namespace is unchanged in Boot 4, confirmed against Spring Boot's own configuration changelog, only the starter artifact id was renamed.
- [x] Frontend: Next.js 16.1.6 → 16.3.2, plus minor/patch bumps across `@hookform/resolvers`, `@tabler/icons-react`, `@tanstack/react-query`, `axios`, `lucide-react` (0.x → 1.x), `radix-ui`, `react-hook-form`, `sonner`, `tailwind-merge`, and `zod`. `@tanstack/react-table` stayed pinned to 8.21.3 (v9 still in beta as of mid-2026), `recharts` stayed on 2.15.4 (its only importer is unused shadcn scaffolding).
- [x] Re-check other frontend deps against current versions, done as part of the Next.js upgrade above.
- [x] Full build and test pass on both services after upgrading, before moving on. `mvnw.cmd clean package -DskipTests` and `npm run build && npm run lint` both pass clean on the final merged `dev`.

## Pre-split polish

No story required, this is fix/chore work. Goal: the base game goes from working-but-buggy to fully polished before the two-service split (story 6) starts, so the split has a solid baseline to carry over instead of carrying bugs into two codebases. Each item is its own branch unless noted.

- [x] Fix all frontend lint errors and warnings (`npm run lint`), done as part of the Next.js upgrade.
- [x] Eliminate all backend build warnings, confirmed clean as part of the Spring Boot 4 upgrade and the Maven wrapper bump to 3.9.16 (see the dependency-cleanup entry below). What remains is JVM startup noise from Maven's own jansi library and from Lombok's use of `sun.misc.Unsafe` (projectlombok/lombok#4046, open upstream as of JDK 25), neither of which comes from this project's code or has a released fix yet.
- [x] Eliminate all frontend build warnings, confirmed clean as part of the Next.js upgrade.
- [x] Remove unused dependencies and unused imports flagged across the frontend and backend, confirmed with the project owner before removal. Frontend: `@dnd-kit/*` (4 packages, unused), `recharts` and `vaul` (each only used by a dead shadcn scaffold component, both removed together). Backend: 5 files had unused imports; while in the metadata pipeline files, also caught and fixed `JsonNode.asText()`/`asText(String)` calls left over from the Jackson 3 migration, deprecated in favor of `asString()`/`asString(String)`, which the compiler only flags as a warning when deprecation warnings are shown explicitly.
- [x] Hands-on QA pass through the full game flow (auth, playlist CRUD, song submission, export, multi-user playlist collaboration) to find bugs, rough edges, and incomplete features. Session play isn't in scope yet, story 9-13's realtime/DJ features are still `Needs Definition`. Findings logged as a batch below, same pattern as the audit fixes above.
- [x] Fix everything found in the QA pass: 5 real bugs (playlist rename/color-change validation, join-playlist stuck redirect, touch-device-invisible rename button, misleading empty-search message), plus silent-failure toasts added to the first fix.
- [x] Remove comments that just restate the code they sit on, across backend and frontend. Found in `CardGenerator.java` and `layout.tsx`/`components/shadcn/sidebar.tsx`; the rest of the codebase's comments already explain non-obvious reasoning rather than restating code.

## Bugs and minor fixes

No story required for these. Fix on a `fix` or `chore` branch.

- [x] Remove leftover `console.log` in `AddSongForm.tsx` (done as part of batch 5)
- [x] Fix `docker-compose.yml`'s Postgres volume mount for the `postgres:18-alpine` image, which crash-looped on every start under the old pre-18 mount path
- [x] Fix `UpdatePlaylistRequest` requiring both `name` and `color` as `@NotBlank`, breaking both the playlist rename and color-change features (found during the QA pass, see below). Also adds error toasts to both, previously silent failures.
- [x] Fix the join-playlist page (`/playlists/join/[inviteCode]`) getting stuck on "Joining playlist..." forever: the join mutation's per-call `onSuccess`/`onError` callbacks never fired regardless of whether the join actually succeeded or failed server-side, confirmed with direct logging inside them. Switched to `mutateAsync` with `.then()`/`.catch()`, which resolves reliably.
- [x] Make the playlist rename button visible without hovering; `opacity-0 group-hover/title:opacity-100` left it permanently invisible on touch devices, which have no hover state.
- [x] Give the song table a distinct "no search results" message instead of reusing "No songs in this playlist yet." when a search just has no matches.

## Session tooling

No story required for these. Chore branch.

- [x] Write a script that moves fully-checked-off `TASKS.md` sections into `ARCHIVE.md`, so the session-end habit in `CLAUDE.md` doesn't depend on remembering to do it by hand. For a `## Story N — ...` section, only archive it once `PROJECT_STATE.md` also has that story's status as `Implemented`, and remove its row there too. Update `CLAUDE.md`'s session-end habit to point at running the script.

## Local dev tooling

No story required for these. Chore branch.

- [x] Add a root `Makefile` with a `dev` target that starts the local Postgres container, the core service, the AI microservice, and the frontend with one command, `make dev`. Extend it with the TURN server once story 12 (voice chat) adds one.

## Docs cleanup

No story required for these. Docs branch.

- [x] Replace the em dash in every doc file's H1 title (`# FILE.md — Description`) and in `TASKS.md`'s `## Story N — Name` headings with a colon, the writing-style rule against em dashes applies to every markdown file in the repo and these headers are the only place it had slipped through.

## Backlog audit against the real codebase

Every story below with draft tasks was drafted in a large batch from a single early codebase-mapping pass, then refined through design conversation, not re-verified individually against live code the way stories 10, 11, and 39 originally were before going Ready. This audit goes through each one, checkbox by checkbox, confirms it against the current code, fixes anything wrong or missing, and only then flips the story to Ready in `PROJECT_STATE.md`.

- [x] Story 9
- [x] Story 12
- [x] Story 13
- [x] Story 14
- [x] Story 15
- [x] Story 16
- [x] Story 17
- [x] Story 19
- [x] Story 22
- [x] Story 23
- [x] Story 24
- [x] Story 25
- [x] Story 26
- [x] Story 27
- [x] Story 30
- [x] Story 32
- [x] Story 33
- [x] Story 34
- [x] Story 35
- [x] Story 36
- [x] Story 37
- [x] Story 38

## Dropped and consolidated stories

Stories dropped outright, or folded into another story's tasks rather than kept as their own, removed from `PROJECT_STATE.md`'s active table. Kept here for the reasoning behind each, not for further action.

- **Story 19: Admin bulk song import.** Consolidated into story 40, which redefines bulk import as two separate paths (a slow admin backlog queue, and immediate on-the-spot resolution for any user) rather than one CSV/JSON endpoint. The `ADMIN` role and access-check prerequisite story 19 identified (`User.role` only had a `USER` value, no admin-only access check existed anywhere) still applies to story 40's admin-only backlog endpoints.
- **Story 21: Auto-generated featured playlists.** Its theme-request generation was briefly absorbed into story 30, then dropped there too once story 30 was cut down to two modes, Difficulty-Based and Custom, see the 2026-09 `DECISIONS.md` entry. No on-the-spot themed generation is planned.
- **Story 29: Content-based song recommender.** No viable audio-feature data source found. AcousticBrainz, the obvious free option, shut down its live API and submission pipeline in February 2022; only a frozen dataset remains, dated June 2022, with coverage skewed toward mainstream music already analyzed before the shutdown, exactly the opposite of the niche/underground coverage this project cares about. Self-hosting Essentia (the toolkit AcousticBrainz itself used) would work on any song, but needs the actual audio file, and the only way to get that for a YouTube-sourced song is unofficial downloading, which violates `CLAUDE.md`'s non-negotiable official-APIs-only rule and the DJ-link-out architecture built specifically to avoid touching YouTube's media stream. Paid catalog APIs (Apple Music at $99/year, various smaller commercial ones) are real ongoing cost for a nice-to-have feature and still don't reliably cover niche YouTube-only tracks. Dropped rather than left blocked indefinitely.
- **Story 31: "Similar songs" via text embeddings.** The only version of "similar songs" worth building is audio-based (how a song actually sounds), not text-based (which mostly just catches same-artist or similarly-worded matches). See story 29 above for why the audio-based version doesn't have a viable data source either.
- **Story 32: Periodic LLM-as-judge catalog audit.** Redundant once story 18/40's two-tier verification pipeline was decided: every song already gets verified on submission, and a fast-tier answer gets re-verified through the patient tier afterward, so a separate scheduled audit pass over the whole catalog duplicates that coverage. A manual, admin-triggered version, run on demand rather than on a schedule, isn't ruled out, but isn't a defined feature.
