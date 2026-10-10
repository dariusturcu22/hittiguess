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

- **Story 26: Cache metadata pipeline results by artist/title or YouTube ID.** Dropped after scope review because the exact-ID lookup and pgvector reuse covered its intended value. The completed scope-review tasks are retained below under Dropped story 26.

Stories dropped outright, or folded into another story's tasks rather than kept as their own, removed from `PROJECT_STATE.md`'s active table. Kept here for the reasoning behind each, not for further action.

- **Story 19: Admin bulk song import.** Consolidated into story 40, which redefines bulk import as two separate paths (a slow admin backlog queue, and immediate on-the-spot resolution for any user) rather than one CSV/JSON endpoint. The `ADMIN` role and access-check prerequisite story 19 identified (`User.role` only had a `USER` value, no admin-only access check existed anywhere) still applies to story 40's admin-only backlog endpoints.
- **Story 21: Auto-generated featured playlists.** Its theme-request generation was briefly absorbed into story 30, then dropped there too once story 30 was cut down to two modes, Difficulty-Based and Custom, see the 2026-09 `DECISIONS.md` entry. No on-the-spot themed generation is planned.
- **Story 29: Content-based song recommender.** No viable audio-feature data source found. AcousticBrainz, the obvious free option, shut down its live API and submission pipeline in February 2022; only a frozen dataset remains, dated June 2022, with coverage skewed toward mainstream music already analyzed before the shutdown, exactly the opposite of the niche/underground coverage this project cares about. Self-hosting Essentia (the toolkit AcousticBrainz itself used) would work on any song, but needs the actual audio file, and the only way to get that for a YouTube-sourced song is unofficial downloading, which violates `CLAUDE.md`'s non-negotiable official-APIs-only rule and the DJ-link-out architecture built specifically to avoid touching YouTube's media stream. Paid catalog APIs (Apple Music at $99/year, various smaller commercial ones) are real ongoing cost for a nice-to-have feature and still don't reliably cover niche YouTube-only tracks. Dropped rather than left blocked indefinitely.
- **Story 31: "Similar songs" via text embeddings.** The only version of "similar songs" worth building is audio-based (how a song actually sounds), not text-based (which mostly just catches same-artist or similarly-worded matches). See story 29 above for why the audio-based version doesn't have a viable data source either.
- **Story 32: Periodic LLM-as-judge catalog audit.** Redundant once story 18/40's two-tier verification pipeline was decided: every song already gets verified on submission, and a fast-tier answer gets re-verified through the patient tier afterward, so a separate scheduled audit pass over the whole catalog duplicates that coverage. A manual, admin-triggered version, run on demand rather than on a schedule, isn't ruled out, but isn't a defined feature.

## Chore: Backlog refinement from the project owner's specification

A written specification from the project owner added new cross-cutting architecture stories, corrected several existing stories, and called for a documentation cleanup pass. Split across separate branches below since it touches unrelated parts of the backlog; each is checked off once its PR merges.

- [x] Cross-cutting: new stories for the database split (42), metadata minimization (43), and test user infrastructure (44); the access-token reissuance bug fix; a rate-limit stress-testing task on story 27; a legal-page scope clarification on story 37
- [x] Story 9: DJ-controlled reveal flow, audio cutoff sequence, audio-sharing UI warning, and its missing test tasks
- [x] Stories 10 and 11: dedupe the active/non-active player token and leaderboard tasks, add story 11's telemetry requirement
- [x] Stories 24 and 25: full task rewrite against current rate limits, architecture, and decisions
- [x] Stories 26, 30, and 40: caching-layer scope review, playlist-selection cut down to two modes, dedup pipeline's exact-match linking decided
- [x] Backlog cleanup: remove dropped/consolidated stories from the active tables, audit for conflicting decisions across docs
- [x] Implementation roadmap document, explicit sequential order for remaining stories
- [x] Structured system docs: API contracts, entity model, state diagrams
- [x] Frontend content specifications, independent of story 28's visual design

## Chore: Documentation consistency audit

A full pass across every file in `docs/` (`VISION.md`, `ARCHITECTURE.md`, `GAME_DESIGN.md`, `PROJECT_STATE.md`, `TASKS.md`, `ROADMAP.md`, `SYSTEM_REFERENCE.md`, `FRONTEND_CONTENT.md`, `DECISIONS.md`, `ARCHIVE.md`), cross-referencing every story against its own decisions and against the others, to catch drift between a decision being made and every doc that restates it actually getting updated. No story required, this is docs-only fix work.

- [x] `ARCHITECTURE.md`'s DJ playback section still described the superseded DJ-controls-the-round's-flow mechanism (pause/play/close/end-turn/manual reveal over WebSocket), not the later "DJ holds no in-app round controls; reveal and turn advance run automatically" decision (`DECISIONS.md`). Updated to match
- [x] `TASKS.md` story 9's preamble and task list assigned DJ-only round-flow-control tasks and tests (manual reveal, pause/play/close/end-turn) superseded by the same later decision; preamble rewritten, stale tasks/tests dropped, keeping only what the DJ view actually still does (the link-out action itself, gated to the DJ role)
- [x] Story 10 had no explicit task for the automatic reveal step `GAME_DESIGN.md` and `ARCHITECTURE.md`'s data flow both describe (artist/title/year broadcast once the betting window closes, no DJ or player trigger); added, plus a matching test
- [x] `ARCHITECTURE.md`'s AI microservice source list (`YouTube, MusicBrainz, Discogs, Wikidata`) was missing Wikipedia, added as a fourth source by the 2026-09 "Metadata pipeline final shape" `DECISIONS.md` entry
- [x] `ARCHITECTURE.md`'s "What's built" bullet described MusicBrainz, Wikipedia, and Genius as "paused pending an API compliance and cost review"; that review finished (`DECISIONS.md`'s 2026-08/09 source-set entries): Genius is dropped for good, MusicBrainz and Wikipedia are kept sources still needing their stub replaced with a real implementation. Bullet updated to say so
- [x] `ARCHITECTURE.md`'s "Not yet built" list named "scheduled re-verification" and "auto-generated featured playlists", both dropped stories (32 and 21). Removed both
- [x] `ARCHITECTURE.md`'s Song and playlist database section called the release-year field shape and multi-artist storage "undecided"; both were resolved by 2026-09 `DECISIONS.md` entries and `PROJECT_STATE.md`'s resolved questions already reflect it. Section now states the decided shape instead of pointing at an open question that no longer exists
- [x] `SYSTEM_REFERENCE.md`'s entity model section referenced "stories 19/40" and "stories 44 and 19/40" for the `ADMIN` role; story 19 is consolidated into story 40 and no longer an active story (`ARCHIVE.md`). Now points at story 40 alone
- [x] `TASKS.md` story 44's own text had the same stale "story 19/40" phrasing in two places; fixed to story 40 alone
- [x] `TASKS.md` story 41 had a completed, checked-off decision (a flagged injection attempt writes an abuse-visibility event) with no corresponding `DECISIONS.md` entry; logged
- [x] `PROJECT_STATE.md` listed story 46 as `Needs Definition` even though its own `TASKS.md` section opens with "Checked against real code", cites specific real code (`Playlist.users`, `UserController`'s join endpoint), and states no blocking dependency, only a coordination note with story 15; every other story with that same "checked, not blocked" shape is marked `Ready`. Promoted
- [x] `GAME_DESIGN.md`'s "Planned game modes" section (Decade Challenge, Genre Round, Underground Mode, Speed Round) had no story anywhere in the backlog, and Genre Round in particular is the same idea as the theme-based generation the 2026-09 "Story 30 cut to two top-level modes" `DECISIONS.md` entry explicitly dropped ("no on-the-spot themed generation is planned"). Section reframed to read as unscoped future ideas, not a contradicted claim of planned scope

## Story 20: LLM client infrastructure for the metadata pipeline

Checked against real code: `ai/app/clients/openai_client.py` is the only LLM client, a module-level `OpenAI` singleton, no other client exists. Greenlit to build (`DECISIONS.md`): the spike's validated model choice, gpt-5-nano for reconciliation and DeepSeek-V4-Flash for Wikipedia extraction, goes into production rather than staying validated-but-unbuilt. gpt-5-nano needs no new client, the existing `openai_client.py` already calls OpenAI by model name; DeepSeek-V4-Flash is hosted on DeepInfra, a different provider, so this story's actual remaining scope is narrow: add that one client. Not blocked on anything, this is infrastructure with no dependency on the Song schema; in practice it lands together with or just ahead of story 18, its first real caller.

- [x] Add a DeepInfra (OpenAI-compatible) LLM client to `ai/app/clients/`, copy and adapt the validated request-building and error-handling logic in `ai/spikes/openai_compatible_spike.py`: temperature 0.0 for structured-output calls (confirmed live to remove run-to-run answer variance on close reconciliation calls), a 90-second request timeout (the real fix for the Nemotron-hang bug the spike found), and the `response_format` JSON-schema-mode-with-forced-tool-calling-fallback pattern. The client itself (`deepinfra_client.py`) stays a thin `OpenAI` singleton matching `openai_client.py`'s own shape; the structured-output call logic, generic over the caller's own Pydantic response model, lives in `llm.py` alongside the existing `synthesize` function
- [x] Wire DeepSeek-V4-Flash as the model this client calls, the extraction model story 18's Wikipedia-reading step depends on
- [x] Add the DeepInfra API key to AI service config (`config.py`), following the existing `openai_api_key` pattern
- [x] Drop every other shortlisted candidate (Groq, llama.cpp, AWS Bedrock Nova Micro) from further production consideration, none beat gpt-5-nano/DeepSeek-V4-Flash on the actual reconciliation/extraction tasks this pipeline needs; kept only as documented spike results in `ai/spikes/`, not carried into the microservice

Tests:
- [x] Unit tests for the DeepInfra client's request building and response parsing, mirroring `youtube.py`'s existing test pattern
- [x] Unit test for the client's timeout behavior: a hung request fails after 90 seconds rather than blocking the pipeline indefinitely
- [x] Unit test confirming structured output validates correctly against a Pydantic schema for both the extraction and reconciliation call shapes

## Story 23: Song schema reconciliation

Checked against real code and `ARCHITECTURE.md`'s target shape (line 36): `Song` today has a single `releaseYear` int, a single `songTag` enum, a single `artist` string, and no `verificationStatus`, `confidence`, or `metadataRaw` fields. No migration tool exists yet, schema changes today happen only through Hibernate's `ddl-auto=update`; this story introduces Flyway rather than add another layer of auto-DDL.

Release year is one mutable field plus `verificationStatus`, decided, not `submittedYear`/`verifiedYear` as two separate fields: once verified, the year doesn't change except through the same review process that verified it, matching story 18's lock rule and the report/re-verification design under discussion.

Multiple artists: decided. A song's artists are an ordered list, each entry tagged `MAIN` or `FEATURED`; a song can have more than one `MAIN` artist (a joint credit like "Queen & David Bowie" has two, neither featured), plus any number of `FEATURED` ones. The role tag is a display concern only, a physical card prints the main artist(s) then "featuring" the featured ones, guessing and scoring treat every artist on the list identically, naming any single one correctly is enough (story 10).

Title cleaning changes as a result, superseding the AI microservice's current `prompt.py` rule that keeps a `(feat. X)` clause in the title text: that clause is stripped out instead, its artist extracted into the structured list. Only a remix, cover, or mashup clause survives in the title text, and each of those creates its own separate `Song` row entirely, its own artist list and release year, not a variant of the original; story 16's pgvector duplicate check must not treat a remix/cover/mashup as a near-duplicate of the original it's based on.

- [x] Introduce Flyway as the schema migration tool. `ddl-auto` moves to `validate`; `baseline-on-migrate`/`baseline-version=1` let the existing Supabase instance adopt the reconstructed baseline without re-running it, a fresh database runs it for real. Note for anyone touching Flyway again on this Spring Boot version: `flyway-core` alone does not trigger Spring Boot's autoconfiguration, that needs the separate `spring-boot-flyway` dependency too, confirmed live after it silently never ran without it
- [x] Add a `verificationStatus` field: `UNVERIFIED` (default), `VERIFIED` (locked, matches story 18's lock rule), `NEEDS_REVIEW` (an LLM-reconciled year, not locked), and `MANUAL_ENTRY` (a human-entered year for a song no source had any data on, the least-trusted tier, distinct from the other three)
- [x] Replace the single `artist` string with an ordered `SongArtist` list (song, artist name, role: `MAIN`/`FEATURED`), supporting more than one `MAIN` entry. No featured-artist extraction pipeline exists yet (that's a later story), so `CreateSongRequest`/`UpdateSongRequest` still accept one submitted artist name; the mapper wraps it as a single `MAIN` entry, ready for the list to actually grow once extraction exists
- [x] Update the AI microservice's title-cleaning prompt (`prompt.py`): stop keeping `(feat. X)`/`(ft. X)` clauses in the title, extract them into the artist list instead; keep remix/cover/mashup clauses in the title, and treat each as its own distinct submission rather than a variant of the original song
- [x] Persist `confidence` on `Song`, depends on the `SongMetadataResponse` fix in this file's Bug fixes section existing first
- [x] Persist `metadataRaw`, the full pipeline output, for auditability
- [x] Replace the single `songTag` enum with a multi-value `tags` relation, and update `SongMapper`'s default-to-`NONE` behavior in `toDTO`/`toEntity`, which won't map cleanly onto "no tags" vs. a tag literally named `NONE` once it's a collection. `SongTag` drops the `NONE` constant entirely, an empty collection is how "no tags" is represented now
- [x] Data migration for existing rows: default `verificationStatus`
- [x] Update `SongDTO`, `CreateSongRequest`, `UpdateSongRequest`, `SongMapper`, and regenerate the frontend's orval client and song forms for the new shape. The frontend model files are hand-written to match the new backend shape, not regenerated for real: this sandbox's JDK can't open the loopback socket `java.net.http.HttpClient` needs, which blocks the backend from starting here at all (the AI-service and OAuth2 RestClient beans both hit it), so there's no live `/v3/api-docs` to run `orval` against. Needs a real `npm run api:gen` on a machine where the backend can start, to confirm these match exactly
- [x] Gate the song-edit endpoint and the frontend edit action by `verificationStatus`, decided during story 28's design pass: only `MANUAL_ENTRY` songs stay editable; `VERIFIED` and `NEEDS_REVIEW` songs lose the edit action entirely, editing verified or LLM-reconciled data by hand undermines the trust tiers the pipeline already established. `VERIFIED`/`NEEDS_REVIEW` keep only the report action (and, `NEEDS_REVIEW` specifically, story 17's thumbs-up once it ships); a non-`MANUAL_ENTRY` edit attempt is rejected server-side, not just hidden client-side. `UNVERIFIED` stays editable too, alongside `MANUAL_ENTRY`: it's every song's actual status today since no pipeline exists yet to move it anywhere else, and it hasn't been through any pipeline step whose trust tier hand-editing would undermine

Tests:
- [x] Unit tests for the data migration: `verificationStatus` defaulted correctly for existing rows, plus the legacy artist string and tag both carrying forward correctly into the new tables (`SongSchemaMigrationTest`, run against a real Postgres via Testcontainers)
- [x] Integration test: existing API responses (`SongDTO`) don't break for rows migrated from the old shape (`SongApiCompatibilityAfterMigrationTest`); a separate test (`FlywayAutoConfigurationRunsOnStartupTest`) confirms Spring Boot's own Flyway bean, not just the Java API called directly, actually migrates a fresh database on startup
- [x] Unit tests for the edit-access gate: a `MANUAL_ENTRY` or `UNVERIFIED` song accepts an edit, `VERIFIED` and `NEEDS_REVIEW` reject one server-side regardless of frontend state (`PlaylistServiceTest`)

## Story 15: Song/playlist relational fix

Checked against real code: `Song.playlist` is a required singular `@ManyToOne`, one song belongs to exactly one playlist today. Touches the same table as story 23; sequencing or combining the two migrations avoids two separate schema changes to `Song`.

- [x] Introduce a join table between `Song` and `Playlist`, replacing the singular `@ManyToOne playlist` on `Song`
- [x] Rewrite `Playlist.songs`'s `@OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)` relation and its `addSong`/`removeSong` helpers, both of which assume the singular back-reference (`song.setPlaylist(this)`/`song.setPlaylist(null)`) that a join table removes
- [x] Migrate existing data: each song's current single playlist link becomes one row in the new join table
- [x] Update `PlaylistService`'s `checkSongBelongsToPlaylist`, which currently assumes one song belongs to exactly one playlist; `checkPlaylistAccess` doesn't need changing, it checks playlist-user membership and doesn't touch the song relation
- [x] Decide song deletion semantics once a song isn't playlist-exclusive: does removing a song from one playlist delete it outright, or only unlink it? `PlaylistController`'s current delete-song endpoint, via `Playlist.removeSong` and `orphanRemoval = true`, does a real delete today. Decided: always unlink, never delete the song itself, a song is independent of any playlist it belongs to, see `DECISIONS.md`'s 2026-09 "Song deletion reversed" entry (superseding this same entry's earlier "unlink first, delete only when orphaned everywhere" decision)
- [x] Update `SongDTO`/`PlaylistDetailDTO`, `PlaylistMapper.toDetailDTO` (the code path that assembles a playlist's song list), and the frontend to reflect a song appearing in multiple playlists. Neither DTO ever exposed the singular relation directly, so their shape is unchanged; `PlaylistMapper.toDetailDTO` and the frontend continue to work against `Playlist.getSongs()` as before, now backed by the join table
- [x] Coordinate with story 23 (schema reconciliation), both touch `Song`'s shape. Built directly on top of story 23's `Song` entity rewrite rather than against a stale copy

Tests:
- [x] Unit tests for `checkSongBelongsToPlaylist` against the new many-to-many relation, plus a regression check that `checkPlaylistAccess` is unaffected
- [x] Integration test: migrating existing data preserves each song's original playlist link
- [x] Integration test: a song in multiple playlists behaves correctly for access checks and the decided deletion semantics

## Story 47: Replace SongTag with genre, redraw the printed card, add real paper-size export

Surfaced from `docs/design/source`'s settled mockups (`CardOptions.dc.html`, `SongDetailLight.dc.html`, the submission-flow mockups), which were built before story 23 and predate story 28's implementation phase, so they were never cross-checked against the current schema. Checked against the real code: `SongTag` (`PLAYLIST`/`SPECIAL`/`ANIME`) has no analog anywhere in the mockups; `SongDetailLight.dc.html` instead shows a single genre chip ("Alternative Rock") next to the year and duration, and no mockup shows a manual tag picker at submission, consistent with `genre` being pipeline-populated like `confidence`/`metadataRaw` rather than user-submitted. `CardOptions.dc.html` explicitly settles the printed/timeline card's look: square, one flat color, no gradient, thick border, hard offset shadow, artist/year/title only, no tag triangle or country flag. Separately, `CardGenerator`'s page math was never actually tied to a real paper size: `PAGE_WIDTH`/`PAGE_HEIGHT` are defined as exactly `CARD_SIZE * CARDS_PER_ROW`/`CARD_SIZE * ROWS_PER_PAGE`, so the margin calculation is always zero and the resulting PDF page doesn't correspond to A4, Letter, or any standard paper size.

Backend only. The frontend side, the tag/genre input surface and a still-missing print-settings screen for choosing paper size, is deferred to story 28's implementation phase; no design for that screen exists yet either.

- [x] Delete `SongTag` and the `song_tags` table; add a nullable `genre` string column to `Song` via Flyway, no automatic backfill from the old tags, there's no reliable mapping from `PLAYLIST`/`SPECIAL`/`ANIME` to a real genre
- [x] Remove `genre` from `CreateSongRequest`/`UpdateSongRequest`, it isn't user-submitted, same as `confidence`/`metadataRaw`; expose it read-only on `SongDTO`
- [x] Remove the now-meaningless `GET /api/enums/tags` endpoint and `SongMapper`'s tag-handling code
- [x] Redraw `CardGenerator`'s printed card to match `CardOptions.dc.html`: a single flat color (`gradientColor1`) instead of a two-color gradient, rounded corners, a thick border, a hard offset shadow, artist/year/title only; drop the tag-triangle and country-flag decorations the settled design doesn't show, including the fixed placeholder color a different PR had briefly given the tag triangle in the meantime. Also picks readable dark-versus-light text per card by the fill color's luminance, since a single arbitrary flat color (unlike the old two-stop gradient) can be light enough that fixed white text stops being legible
- [x] Add a `PaperSize` enum (A4, Letter) to the card/QR export endpoints, defaulting to A4; compute each page's actual card grid and margins from the paper's real dimensions at 300 DPI instead of today's fixed, arbitrary grid, for both `CardGenerator` and `QRGenerator`

Tests:
- [x] Unit tests for the `PaperSize`-driven grid math: page dimensions and cards-per-page for both A4 and Letter
- [x] Migration test: existing `song_tags` rows and the column are gone after migrating, `genre` exists and is nullable
- [x] Unit test: `SongMapper.toDTO` maps `genre` straight through; neither `CreateSongRequest` nor `UpdateSongRequest` accepts it

## Story 25: Add Discogs as a metadata source

Rechecked against current code: `ai/app/metadata/sources/musicbrainz.py`, `wikipedia.py`, and `genius.py` are stubs returning empty results, each commented with a reference to the 2026-08 pause decision (`DECISIONS.md`). No `discogs.py` or `wikidata.py` file exists yet. The resolved source set stays MusicBrainz, Discogs, and Wikidata (`PROJECT_STATE.md`), settled through the sourcing spike, not open for reconsideration. This story covers Discogs only; un-stubbing MusicBrainz and building Wikidata are tracked separately under "Spike: MusicBrainz and Wikidata sourcing," now handoff tasks off that spike rather than an open design question.

- [x] Add `ai/app/metadata/sources/discogs.py`, copy and adapt `ai/spikes/discogs_spike.py`'s validated implementation: the search-and-select logic, and the `masterless_release_years` fallback (a release with no linked master still often carries its own correct `year` field directly in the search result, silently discarded without this, a real bug the spike caught live)
- [x] Apply the "check every candidate, take the earliest" rule already used for MusicBrainz: a track can belong to more than one Discogs master (its own standalone-single release and an album it also appears on), each with its own year, trusting whichever master a search result lists first picked a wrong year for a real playlist song during the spike
- [x] Treat Discogs' `year: 0` on its master resource as unknown, not a literal date, a live-confirmed bug the spike caught on a real release ("Titanium")
- [x] Carry over `DiscogsRateLimiter` from the same spike file, live-tracking Discogs' own `X-Discogs-Ratelimit`/`X-Discogs-Ratelimit-Remaining` response headers rather than a fixed guessed rate, matching the adaptive-limiter treatment MusicBrainz's own un-stub task already calls for
- [x] Follow `sources/youtube.py`'s existing pattern (the only currently-live production source) for HTTP client usage, timeout, and broad-exception-to-`UNKNOWN_DEFAULTS` fallback
- [x] Add the Discogs API token to AI service config (`config.py`), following the existing `youtube_api_key`/`openai_api_key` pattern
- [x] Wire Discogs into `_gather_all_metadata` and add a `_append_discogs_data` function in `prompt.py`, matching the existing per-source prompt-section pattern; Discogs only feeds the lock-evaluation and reconciliation steps (story 18), it makes no LLM call of its own
- [x] Confirm Discogs' API terms of use permit this usage, matching the review MusicBrainz and Wikidata already got (`PROJECT_STATE.md`), already reviewed and accepted as part of the source-set decision, not revisited here

Tests:
- [x] Unit tests for `discogs.py`'s request building and response parsing, mirroring `youtube.py`'s existing test pattern
- [x] Unit test for the `masterless_release_years` fallback and the `year: 0`-as-unknown handling, both real bugs the spike found
- [x] Unit test for the fallback behavior on a failed Discogs call
- [x] Unit test for `DiscogsRateLimiter` pacing correctly off live response headers, not a fixed guessed interval

## Story 16: pgvector-based duplicate detection

Checked against real code: no pgvector dependency in `pom.xml`, no vector-DB client or embedding code anywhere in `ai/app`, this is greenfield on both services. Based on `ARCHITECTURE.md`'s RAG/dedup section (line 127-129): normalize `artist + title`, embed, check similarity before running the full pipeline, reuse existing data on a high-confidence match.

- [x] Enable the pgvector Postgres extension (coordinate with story 8/23 if a migration tool lands around the same time)
- [x] Add an embedding step to the AI microservice: normalize `artist + title`, generate an embedding via OpenAI's embeddings API, no embedding client exists in `ai/app` today
- [x] Store embeddings for verified songs
- [x] Add a similarity-check step before the source fetch/LLM synthesis in `metadata/service.py`'s `resolve_metadata`, reuse existing data on a high-confidence match instead of re-running the pipeline
- [x] Decide and document the similarity threshold for "high-confidence match", flagged as still unresolved in `ARCHITECTURE.md`
- [x] Coordinate with story 15 if dedup needs to consider a song already existing under a different playlist relationship

Tests:
- [x] Unit tests for the similarity-check step (mocked embedding client): a high-confidence match reuses existing data, a low-confidence match proceeds to the full pipeline
- [x] Integration test: submitting a near-duplicate song reuses existing verified data instead of re-running the LLM

## Chore: Cross-tool agent instructions

`CLAUDE.md` held every project convention (stack, commands, non-negotiable rules, writing style, code conventions, git workflow, task gate) as a Claude-Code-only file, unreadable by any other agentic coding tool used on this project. `AGENTS.md`, the cross-tool convention read natively by Codex, Cursor, Claude Code, and others, replaces it as the source of truth; `CLAUDE.md` becomes a thin pointer to it.

- [x] Move `CLAUDE.md`'s full content into a new root `AGENTS.md`
- [x] Replace `CLAUDE.md`'s content with a short pointer to `AGENTS.md`
- [x] Fix `CLAUDE.md`'s Tooling section, which pointed at `frontend/AGENTS.md` for a Next.js breaking-changes warning; that file never existed anywhere in the repo's history, confirmed via `git log --all`. Reworded to state the fact directly instead of citing a file that isn't there
- [x] Add `.kiro/steering/agents-md.md` (always-included) that inlines `AGENTS.md` via Kiro's live file-reference syntax, so Kiro's own steering surface carries the same content without a second copy to keep in sync
- [x] Add `.cursor/rules/agents-md.mdc` (`alwaysApply: true`) that references `AGENTS.md` the same way, using Cursor's `@file` reference syntax

## Story 42: Explicit database split

Formalizes the boundary between the core transactional database and story 33's separate analytics/event store as its own architectural decision, rather than leaving it implicit in story 33's provisioning task alone. Story 33 still owns picking the actual analytics store; this defines which data belongs on which side of the line, and why.

Checked against real code and the current docs: `ARCHITECTURE.md`'s Database domain boundary section already states the boundary this story's first task calls for, word for word, including "no entity is planned to live in both, or move between them." Not blocked on anything, the one real remaining gap is that stories 33 and 34 don't cross-reference this story yet.

- [x] Document, in `ARCHITECTURE.md`'s Database section, the explicit domain boundary: every entity either service reads or writes today, users, groups, sessions, rounds, guesses, songs, playlists, and pgvector embeddings, stays in the transactional Postgres+pgvector instance; only story 33's append-heavy usage/event data goes in the separate analytics store. Already present in `ARCHITECTURE.md`'s Database domain boundary section
- [x] Confirm no entity currently planned for either service needs to live in both places or move between them; already stated as true in `ARCHITECTURE.md`, re-check and note it here if one turns up during story 33 or 34's actual implementation. Re-checked against story 33's actual implementation (`feature/analytics-data-store`): the analytics store holds a single `analytics_events` table (event type, timestamp, JSONB payload), no transactional entity is duplicated or moved into it, still holds
- [x] Cross-reference this story from stories 33 and 34 so the boundary isn't restated inconsistently in three places

## Story 43: Metadata minimization

A cross-cutting principle rather than a single implementation: curb `metadataRaw`'s growth so it doesn't bloat the database. Story 40 already flags this as a real constraint (Wikidata's own entity dumps ran tens of KB per song during the sourcing spike; at that size the 500MB Supabase free-tier cap holds roughly 10,000-50,000 songs instead of 170,000+ with a curated version), and story 23 already decides the fix (`metadataRaw` persists the curated, actually-used subset of each source's response, not the full raw API response). This story applies that same rule everywhere raw pipeline output gets persisted, not just at those two stories' specific call sites.

Checked against real code: `metadataRaw` now exists on `Song` (story 23 landed), but no code anywhere in the backend or AI microservice ever sets it, no `setMetadataRaw` call exists in the codebase. `ARCHITECTURE.md`'s Song and playlist database section already documents the curation rule as a standing constraint. Not blocked on anything.

- [x] Audit every place raw source or pipeline output is persisted (`metadataRaw` on `Song`, any raw YouTube API Data fields) against the curated-subset rule already decided in stories 23 and 40, confirm nothing outside those two stories ends up persisting an uncurated raw response. Re-audited now that story 23 has landed: `metadataRaw` exists as a column but nothing writes to it yet, that's story 40's scope; still confirmed clean
- [x] Document the curation rule in `ARCHITECTURE.md` as a standing constraint on any future field that persists external API output, not just `metadataRaw`. Already present in `ARCHITECTURE.md`'s Song and playlist database section
- [x] Coordinate with story 40's YouTube-API-Data 30-day refresh/delete requirement: both are limits on the same field, one on size, one on retention. Cross-referenced from story 40's own task now

## Story 44: Test user infrastructure (dev only)

A dedicated `Role` for automated test/QA agents, separate from `USER` and story 40's `ADMIN`. Exists so automated agents, this project's own AI-assisted development workflow included, reuse one seeded test account's credentials across runs instead of registering a fresh throwaway account every time. Coordinates with story 22 (test coverage): this is test infrastructure, not test coverage itself.

Checked against real code: `Role.java` declares only `USER` today, confirming the first task below. No environment/profile mechanism exists anywhere in the backend, no `@Profile` annotation and no `spring.profiles.active` configuration anywhere, so "Production environment" isn't yet a concept the code can gate on; establishing that distinction is this story's own scope to build, not a dependency on another story. No root `CONTRIBUTING.md` exists yet either (story 36), the documentation task below already anticipates that with its dev-setup-doc fallback. Not blocked on anything else.

- [x] Add a `TEST` value to `User.role`, alongside the existing `USER` and (once story 40 lands) `ADMIN`
- [x] Add a fixture or seed mechanism that creates one reusable test account with known credentials in local/dev environments, rather than a new account per test run
- [x] Document, in `CONTRIBUTING.md` (story 36) or a dev-setup doc, that agents and contributors running tests locally reuse the seeded test account's credentials instead of registering new ones
- [x] Add an environment/profile mechanism distinguishing a Production deployment from local/dev, none exists today, no `@Profile` or `spring.profiles.active` usage anywhere in the backend; every guardrail below depends on this existing
- [x] Add an environment guardrail: any `TEST`-role account, and any endpoint or behavior gated on that role, is a no-op or outright rejected when running against a Production environment, even if a `TEST`-role row somehow exists there
- [x] Add a startup or CI check that fails loudly if a `TEST`-role row is ever found in a Production database, rather than silently ignoring it

Tests:
- [x] Unit test confirming `TEST`-role behavior is disabled under a Production environment flag, including the case where a `TEST` row actually exists
- [x] Unit test for the seed/fixture mechanism producing the same reusable credentials across repeated runs

## Story 36: Open-source collaboration readiness

- [x] Add `CONTRIBUTING.md`: local dev setup (`make dev`), the branch/PR workflow already defined in `CLAUDE.md` written for an external audience, how to pick up a story from `TASKS.md`
- [x] Add `LICENSE`: MIT, chosen over AGPLv3/BSL since there's no revenue or scale to protect, and MIT is the stronger signal for a portfolio project, zero friction for anyone evaluating the code
- [x] Add `CODE_OF_CONDUCT.md`
- [x] Add GitHub issue templates (bug report, feature request) and a PR template matching the repo's actual PR description style (plain prose, no `## Summary`/`## Test plan`, see `CLAUDE.md`'s writing-style rules)
- [x] Document which secrets a new contributor needs (`YOUTUBE_API_KEY`, `OPENAI_API_KEY`, `INTERNAL_SERVICE_API_KEY`) and how they get sandbox-safe values, since both external API keys carry real cost/quota implications

## Story 27: Rate limiting

Checked against real code: the only rate limiting anywhere is `SongMetadataService`'s single in-flight-request-per-user gate on `/api/metadata/song`, a `ConcurrentHashMap`-backed set, not a time-window limiter. No rate-limiting library (Bucket4j, resilience4j) exists in `pom.xml`. `/auth/login` and `/auth/register` have no rate limiting at all today.

- [x] Add a rate-limiting library (Bucket4j is the standard Spring choice) to `pom.xml`
- [x] Add per-user or per-IP request-window rate limits across public-facing endpoints, not just the existing single in-flight gate
- [x] Rate-limit `/auth/login` and `/auth/register` specifically, to blunt credential-stuffing and enumeration attempts
- [x] Standardize the 429 response shape; the metadata endpoint's current 429 uses Spring's default `ProblemDetail`, not the app's own `ErrorResponse` record used elsewhere in `GlobalExceptionHandler`
- [x] Rate-limit the AI microservice's `/metadata/resolve` endpoint directly, not just the core service's call into it, since anything holding the shared `X-Internal-Api-Key` secret can call it directly
- [x] Load-test every rate-limited entry point (both services) under concurrent traffic past the configured limit, confirming the limiter holds under real concurrency rather than only the single-threaded unit tests below

Tests:
- [x] Unit tests for the rate limiter: requests under the limit pass, requests over the limit get rejected, including the boundary value
- [x] Integration test: `/auth/login` and `/auth/register` rate limiting specifically
- [x] Integration test: the AI microservice's `/metadata/resolve` rate limit triggers independent of the core service's own limiting

## Story 33: Analytics data store

Story 42 owns the explicit domain boundary this story's provisioning assumes: every transactional entity stays in the core Postgres+pgvector instance, only this story's usage/event data goes in the separate store it provisions below.

- [x] Choose and provision a separate append-heavy store for usage/event data, apart from the transactional Postgres database (a separate schema, or a dedicated event/time-series store). A second Postgres database, `analytics-db` in `docker-compose.yml`, migrated through its own Flyway history under `db/analytics-migration`, independent of the core service's V1-V10 history
- [x] Define the event schema: game session start/end (with a compact per-game summary, group, players, win/loss, cards won, final score, for story 34's game history feature), login, playlist created, song submitted, rate-limit-exceeded (user, endpoint), report submitted, failed login attempt. A single `analytics_events` table (event type, timestamp, JSONB payload) plus a typed payload record per event, in `backend/src/main/java/org/dariusturcu/backend/analytics`
- [x] Decide a retention policy. 180 days, configurable through `analytics.retention.days`

Tests:
- [x] Integration test: an event write to the new store doesn't touch or block the transactional database
- [x] Integration test for the retention policy's cleanup logic

## Docs: fix status inconsistencies (docs/fix-status-inconsistencies)

An audit found several docs describe already-merged work as still pending. Batches 20 (story 18), 21 (story 40), 22 (story 41), 23 (story 24), and 24 (story 17) merged to `dev`; story 26 is dropped. The four metadata sources, the `@Scheduled` sweepers, the game/group/report entities, `Role.ADMIN`, and Flyway through V13 all exist in the real code. This is docs-only cleanup against that ground truth.

- [x] `PROJECT_STATE.md`: stories 18, 40, 41 move from Needs Definition to Implemented; drop story 41's stale metadata-sourcing-spike blocker clause; story 17 is Implemented (PR #100 merged)
- [x] `ROADMAP.md`: check off batches 20, 21, 22, 23 and drop their "tasks need confirming" caveats; remove stories 18, 40, 41, 24 from the active Phase 2 list; mark batch 30 (story 26 cache) dropped; mark Phase 0 shipped and stop describing the sources as still to build; drop the "needs confirming" tail on batch 26 (story 9)
- [x] `ARCHITECTURE.md`: change the story 18 lock-before-LLM line from decided-not-implemented to implemented; drop stories 18, 24, 40, 41 and story 26 from "Not yet built", keeping 9, 12, 13; add the story 41 content-safety gate to the metadata pipeline description
- [x] `SYSTEM_REFERENCE.md`: expand the entity list to include the game/group/report entities plus `AlternateYoutubeId` and `PendingImport`; drop those and `ADMIN` from "Planned (not yet code)", leaving `ChatMessage` and `SongDifficulty`; set `User.role` to (USER, TEST, ADMIN); add the live group, session, admin, and report controllers to the API table; note migrations reach V13 on `dev`
- [x] `TASKS.md`: correct story 40's false "No `@Scheduled` usage" intro claim; correct story 18's "lock-evaluation logic itself still hasn't happened" intro claim
- [x] `DECISIONS.md`: append one dated entry recording that the verified-promotion criteria and build-into-real-microservice open items are resolved (append-only, existing entries untouched)

## Chore: Playwright end-to-end tooling

Story 22 owns the frontend's broad test suite, still unbuilt. Ahead of that, real-time multiplayer behavior (WebSocket-synced game sessions, group lobbies) needs genuinely separate, simultaneously-authenticated browser sessions to test at all, not one tab reused for multiple roles. This adds the tool and one proof that the mechanism works, not the broad suite itself.

- [x] Add `@playwright/test` as a frontend dev dependency and `playwright.config.ts` (chromium only, `http://localhost:3000`)
- [x] Add `frontend/e2e/three-test-accounts-login.spec.ts`: three isolated `browser.newContext()` sessions log in as the three seeded TEST-role accounts and each lands on `/playlists`, proving the pattern a future DJ/active-player/other-player game-session test builds on
- [x] Add an `e2e` script to `frontend/package.json`
- [x] Document running the suite in `docs/DEV_SETUP.md`

## Bug fixes

No story required for these. Fix on a `fix` branch.

- [x] `SongMetadataResponse` (Java) silently drops the AI microservice's `confidence`, `source`, and `reasoning` fields: `SongMetadataResult` (Python) computes and returns all three today, but the Java record deserializing that response only declares `title/artist/releaseYear/gradientColor1/gradientColor2`, so the other three are read off the wire and discarded on every metadata call. Extend the record to keep them.
- [x] `DELETE /me` (`UserService.deleteUser()`) throws an unhandled `DataIntegrityViolationException` for any user who has ever added a song: `Song.addedBy` (`Song.java:41-43`) is a non-nullable `@ManyToOne` with no inverse mapping on `User` and no cascade rule, so the FK Hibernate generates under `ddl-auto=update` has no `ON DELETE` clause. It also orphans a playlist when the deleting user is its last remaining member: unlike `leavePlaylist()` (`UserService.java`), which deletes a playlist once `getUserCount() == 0`, `deleteUser()` has no equivalent check. Fixed: `Song.addedBy` is now nullable and cleared rather than blocking deletion (`V11__make_song_added_by_nullable.sql`), and `deleteUser()` applies `leavePlaylist()`'s own departure logic per playlist. See `DECISIONS.md`.
- [x] `proxy.ts` gated every protected-route navigation on the presence of the `access_token` cookie alone, a 15-minute lifetime (`jwt.expiration=900000` in `application.properties`), instead of the 7-day `refresh_token` cookie. An idle user whose access token had expired got redirected straight to `/login` on their next navigation, before `axios-instance.ts`'s response interceptor ever got a chance to run and silently reissue a new access token through `/auth/refresh`, even though a valid refresh token still existed. `refresh_token` itself is scoped to `Path=/auth/refresh` on the backend, invisible to the middleware on ordinary route requests, so gating directly on it would have redirected every navigation regardless of session state. `CookieUtil` now also issues a `session_hint` cookie at `Path=/` mirroring the refresh token's lifetime, carrying no credential value of its own; `proxy.ts` gates on that instead, and the client-side interceptor still performs the actual reissue.
- [x] `frontend/components/nav-user.tsx` renders a generic shadcn `DropdownMenu` (avatar/username/email header, a Light Mode/Dark Mode toggle item, a Log out item) where `AppShellDark.dc.html`/`Light.dc.html` show a distinct profile card: avatar, the account name in Bungee, a "View profile" pill, and a settings gear plus a log-out icon on the same row. The theme toggle inside it duplicates the rail's own dedicated toggle in `app-sidebar.tsx`. Fixed: the card now matches the mockup and the duplicate toggle is gone. The same pass also found and fixed a related contrast bug: `app-sidebar.tsx`'s active rail icon rendered in the inactive muted color because `text-icon-muted` lived in the base classes shared with the active state, and Tailwind's stylesheet order let it win over the active state's own `text-accent-foreground`.
- [x] `frontend/app/(app)/playlists/[playlistId]/PlaylistContent.tsx`'s pencil icon opens an inline rename-and-recolor editor with a six-swatch color picker. `EditPlaylistDark.dc.html`/`Light.dc.html` spec a separate, not-yet-built route for this (see `FRONTEND_IMPLEMENTATION_GUIDE.md`'s mapping table). Fixed: the inline editor, its state, and the swatch picker are removed entirely; the pencil icon now renders inert, matching the existing "Start session" button's pattern.
- [x] `frontend/app/(app)/playlists/page.tsx`'s "New playlist" empty-state card and the Owned/Joined filter pills render without a pointer cursor, meaning they're built as non-interactive elements rather than buttons. Fixed: all three now show a pointer cursor.
- [x] Pasting a YouTube link into the add-song flow (`NewSongLinkStep.tsx`/`NewSongReviewStep.tsx`) lands on the review step's needs-review state without a real metadata lookup happening against the AI microservice's pipeline (`ai/app/metadata/router.py`/`service.py`) or its backend proxy controller. Two compounding causes: `AddSongForm.tsx`'s fetch handler called `setMode("new-review")` unconditionally in a `finally` block regardless of the AI response's actual status, hiding every REJECTED or ERROR outcome behind the same blank needs-review card a real fetch would produce; and `AiServiceConfig`'s `RestClient` used the JDK HTTP client's default HTTP/2 preference, which attempts a cleartext upgrade the AI microservice's HTTP/1.1-only ASGI server rejects outright, failing every metadata call before FastAPI ever saw the request. Both are fixed: the client is pinned to HTTP/1.1, and the review step is only reached on a real SUCCESS response, with REJECTED and ERROR surfaced as a retryable error on the link step.
- [x] `frontend/app/(app)/playlists/[playlistId]/songs/add/SongSearchStep.tsx`, the add-song flow's first step, has no back-to-playlist affordance, unlike the review step's existing "Back to search" link. Fixed: it now has an equivalent "Back to playlist" link.

## Story 45: Import songs from an existing playlist

Surfaced during story 28's design pass, not part of the original backlog mapping. Playlist detail already offers two ways to add content: search-and-add from the catalog (story 14) and importing a whole YouTube playlist (story 40's user-facing bulk import). This is a third, distinct path: copying songs directly from a playlist the player already has access to, owned, a member of, or published publicly, straight into the playlist they're editing. No metadata pipeline involvement, every song is already a resolved `Song` row, so the copy is instant rather than a fetch-and-verify flow.

No longer blocked: story 15 has landed the `song_playlists` join table a `Song` needed to attach to more than one playlist. This story is mostly wiring on top of it: pick a source playlist, link its songs' existing rows into the target playlist via the same join table.

Confirmed against the real code before starting. The `song_playlists` join table is the owning side of `Playlist.songs`, linked through `Playlist.addSong` and unlinked through `Playlist.removeSong`; there is no ordering column or added-by column on the join itself. Ordering is `@OrderBy("id ASC")` on the collection, and added-by lives on the `Song` row, set once at song creation, so linking a song into another playlist neither reorders nor reassigns it. Access is enforced by `PlaylistAccessService`: `requireRead` and `requireWrite` pass for the owner, otherwise check the member's `canRead`/`canWrite` grant. `AccessDeniedException` from those checks maps to a 403 at the HTTP boundary through Spring Security. No new schema is needed: the story reuses the existing join table, so no migration ships with it (latest on `dev` stays V13).

Correction to the draft: `Playlist` has no `isPublic` field today. Public playlists are story 30, which is not built, so the public-source path can't be enforced yet. Source readability is owner-or-member through `requireRead`, matching every other playlist read in the codebase. The public-source path and its test are deferred to story 30, called out below.

Story 30 has since added `isPublic` as its own narrow slice. The deferred source-read extension below is implemented by extending `PlaylistAccessService.requireRead` itself, the same check this story's import already calls: a public playlist now passes `requireRead` for any authenticated user, owner and member or not.

- [x] Add an endpoint accepting a source playlist ID and a target playlist ID, validating the requester can read the source (owner or member through `requireRead`) and can write to the target (`requireWrite`). `POST /api/playlists/{playlistId}/imports`, body carries the source playlist ID; the path playlist is the target
- [x] Link every song from the source playlist into the target playlist via story 15's join table; skip songs already present in the target rather than erroring or duplicating the link. Returns how many songs were linked and how many were skipped as already present
- [x] Deferred to story 30: extend the source read check to accept a public source the requester neither owns nor is a member of, once `isPublic` exists on `Playlist` (implemented by extending `PlaylistAccessService.requireRead` to pass for any public playlist, which `PlaylistImportService` already calls for the source check)
- [x] Story 28: the frontend picker, choose a playlist from owned/joined/public, show a confirm step naming how many songs will be added (and how many are already present and will be skipped)
- [x] Since the copy is synchronous and immediate, no background-job or progress-tracking UI is needed for this path specifically, unlike story 40's YouTube-crawl import

Tests:
- [x] Unit/integration tests for the access check: a source playlist the requester can't read (not owned, not a member) is rejected; a requester without write access to the target is rejected
- [x] Integration test: importing from a playlist with overlapping songs only links the ones not already in the target
- [x] Integration test: importing all of a source playlist's songs into an empty target links every one
- [x] Integration test: importing from an empty source links nothing
- [x] Deferred to story 30: integration test that importing from a public playlist the requester neither owns nor is a member of succeeds

## Bug fixes

No story required for these. Fix on a `fix` branch.

- [x] No endpoint returned a playlist's details for an invite code without the caller already being a member, so a join-by-invite screen has nothing to preview before the visitor accepts. `PlaylistController` now exposes `GET /api/playlists/invites/{inviteCode}/preview`, permitted without authentication, returning the playlist's name, color, song count, and members through a new `PlaylistInvitePreviewDTO`; an unknown invite code produces a 404. `frontend/app/(app)/playlists/join/[inviteCode]/page.tsx` on this branch still auto-joins immediately with a loading spinner, no preview card, no per-playlist display name/avatar step; wiring this endpoint into an actual preview UI depends on the visual redesign in progress on a separate branch, which already builds the avatar/member-avatar-stack components this would need.

## Batch C: Song review and catalog search wiring

`ROADMAP.md`'s Batch C scope: wire `AddSongForm.tsx` and the song list to story 14's catalog search endpoint, and add the report button and thumbs-up confirmation affordances from story 17. Checked against real code: `AddSongForm.tsx` already searches the catalog through `SongSearchStep.tsx` and `useSearchSongs`, and `SongReadOnlyView.tsx` already carries the report button and thumbs-up confirmation. The remaining gap is the song list's empty state, which only linked out to the add-song page instead of offering catalog search directly. `docs/ROADMAP.md`'s wording names a generic `DataTable` component that doesn't exist in the current song list (`PlaylistContent.tsx`'s `SongRow`-based list predates that abstraction); this batch adds the missing wiring against the current list structure rather than introducing a new table component.

- [x] Add inline catalog search to the song list's empty state (`SongCatalogQuickAdd.tsx`), reusing `useSearchSongs` and `useCreateSong` so a song can be added without leaving the playlist page
- [x] Extract the catalog-search-result-to-`CreateSongRequest` mapping shared by `AddSongForm.tsx` and `SongCatalogQuickAdd.tsx` into `songCatalogRequest.ts`, rather than duplicating it
- [x] Set up Vitest and Testing Library (jsdom environment, `@/` alias, jest-dom matchers) as the frontend's first unit test infrastructure, since this batch is the first to need frontend tests

Tests:
- [x] Frontend test: the confirm affordance shows only for a `NEEDS_REVIEW` song and submits a confirmation on click (`SongReadOnlyView.test.tsx`)
- [x] Frontend test: the song list's empty-state catalog search calls the search endpoint and adds a matching result with its data (`SongCatalogQuickAdd.test.tsx`)

## Lobby and voice fix pass

- [x] Refresh active group membership after group creation, departure, and game-session changes
- [x] Keep administrator controls loading-safe and make realtime status visually distinct from controls
- [x] Improve chat input spacing and replace the native playlist picker with keyboard-accessible playlist choices
- [x] Run frontend checks

Tests:

- [x] Add or update focused realtime and lobby component coverage

## Metadata user-attention and persistence fix pass

- [x] Derive non-admin user attention from verification status and normalized confidence
- [x] Preserve trusted metadata preview verification data only when a submitted song still matches that preview

Tests:

- [x] Add backend and frontend coverage for attention and metadata persistence

## Metadata pipeline accuracy fix pass

- [x] Strip featured-artist annotations from titles used only for structured-source and Wikipedia queries, preserving the displayed and persisted title
- [x] Normalize LLM confidence values to the lowercase `low`, `medium`, or `high` set at the schema boundary
- [x] Lock as verified after Wikipedia when at least three candidate source years agree, or when at least three non-null candidates all fall within a one-year range
- [x] Preserve truthful lock provenance for Wikipedia-assisted and clustered-source locks without invoking four-source reconciliation
- [x] Add metadata unit tests for query-title cleanup, confidence normalization, Wikipedia-assisted agreement, and year clustering

Tests:

- [x] Run the AI metadata test suite

## Batch F accessibility and component boundary

- [x] Keep shadcn primitives as the accessible interaction boundary and apply the project visual system through composition and tokens
- [x] Improve focus-visible states and readable translucent surfaces in the chat overlay and voice sidebar
- [x] Verify the affected interactive controls with frontend lint and a production build

## App-wide polish fix pass

- [x] Center empty playlist states in the available page area
- [x] Replace the JavaScript logo height loop with composited CSS animation and make the sidebar logo a dashboard link
- [x] Redirect authenticated visitors from the marketing root to the playlist dashboard

Tests:

- [x] Run frontend lint and production build

## Playlist cover and color regression fix pass

- [x] Make playlist cover mosaics responsive squares and merge caller classes with `cn`
- [x] Use the six approved playlist title colors for client defaults and server-side updates
- [x] Replace the black newly-created playlist color with the approved default
- [x] Add focused frontend and backend validation coverage

Tests:

- [x] Run frontend checks and relevant backend tests

## Saved playlists missing from the library fix

Explore's Save button writes a `SavedPlaylist` row, but the Your playlists page only reads the membership-based user library, so a saved playlist appears nowhere. Saving is deliberately distinct from membership, so the library needs its own surface for saved items.

- [x] Add a Saved tab to the Your playlists page backed by `GET /api/users/me/saved-playlists`, reusing the existing card grid
- [x] Refresh the saved-playlists query when a save succeeds from Explore, so the tab is current even if it was loaded before

Tests:

- [x] Frontend test: the Saved tab lists the current user's saved playlists and the Owned/Joined filtering still works
- [x] Run the frontend checks for the touched pages

## Landing and auth fix pass

Covered by PR #172 (plus the OAuth offline tests beside it).

- [x] Unauthenticated users see the landing page instead of a login redirect
- [x] Bound landing and auth page scroll to their content
- [x] Fix the theme toggles and the logo animation on landing and auth
- [x] Verify the join-link flow for logged-out users: login or account creation, then automatic redirect into the join prompt
- [x] Verify remember-me exists and works; wire the forgot-password form if it is still a stub
- [x] Verify whether OAuth can be tested without real Google credentials, and test it if so

Tests:

- [x] Frontend tests for the landing redirect, scroll bounds, toggle, and animation states
- [x] Playwright coverage for account creation, login, and the join-link redirect chain
- [x] Live email-flow check through the backend's log-line mode (no inbox needed while the Resend key is unset)

## Story 22: Test coverage

Checked against real code: the backend has exactly one test file, an empty `contextLoads()` smoke test, zero controller/service/security coverage. The AI microservice has unit tests only for pure functions (`llm.synthesize`, `prompt.build`, `sources/util.py` helpers), nothing for `router.py`, `service.py`'s orchestration, or `auth.py`. The frontend has no test runner installed at all. `.github/workflows/pr-checks.yml` runs `mvnw compile` and `npm run lint && npm run build`, no test execution step for either service, and no job at all for the AI microservice, so even its existing pytest tests never run in CI today.

- [x] Add a CI job for the AI microservice (none exists today) running its existing `pytest` suite
- [x] Add a `mvnw test` step to the backend CI job (currently compile-only)
- [x] Add JUnit/Mockito tests for every backend service (`PlaylistService`, `SongMetadataService`, `UserService`, `AuthService`, `ExportService`), covering the access-control checks in `PlaylistService`, the rate limiter in `SongMetadataService`, and the account-enumeration-avoidance logic in `AuthService` (all five services carry suites including access control, refresh rotation, and export rendering; backfilled on the story-22 coverage branch)
- [x] Add `@WebMvcTest`/MockMvc tests for every controller (controllers carry unit or MockMvc coverage, with MockMvc slices against the real security configuration for auth flows, CSRF posture, and rate limits)
- [x] Add a Spring Security test covering JWT auth, refresh-token rotation, and CSRF (handshake auth, rotation with old-token death and expiry, and CSRF token-pair enforcement with auth-path exemption are all covered)
- [x] Add tests for `ai/app/metadata/router.py`, `service.py`'s orchestration, and `auth.py`'s internal-key check, using FastAPI's `TestClient`
- [x] Add a frontend unit test runner (Vitest or Jest, neither installed today) plus React Testing Library, and a `test` script in `package.json` (Vitest with Testing Library and a `test` script, in place since the Batch A surface)
- [x] Add frontend unit tests for the song forms' hand-written validation (`AddSongForm.tsx`, `SongForm.tsx`) and the auth forms
- [x] Add Playwright for frontend integration/end-to-end tests, none exist today; separate from the unit test runner above, drives the real browser against the real backend rather than mocking it (see this file's Chore: Playwright end-to-end tooling section)
- [x] Add Playwright coverage for the core flows that exist today: login/register, playlist CRUD, song add/edit, export (covered by the login, lobby, gameplay, results, rounds-and-import, and core-flows specs)
- [x] Add the new test steps to `.github/workflows/pr-checks.yml` for all three services
## Story 48: Comment cleanup

`AGENTS.md`'s code conventions already state the rule this story enforces: write as few comments as possible, only when the reasoning genuinely can't be inferred from the code, none that restate what the line already says, none that narrate a specific example instead of the general rule. AI-assisted batches built across this project have drifted from that rule in places, leaving comments that re-explain what adjacent code already makes obvious, or that narrate a past version's reasoning instead of documenting the code as it stands.

- [x] Audit every comment in `backend/src/main`, `ai/app`, and `frontend/app`/`frontend/components` against `AGENTS.md`'s comment rule; remove any that restate the line below it, shorten any that are longer than the invariant they document actually requires (audited on the story-48 cleanup branch: two stale comments fixed, everything else already compliant from enforcement at write time)
- [x] Remove or rewrite any comment that narrates a specific past decision, ticket, or debugging step instead of stating the current invariant as fact; that history belongs in `DECISIONS.md` and commit messages, not in code (none found beyond the two stale wordings above)
- [x] Leave in place, and don't shorten past the point of losing the actual reasoning, comments documenting a genuinely non-obvious constraint (a hidden ordering dependency, a workaround for a specific external API's behavior, a security-relevant invariant) (kept: broker prefixes, JWT handshake pattern, scheduler threading, rate-limit buckets, mosaic fallbacks)
- [x] Spot-check `DECISIONS.md` for the same drift, an entry that restates a decision already stated earlier in the same entry rather than adding new reasoning; `DECISIONS.md` stays append-only, so this means catching it going forward in new entries, not rewriting past ones (new entries from this session read clean)

Tests:
- [x] None; this story changes comments only, no behavior. Run each service's existing test suite once after the pass to confirm nothing was accidentally deleted along with a comment (a comment removal that took its statement's closing brace or trailing code with it) (backend suite green after the pass; no frontend files touched)
## Story 49: Naming consistency

The project's real name is `hittiguess`. Earlier working names (`Hitster`, `My Hitster`, `HitGuessr`) still appear in a handful of places that were never updated after the rename. A reference to the actual Hitster board game as the product's inspiration, in `README.md` and the landing page copy, is correct as written and stays.

Checked against real code, every remaining old-name occurrence:

- [x] `backend/docker-compose.yml`: rename the `my-hitster-postgres` container and the `hitster_postgres_data` volume (renamed all four identifiers including the analytics pair; no other file references them)
- [x] `backend/src/main/java/org/dariusturcu/backend/config/SecurityConfig.java`: update the hardcoded `https://my-hitster.dariusturcu22.com` allowed CORS origin (already env-driven via `FRONTEND_ALLOWED_ORIGINS`, no hardcode left; the production-domain default value stays as live infrastructure)
- [x] `backend/src/main/java/org/dariusturcu/backend/websocket/WebSocketConfig.java`: update the same hardcoded `https://my-hitster.dariusturcu22.com` allowed origin (already env-driven, same as above)
- [x] `ai/app/main.py`: rename the FastAPI app's `title` from `"hitguessr AI microservice"` (plus the same leftover in `ai/pyproject.toml`'s description)
- [x] `frontend/components/app-sidebar.tsx` and `frontend/components/logo.tsx`: rename the displayed `"My Hitster"` brand text (already `hittiguess` wordmark and icon-only sidebar, nothing to change)
- [x] `frontend/orval.config.ts`: rename the `myHitster` and `myHitsterZod` generator config keys (renamed; regen output verified byte-identical apart from the banner)
- [x] Re-run the same search across the codebase once the above land, to catch anything this pass missed (generated API client output, environment variable names, deployment config) (re-ran: only the task text itself, the live production-domain default, and the legitimate board-game reference remain)

Tests:
- [x] Confirm the existing CORS-related backend tests still pass after the `SecurityConfig`/`WebSocketConfig` origin rename (origins are env-driven; the CORS tests pass with neutral example origins)
## Playlist detail and edit fix pass

- [x] Add owner-only playlist deletion and wire the edit-page confirmation flow
- [x] Redirect to the playlist detail page after save succeeds
- [x] Expose playlist ownership in the user library and make Owned and Joined filtering work
- [x] Replace the expanded member list with an accessible collapsed member control
- [x] Replace bulk-import placeholder progress with the existing real-time progress stream (replaced by the background import jobs with sidebar progress, greyed pending songs, and live progress events)

Tests:

- [x] Add backend deletion coverage and run backend and frontend checks

## Fix: Export paper sizes, combined info+QR output, and playlist description backend

Closes out the two items the LAN playtest findings above left open pending backend support. No story gate: both are bug-fix-shaped backend/frontend slices tracked directly in `TASKS.md`, not new feature scope.

- [x] Extend `PaperSize` with `LEGAL` (8.5x14in), `A3` (297x420mm), `A5` (148x210mm), and `TABLOID` (11x17in) at the existing 300dpi convention, alongside `A4`/`LETTER`
- [x] Add a combined info+QR card face: each card shows its info (artist/year/title) on the same face as a small QR code, a single-sided alternative to the existing double-sided info/QR pair, since duplex printing isn't built
- [x] Add `GET /api/playlists/{playlistId}/export/combined` (`ExportController`/`ExportService`), same `paperSize` query param and access check as `/export/info` and `/export/qr`
- [x] Add a `description` column to `Playlist` (migration, nullable, `VARCHAR(300)` matching `Playlist.MAX_DESCRIPTION_LENGTH`, the same constant `UpdatePlaylistRequest`'s validation bounds against)
- [x] Add `description` to `UpdatePlaylistRequest` (bounded length, matching the field's existing textarea) and `PlaylistDetailDTO` (not `PlaylistSummaryDTO`, no mockup shows a description on the playlist grid cards), and wire it through `PlaylistMapper`/`PlaylistService.updatePlaylist`
- [x] Frontend: hydrate the edit page's description draft from the playlist, include it in the save call, and save whenever either the name or the description actually changed, not just the name
- [x] Frontend: add the new paper sizes and the combined content option to the export dialog's selects
- [x] Regenerate the orval API client against the updated OpenAPI schema

Tests:
- [x] Unit tests for the new `PaperSize` values' page/margin math, same shape as the existing `A4`/`LETTER` coverage
- [x] Combined-page rendering is exercised through `ExportServiceTest`'s new combined-PDF tests, matching how `CardGenerator`/`QRGenerator` already have no direct unit tests of their own and are only exercised through `ExportServiceTest`
- [x] Unit test: the combined export checks read access the same way `ExportServiceTest`'s existing info/QR tests do, no separate controller-level export test existed to extend
- [x] Unit tests for the description update: within the length bound saves, over it is rejected, a non-owner update is rejected the same as the existing name/color update
- [x] Frontend test: the edit page saves a description-only change (no name change) and hydrates the existing description into the draft on load

## LAN playtest findings, batch 2 (export dialog, fetch reliability)


- [x] Export opens as a dialog with a song/option summary instead of an inline section; combined info+QR output and extra paper sizes need backend support and land separately (download and print verified working on LAN, the failures were stale-bundle)
- [x] Add-page 500 on render from a missing server snapshot in the queue hook (same latent pattern as the voice hooks)
- [x] Add-by-link fetch verified end to end on LAN (~35s to review); the earlier failures were the AI service being down plus the 500 above

Tests:
- [x] Playlist content, import, and queue-hook suites stay green; LAN browser probes for export download and fetch-to-review

## LAN playtest findings, batch 3 (lobby start flow, voice sidebar)

- [x] Voice sidebar: no collapse control, hidden outside calls except on the lobby page, slim rail matching the left sidebar with the join action on top
- [x] Playlist chip opens a tier popup (easy/medium/hard/custom) below it; custom opens a fullscreen multi-playlist picker with a chosen list and a back path; the standalone Custom start button goes away
- [x] Two-player minimum only as a popup on Start, never persistent
- [x] Lobby content scrolls on zoom with bottom actions pinned; avatar and name float as one unit

Tests:
- [x] Lobby suite covers the tier popup, custom picker with back path, min-players popup, and the removed custom start (plus a fixed infinite loop in the playlist preselect capture)

## Chore: Documentation accuracy pass

Triggered by a Local-milestone audit: `docs/ROADMAP.md` claimed stories 30, 35, 22, 48, and 49 were still open when `PROJECT_STATE.md` and `ARCHIVE.md` already showed them shipped, and `docs/SYSTEM_REFERENCE.md`/`docs/FRONTEND_IMPLEMENTATION_GUIDE.md` had drifted behind the real schema and routes.

- [x] Correct `docs/ROADMAP.md`'s "Remaining work", "Readiness tiers", and "What's shipped" sections: stories 22, 30, 35, 48, and 49 are shipped, not remaining; story 38's usage-limit check is built; story 28's implementation now runs through Batch F; story 47's actual open items (design-mockup sync, feedback-polish batch, voice check) are named instead
- [x] Update `docs/SYSTEM_REFERENCE.md`'s migration list from V15 to V25, add the six undocumented endpoints (`import-jobs` pair, `cover`/`avatar` pairs, and the three story-30 session-generation endpoints), and correct the entity block: `Song.gradientColor1`/`gradientColor2` replaced by `color`, plus the undocumented `Song.wikidataSitelinksCount`, `Playlist.coverImage`, `User.avatarImage`, `RefreshToken.rememberMe`, `Group.joinCode`/`fixedDjMemberId`, and the `PlaylistImportJob`/`PlaylistImportJobItem` entities
- [x] Fix `docs/FRONTEND_IMPLEMENTATION_GUIDE.md`'s two wrong route paths (`/playlists/explore` to `/explore`, `/admin/catalog` to `/admin/catalog-backlog`) and its stale "TBD" import-route entry, replaced with the real implemented routes
- [x] Fix `docs/TASKS.md`'s own preamble, still citing "Batches A through E"

Tests:
- [x] None; this chore changes documentation only, no behavior. Frontend and backend suites confirmed green (modulo the sandbox's known loopback-socket limitation on backend integration tests) as part of the same audit that surfaced this drift, not re-run for this chore specifically since no code changed

## Fix: reset-password and verify-email unreachable while logged out

Surfaced during a visual verification pass against the mockups: `proxy.ts`'s `PUBLIC_ROUTES` list omitted `/reset-password` and `/verify-email`, so the middleware redirected any logged-out request to either page straight to `/login` before it ever rendered. Both pages are reached almost exclusively by a logged-out visitor clicking a link from their email, so this broke both flows entirely rather than being an edge case.

- [x] Add `/reset-password` and `/verify-email` to `proxy.ts`'s `PUBLIC_ROUTES`

Tests:
- [x] Unit tests for `proxy.ts` (none existed before): every public route (including the two fixed here) passes through for a logged-out request, a protected route redirects a logged-out request to `/login`, a protected route passes through once `session_hint` is present

## Fix: leaving a group with an in-progress game session crashed, and stale e2e locators

Surfaced running the real Playwright e2e suite (`batch-e-group-lobby.spec.ts`) against a live backend and two genuinely separate sessions, per `docs/DEV_SETUP.md`. `GroupService.leaveGroup`'s last-member-deletes-the-group branch called `groupRepository.delete(group)` unconditionally, with no check for whether a `GameSession` still referenced that group. Since a group locks once a session starts and stays referenced by that session's row until it ends or auto-abandons, the last connected member explicitly leaving mid-session hit an unhandled SQL foreign key violation, surfaced to the client as a raw SQL error string in a 400 response.

- [x] `GroupService.leaveGroup`: when the group would otherwise be deleted (no members left), skip the delete if a `GameSession` still references it; the session's own 10-minute zero-connected-players auto-abandon path already purges the session and reopens the group through `recordGameSessionEnded`, so nothing else needs to change to clean it up afterward
- [x] Fix three stale locators in `batch-e-group-lobby.spec.ts`, all from UI changes the test was never updated for: `.selectOption("ROTATING")` on the DJ-mode dropdown (now a shadcn/Radix `Select`, not a native `<select>`), an ambiguous `getByRole("button", { name: "Settings" })` now also matching the popup's "Close settings" overlay button, an ambiguous `getByRole("button", { name: "Close chat" })` now matching both the chat panel's own close button and the footer toggle, and the results page's single-click download now opening a format-choice panel (PDF/text/CSV) per story 47's design instead of downloading directly

Tests:
- [x] Unit test: `GroupServiceTest`, the admin leaving alone while a `GameSession` still references the group does not delete it
- [x] `batch-e-group-lobby.spec.ts`'s real-backend lobby test (join, settings, chat, start session) and the mocked results-export test both pass end to end against a real two-session backend; the third (fully-mocked gameplay-shell) test remains flaky under back-to-back local runs sharing the same seeded accounts, unrelated to this fix, not chased further

## Fix batch: group lobby picker, chat backdrop, invite OAuth return, password toggles

Lobby playlist and difficulty picker (`frontend/app/(app)/groups/[groupId]/page.tsx`):
- [x] Custom button matches tier pills in row, styling, and aria-pressed treatment
- [x] Playlist source state tracks tier vs custom and drives the active highlight
- [x] Cards count input removed, target count computed as memberCount * winCondition * 3 clamped to minimum
- [x] Custom picker modal gets vertical margin so the backdrop stays clickable, backdrop click closes it
- [x] Playlist link input, Start button, and playlistLink state removed, startCustom usage cleared
- [x] Picker rows render compact playlist cards reusing the library cover pattern
- [x] Confirm button saves selection only, no session start

Chat overlay (`frontend/components/group-chat-overlay.tsx`):
- [x] Backdrop click closes the panel in floating and non-floating paths, Chat toggle stays usable

Invite and OAuth return path:
- [x] Login and register Google links carry validated returnTo
- [x] Backend passes returnTo through the OAuth2 request and appends it to the frontend redirect
- [x] RedirectHandler routes to validated returnTo instead of fixed /playlists
- [x] Playlist join page carries the same returnTo login link as the group join page
- [x] Logged out group join route checked for real redirect or query race vs dev only noise

Password fields:
- [x] Reusable PasswordInput wrapper with Eye and EyeOff toggle
- [x] Login, register, and reset password fields use the wrapper with existing bindings intact

Tests:
- [x] Lobby tests for custom highlight, confirm without start, and backdrop close
- [x] Chat test for click outside close
- [x] Frontend tests for returnTo on Google links and RedirectHandler routing
- [x] Backend tests for returnTo propagation through the OAuth handlers
- [x] Password toggle reveal and hide test
- [x] Frontend `npm run test -- --run`, `npm run lint`, and `npm run build` clean
- [x] Backend `./mvnw test` clean

## Fix batch 2: lobby popups, export, import, explore, invites, metadata, UI polish

Popup mutual exclusion and spacing (`frontend/app/(app)/groups/[groupId]/page.tsx`):
- [x] Opening settings, tier popup, custom picker, or chat closes the other three
- [x] Starting a session closes all four popups
- [x] Chat, settings, and tier popups share one footer gap distance

Voice mesh error text (`frontend/hooks/use-voice-mesh.ts`):
- [x] Missing mediaDevices reports an HTTPS or localhost message, not the permission one
- [x] getUserMedia catch branches on error name for distinct messages

Export redesign (backend `CardGenerator`, `ExportService`, `ExportController`, `PlaylistContent.tsx`):
- [x] Combined mode removed on backend and frontend
- [x] Single PDF interleaved duplex mode added on backend with frontend option
- [x] Export dialog pickers use shadcn Select instead of raw select
- [x] Empty playlist export shows an error toast instead of opening the dialog

Icon drag and avatar selection (global):
- [x] Icon images not natively draggable, avatar initials not text selectable

Explore listing (`PlaylistService.getPublicPlaylists`):
- [x] Owned and joined playlists excluded from explore results, saved unaffected

Invite preview error states:
- [x] Dead playlist invite renders an invalid link screen instead of the join form
- [x] Backend group invite preview endpoint mirroring the playlist one
- [x] Group join page gates on the preview error state the same way

Import progress:
- [x] YouTube import page shows inline crawling progress with per song checklist and no auto redirect
- [x] Playlist songs refetch incrementally as import items finish
- [x] Sidebar import indicator links to the YouTube import progress page
- [x] Playlist page importing banner revisited once the above land
- [x] From playlist import page spot checked against its mockup step

Members trigger (`PlaylistContent.tsx`):
- [x] Trigger renders bare avatar stack with no box or label text, popup content unchanged

Featured artist extraction (AI prompt plus Java mapping):
- [x] Prompt extracts featured credits into the artist field instead of embedding them
- [x] Java mapping splits credits into MAIN and FEATURED SongArtist rows

Report dialog validation:
- [x] Light validation pass on the report fields

Cover mosaic letterboxing (`playlist-cover-mosaic.tsx`):
- [x] Tiles scale images past baked in bars in mosaic and single cover paths

Tests:
- [x] Frontend tests for popup exclusion, export dialog, invite error states, import progress, members trigger, report validation
- [x] Backend tests for explore filtering, group invite preview, duplex interleave, artist role split
- [x] AI prompt or schema tests if the suite covers them
- [x] Frontend `npm run test -- --run`, `npm run lint`, and `npm run build` clean
- [x] Backend `./mvnw test` clean

## Fix-up batch: import resume, dedup artists, preview privacy, mosaic zoom, report year, explore query, invite exit, multi-artist

Import progress resume (`frontend/app/(app)/playlists/[playlistId]/import/youtube/page.tsx`):
- [x] Progress view initializes from an already running import on mount and reload
- [x] Transient query errors no longer render as import complete, only genuine job absence does

Dedup featured artists (`ai/app/metadata/service.py`, `ai/app/dedup/schemas.py`):
- [x] VerifiedSongMatch carries featured artists from the stored row through the fast path result

Group preview privacy (backend `GroupController`, `GroupMapper`):
- [x] Public preview uses a lean member shape with display identity only, no user ids or presence

Mosaic zoom (`frontend/components/playlist-cover-mosaic.tsx`):
- [x] Zoom applies to YouTube thumbnail tiles only, custom covers render unscaled

Report year validation (`SongReadOnlyView.tsx`):
- [x] Malformed year input fails validation instead of passing the bounds check

Explore membership check (`PlaylistService.java`):
- [x] Membership test uses the existing repository exists query instead of streaming in Java

Dead invite exit (both join pages):
- [x] Invalid screen routes logged-out users to home instead of the protected library

Multi-artist main credits (AI schemas, prompt, service, Java mapping):
- [x] Precheck and result schemas carry main artists as a list with prompt guidance
- [x] Service threads the list through verification, fallback, and dedup paths
- [x] Java records and resolution persist one MAIN row per name with sequential orders
- [x] Guess matching confirmed correct for multiple MAIN artists

Tests:
- [x] Frontend tests for import resume, error vs complete, mosaic zoom scope, report NaN year, invite exit route
- [x] Backend tests for lean preview shape and repository membership check
- [x] AI tests for dedup featured carry and main-artist list threading
- [x] Frontend `npm run test -- --run`, `npm run lint`, and `npm run build` clean
- [x] Backend `./mvnw test` clean
- [x] AI `pytest` clean

## Chore: Regenerate the frontend API client after the main/featured-artist schema change

Batch 2's fix-up work (`Carry main-artist plurality and dedup featured credits as lists`) changed `SongMetadataResponse.java`'s `artist: String` field to `mainArtists: List<String>` / `featuredArtists: List<String>`, but the frontend's orval-generated client was never regenerated to match. `frontend/hooks/models/songMetadataResponse.ts` still had the old `artist?: string` shape, and since the field is typed optional rather than removed, `npm run build` stayed green while `AddSongForm.tsx`'s manual "Add song via link" review step silently read `metadata.artist` as always-undefined, submitting a blank artist for every song added that way.

- [x] Started the backend in WSL (`ai`/`backend`/Postgres containers), ran `npm run api:gen` against its live OpenAPI spec, picking up every schema drift accumulated since the last regeneration (`SongMetadataResponse`, the new export duplex mode, group invite preview, ground-truth data, and others)
- [x] Fixed `AddSongForm.tsx` to build the artist credit from `mainArtists`/`featuredArtists`, matching the `Main & Main (feat. Featured, Featured)` convention `CardGenerator.formatArtists()` already uses on the backend
- [x] Confirmed no other frontend code referenced the removed `.artist` field (`tsc --noEmit` across the whole project was the only error, now clean)

Tests:
- [x] `tsc --noEmit` clean project-wide
- [x] Frontend `npm run test -- --run`, `npm run lint`, and `npm run build` all clean

## Bug batch: session page stale state and voice settings error feedback

- [x] Session page's link-out warning banner and bet-selection mode reset when a new round starts, instead of persisting from an earlier round
- [x] Timeline drop targets no longer stay clickable once the betting window closes
- [x] Voice settings popup surfaces a distinct message for an insecure context and for each `getUserMedia` failure, instead of testing the microphone silently

Tests:
- [x] `frontend/app/(app)/sessions/[sessionId]/page.tsx`, `frontend/components/voice-settings-popup.tsx` covered by the existing full suite (193 passed), no prior automated coverage of these two components to extend

## Bug batch: group lobby picker, mosaic zoom, and song attribution

- [x] The group lobby's custom playlist picker no longer reopens itself after being closed when reached through a `?playlist=` preselect link
- [x] The custom picker's playlist grid matches the playlist library's tiled card layout instead of a compact row list
- [x] Playlist cover mosaic tiles clip their own zoomed thumbnail instead of bleeding across the tile divider, and zoom further past YouTube's baked-in letterbox bars
- [x] Songs added through bulk import, playlist import, and the admin catalog backlog are attributed to the submitting user instead of always showing "Added by a deleted account"

Tests:
- [x] `SongResolutionServiceTest` covers attributing a new song to the given user and not reassigning an already-attributed song on reprocessing; existing `BulkImportServiceTest` and `PlaylistImportJobServiceTest` stubs updated for the new signature
- [x] `frontend/app/(app)/groups/[groupId]/page.tsx` and `frontend/components/playlist-cover-mosaic.tsx` covered by the existing full suite

## Bug batch: playlist content box/state fixes and sidebar hydration

- [x] Invite link/code copy actions show an error toast instead of a false "copied" state when the clipboard write fails
- [x] The playlist detail members trigger no longer sits inside a bordered/backgrounded box
- [x] The songs empty state is vertically centered whether or not the import banner is showing
- [x] The sidebar's active-import indicator reads localStorage after mount instead of during the initial render, removing a hydration mismatch

Tests:
- [x] `PlaylistContent.test.tsx` covers the copy-failure toast path via a rejected clipboard write and a failing execCommand fallback

## Bug fix: import-from-YouTube progress screen matches the design

- [x] AI microservice exposes a `/metadata/video-info` endpoint that batch-fetches each video's raw title and uploading channel name via YouTube's videos.list, chunked to the 50-id batch limit
- [x] `PlaylistImportJobService.startImport` captures each item's raw title and channel name at job creation time, before the metadata pipeline resolves anything
- [x] `PlaylistImportJobItemDTO` carries the raw video info plus the resolved song's title, artist credit, release year, and color once `songId` is set
- [x] The import-from-YouTube page's row list matches the design: a resolved row shows a colored music-note swatch, title, artist, release year, and a checkmark; a still-processing row shows a spinner swatch, the raw YouTube video title (italic), the uploading channel, and a "Fetching..." chip
- [x] The progress header shows the real playlist name and the submitted link, matching the design, instead of a generic heading

Tests:
- [x] `youtube.py` covers batching past 50 ids and tolerating a failed batch; a new router test file covers the `/metadata/video-info` endpoint
- [x] `PlaylistImportJobServiceTest` covers raw video info landing on new items and resolved song details appearing in the job DTO
- [x] A new `import/youtube/page.test.tsx` covers the resolved-row and still-processing-row rendering, including the raw-title fallback

## Gameplay playtest findings (round flow, DJ audio, design match)

Reported from a live playtest: audio streaming never reaches other players, "Open on YouTube" opens two tabs, the timeline sits left instead of centered and can't be scrolled, placement looks nothing like the mockups, and most gameplay screens differ from `docs/design/source/GameSession*.dc.html`. Each item below is confirmed against the running stack and the code. Rules follow `GAME_DESIGN.md` and `DECISIONS.md` where a mockup's copy predates them (the betting mockups still describe one bettor per round; betting is one bet per gap).

Round flow and session data (backend, `fix/gameplay-round-flow`):
- [x] Opening the betting window publishes no session event, so every client stays on the countdown view until the reveal. Publish a `BETTING_OPENED` event when the window opens
- [x] Reveal, scoring, and the next round all run in one call chain, so the reveal state is never visible. Hold the scored round for a fixed reveal interval before advancing, and expose when the next round starts
- [x] `RoundDTO` carries no deadlines, so no client can render the countdown or the betting timer. Add the lock-in time, betting-window end, and next-round start
- [x] `PlayerCardDTO` carries no artist or card color, so timeline cards print the title twice in one fixed palette. Add the artist credit and the song's color
- [x] The DJ view in the mockup shows the current song's card; `RoundLinkOutDTO` carries only the video. Add artist, title, year, and color for the DJ only
- [x] Spectators have no way to see the active player's drag before lock-in. Relay the active player's placement preview over the session's round topic, never persisted
- [x] Session and group broadcasts go out before their transaction commits, so a client refetching on the event can read the previous state and stay stuck on it. Broadcast after commit
- [x] Regenerate the frontend API client for the new fields

DJ link-out and audio (frontend, `fix/dj-link-out-and-audio`):
- [x] `window.open` with `noopener` returns `null` even when the tab opens, so the blocked-popup fallback also navigates the game tab to YouTube. The link-out is now a real anchor opening one new tab
- [x] Tab audio capture only starts when the DJ is already in voice, and it runs after `window.open` consumed the click's user activation, so the capture request fails. Request the capture from its own click, join the voice room for the DJ if needed, and keep the warning visible before sharing
- [x] The lock-in cutoff tears down the active player's whole voice connection. Mute only the incoming song audio for the active player until the round ends, and keep voice chat working
- [x] The voice mesh listens for `VOICE_PRESENCE_CHANGE` while the server sends `VOICE_PRESENCE_CHANGED`, so no member list ever refreshes and the offering member never learns about later joiners. Refresh the member list on the real event
- [x] Signals sent before the signaling socket connects are lost, candidates arriving before their offer are rejected, and the signaling client reconnects on every callback change. Keep one client per voice session, buffer early candidates, and retry an offer that never connects
- [x] A player without a usable microphone (plain-HTTP LAN, refused permission) can't join voice at all, so can't hear the DJ. Join as a listener instead
- [x] The join rail only shows on the lobby page, so a remote player can't join voice from the game. Show it on the game page too
- [x] Every player fetches the DJ-only link-out while the session loads, because an undefined player id equals an undefined DJ id. Require a resolved player first

Gameplay screens against the mockups (frontend, `fix/gameplay-screens-match-design`):
- [x] Round intro: first-round countdown with the first DJ and first turn, per `GameSessionRoundIntro*`
- [x] Header: round title with the per-state status line, playlist chip with name and song count read from the group's playlists, per every `GameSession*` mockup
- [x] Timeline: centered track, edge fade, working previous/next scroll, cards show artist, year, and title in the song's color
- [x] Placement: pointer drag of the mystery card, the timeline opens a dashed gap under the pointer with tilted neighbors, dropping keeps the card movable with a "Lock in answer" action, keyboard placement kept, per `GameSessionCardDragging*`, `GameSessionCardDropped*`, `GameSessionCardLocked*`
- [x] Spectator view: the active player's live drag gap, per `GameSessionCardDraggingSpectator*`
- [x] DJ view: the song card beside the "Open on YouTube to play" action and the audio-sharing warning, per `GameSessionDJ*`
- [x] Betting: countdown ring before the window, betting timer, coin dragged into a gap, placed bets shown as coins with the bettor's initial, skip action, per `GameSessionBetting*`
- [x] Reveal: the card lands in the timeline with the outcome line and a "Next turn starts in" pill with a progress bar, per `GameSessionReveal*`
- [x] Footer: DJ and turn pill, token coin stack (sitting out while it's the player's own turn)
- [x] Turn banner centered above the header instead of overlapping it; guess boxes hidden for the DJ and closed after lock-in
- [x] Fix the literal `&apos;` rendered in the countdown status line
- [x] The gameplay end-to-end specs log in through a password label that now also matches the show-password toggle, mock the link-out at a path the API never serves, and expect the next round the moment a round scores. Match the current login field, the real link-out path, and the reveal hold

Tests:
- [x] Backend: service tests for the betting-opened event, the reveal hold before the next round, the placement preview relay, and the new DTO fields
- [x] Frontend: unit tests for the link-out helper, the DJ silencing rule, listen-only joining, and the DJ share request
- [x] Frontend: unit tests for the session page's per-phase rendering and the gameplay helpers
- [x] Rendered comparison of every gameplay state against its mockup, dark and light, on a live three-player session

## Security and bug audit (2026-09)

A full-app audit of the core service, the AI microservice, and the frontend, against `dev` plus the open gameplay fix branches. The two two-factor findings are confirmed against a running stack; the rest are confirmed by reading the code unless marked unverified. Batches are ordered by severity; each is its own `fix/*` branch.

Batch 1, authentication (critical):
- [x] The two-factor pending token issued after a correct password is accepted as a full access token: `JwtUtil.validateToken` checks only the subject and expiry, and neither `JwtAuthenticationFilter` nor `StompAuthenticationChannelInterceptor` rejects the `two_factor_pending` token type. A password alone reaches every authenticated REST endpoint and WebSocket. Reject any token carrying a non-access token type everywhere a real access token is expected
- [x] `POST /auth/2fa/setup` sets `twoFactorEnabled` to false on an account that already has two-factor on, with no password or code, which bypasses the password-or-code rule on `/auth/2fa/disable`. Refuse setup while two-factor is enabled, or require the same proof `disable` does
- [x] `/auth/**` is exempt from CSRF while production cookies are `SameSite=None`, so the cookie-authenticated `/auth/2fa/setup`, `/auth/2fa/confirm`, `/auth/2fa/disable`, and `/auth/logout` accept cross-site requests. Require the CSRF token on every `/auth` endpoint that acts on an existing session
- [x] Two-factor codes can be replayed within their time step. Record the last accepted time step per user and reject a repeat
- [x] Login and two-factor verification are limited per IP only. Add a per-account failure limit with a cool-down

Batch 2, WebSocket authorization (critical):
- [x] STOMP authenticates only CONNECT; SUBSCRIBE is never authorized, so any logged-in user can subscribe to another group's chat, voice, settings, and membership topics and another session's round and ended topics by id. Authorize every SUBSCRIBE against group membership or session player membership
- [x] `VoiceSignalingController` relays every offer, answer, and ICE candidate (which carry players' IP addresses) to the whole group voice topic and relies on the client to filter by target. Deliver each signal only to its target member through a user destination
- [x] Subscribing to a session's round topic registers presence in `SessionPresenceRegistry` without checking the subscriber is a player in that session. Register only players

Batch 3, dependencies (critical):
- [x] `next` 16.3.2 is inside the range of published unauthenticated remote code execution advisories (16.0.0 to 16.3.2, including the Image Optimization API). Upgrade to a patched release
- [x] `npm audit` also reports high-severity `sharp` (libheif) and `fast-uri` advisories and a moderate `baseline-browser-mapping` one. Upgrade them
- [x] `npm audit` now also reports a critical `orval` advisory (fixed in 8.37.0, which also clears the high `js-yaml` one it pulls in), high `brace-expansion` and `browserslist` advisories, moderate `@humanfs/node`, `hono`, and `qs` ones, and a low `postcss-selector-parser` one. Upgrade them, and regenerate the API hooks with the upgraded `orval` so the generated code matches it
- [x] The AI microservice's dependencies are unpinned `>=` ranges with no lockfile. Pin them with a lockfile so builds are reproducible and auditable

Batch 4, game session reliability (critical and high):
- [x] `GameSessionService.reconnectPlayer` has no caller, so a player whose socket closes once (a reload, navigating away and back) stays `isConnected: false` with `disconnectedAt` set for good. An active player who reloads mid-turn is marked `Left` 90 seconds later while playing, and once every player has closed a socket at any point `zeroConnectedSince` sticks and the session is abandoned 10 minutes later mid-game. Call it when a player's socket subscribes to the round topic, and only mark a player disconnected once their last socket for that session closes, since each client holds several
- [x] A session starts with as few songs as players plus one, and when the queue empties `popNextSongId` throws inside the scheduled round advance: the session is never completed or abandoned and every client stays on the reveal. End the session with the current standings when no song remains, and raise the start threshold so a typical game can reach the win condition
- [x] Round timers, group lifecycle timers, player presence, and session results are held only in memory, and nothing recovers them on startup. A restart strands in-flight rounds in `COUNTDOWN`, `BETTING`, or `SCORED` and loses results. Reschedule pending round and group effects from persisted state on startup
- [x] An active player who stays connected but never places a card holds the round forever; the turn timeout only starts on disconnect. Add an idle placement timeout
- [x] `skipBetting` accepts any player, including the active player and the DJ, so one player can end everyone else's betting window. Limit it to eligible bettors, and decide whether it takes every eligible bettor or one
- [x] Every repeated correct artist or title submission increments the session-long leaderboard tallies again. Count each artist and title at most once per player per round
- [x] Guess results are never sent back, so neither the guesser's correct/incorrect animation nor the "guessed the artist" toast from the mockups can render. Deliver each guess result to its guesser, and broadcast a correct guess without the answer

Batch 5, data access and integrity (high):
- [x] `GET /api/sessions/groups/{groupId}/results` has no membership check, and group ids are sequential, so any logged-in user can read any group's results. Restrict it to the session's players
- [x] `PlaylistService.updateSong` edits the shared catalog `Song` row. Adding an existing YouTube id to any playlist links that row, so a user with write on their own playlist can change the year or title of an unverified song in every other playlist and game using it. Stop direct edits of a song linked to playlists the editor can't write, or route them through the report flow
- [x] `GET /api/playlists/{playlistId}/cover` is public, so a private playlist's cover is readable by anyone with its id. Apply the playlist read check, or confirm covers are meant to be public
- [x] Group display name and avatar URL on create and join have no length or format validation. Add bounds, and restrict avatar URLs to the schemes and hosts the app serves

Batch 6, resource abuse (high):
- [x] `PixelArtImageService` decodes an upload in full before checking its dimensions, so a small image declaring huge dimensions can exhaust memory. Read the dimensions through an `ImageReader` before decoding
- [x] `POST /api/bulk-import` takes an unbounded list of ids plus an expanded playlist and resolves every unknown one synchronously through paid OpenAI and YouTube calls, and checks write access on the target playlist only after that work. Cap the batch size, add a per-user daily quota, and check target access first
- [x] The background playlist import (`POST /api/playlists/{playlistId}/import-jobs`) resolves the same way through the same paid calls with no cap or quota, so the cap and quota also apply there, shared with the bulk import through one per-user daily count
- [x] The group join code is four letters with no join-attempt limit beyond the general request limit, and groups have no kick, so strangers can guess into open lobbies and can't be removed. Add a join-attempt limit and an admin kick
- [x] A removed member keeps receiving group topic messages on subscriptions made before the kick, and could rejoin with the same code. Close the removed user's sockets so every group subscription has to be authorized again, and refuse a removed user's rejoin for the life of the group. The lobby gets a remove control for the admin, and the removed member's page falls back to the group unavailable state

Batch 7, error handling and redirects (high and medium):
- [x] `GlobalExceptionHandler` maps every `RuntimeException` to 400 with its raw message, so Spring's `AccessDeniedException` returns 400 instead of 403, `ResponseStatusException` loses its status, and internal messages (database constraint names, PDF failures) reach clients. Map the specific exceptions to their statuses and return a generic message for everything else
- [x] The `returnTo` checks in `ReturnToOAuth2AuthorizationRequestResolver`, `OAuth2AuthenticationSuccessHandler`, and `frontend/lib/return-to.ts` reject `//` but accept `/\`, which browsers treat the same way, so a crafted login link can likely redirect off-site after sign-in (unverified). Reject backslashes and any value that resolves to another origin
- [x] Without a Resend key, verification and password-reset links are written to the application log, so anyone with log access can take over an account. Refuse to start in production without an email key, and never log the link
- [x] With the generic 500 fallback, refusals thrown as `IllegalArgumentException` (a wrong two-factor code, an empty chat message, an out-of-range win condition), an expired refresh token, two-factor confirm before setup, and an empty playlist export would all read as server errors. Map `IllegalArgumentException` to 400 and give the others their own client status
- [x] `returnTo` also accepts tabs and newlines, which browsers strip, so `/\t/host` becomes `//host`. Reject control characters on both sides

Batch 8, hardening (low):
- [x] The frontend sends no Content-Security-Policy, `frame-ancestors`, or `Referrer-Policy`. Add them in `next.config.ts`
- [x] The results CSV export writes display names without neutralizing leading `=`, `+`, `-`, or `@`, so a name can run as a spreadsheet formula. Prefix such cells
- [x] Swagger UI and `/v3/api-docs` are public in production. Disable them outside development
- [x] The AI microservice compares the internal API key with `!=` and would accept an empty header if the key were left blank, and exposes `/metrics` and the FastAPI docs unauthenticated. Use a constant-time compare, refuse to start with an empty key, and restrict or disable those routes
- [x] `GroupService.joinGroup` checks the 8-member cap without a lock, so concurrent joins can exceed it. Enforce the cap atomically
- [x] The join lock was put on the shared invite and join code finders, so the read-only invite preview ran `SELECT ... FOR UPDATE` in a read-only transaction, which Postgres refuses, and the invite link page failed. Lock through finders only the join uses
- [x] The CSP left the API origin out of `img-src`, blocking custom playlist covers and user avatars served by the API, and blocked React's development-only `eval`. Allow both
- [x] With `/metrics` gone from the AI service, the Alloy scrape in `observability/alloy/config.alloy` gets a 404 and AI metrics stop reaching Grafana. Expose the metrics again behind the internal key, or on a port only the scraper reaches, and update the scrape config

Existing test failures:
- [x] `frontend/app/(app)/groups/[groupId]/page.test.tsx` never finishes and pins a worker, which stalls `npm run test`
- [x] `CatalogSeedingIntegrationTest` fails two cases on `dev` with a `TransientPropertyValueException` (a `Song` referencing an unsaved `User`)

Tests:
- [x] Batch 1: integration tests that a pending token is refused on REST and STOMP, that setup can't disable two-factor, that cross-site `/auth` requests without a CSRF token are refused, that a replayed code is refused, and that repeated failures lock the account temporarily
- [x] Batch 2: integration tests that a non-member's SUBSCRIBE to group and session topics is refused and that a voice signal reaches only its target
- [x] Batch 3: the frontend build, lint, and unit and end-to-end suites pass on the upgraded dependencies, and `npm audit` reports no high or critical advisory
- [x] The frontend build fetched Google Fonts through `next/font/google`, and a change in Google's responses to CI made Turbopack fail to resolve the font files, breaking every frontend build. Serve the four font families from the repo with `next/font/local` so the build makes no font requests, and confirm a clean build and the unit suite pass
- [x] The e2e login helpers wait for the `/playlists` load event with `page.waitForURL`, and in a full local run one or two login-dependent specs (the two-player round, and sometimes core flows) time out there even though the page has already reached `/playlists`. The same spec fails the same way on `dev` before the batch 3 upgrades. Find what holds the load event open and make the login wait on the rendered page instead
- [x] Batch 4: service tests for reconnect on subscribe, disconnect only after the last socket, graceful completion on an empty queue, startup rescheduling, the idle placement timeout, skip-betting eligibility, once-per-round tallies, and guess result delivery
- [x] Batch 5: integration tests that non-members can't read results, that a shared song can't be edited through another user's playlist, and that invalid display names and avatar URLs are refused
- [x] Batch 6: unit tests that an oversized-dimension image is refused before decoding, and integration tests for the bulk import cap, quota, and up-front access check
- [x] Batch 6: service tests for the background playlist import cap and quota, the join-attempt limit, the admin kick (admin only, not while a session runs, the kicked user can't rejoin), and closing a kicked user's sockets
- [x] The batch 5 display name pattern uses Java's `\p{Cntrl}`, which the OpenAPI spec publishes unchanged and the generated zod schema compiles as a JavaScript `u` regex, which throws on load. Use `\p{Cc}`, valid in both, and regenerate the client
- [x] Batch 6: frontend tests for the lobby kick control and the import page showing the server's refusal message
- [x] Batch 7: handler tests for each mapped status and the generic message, and unit tests for `returnTo` rejecting `/\` and other-origin values on both sides
- [x] Batch 8: tests for CSV cell neutralizing, the AI key compare and empty-key startup refusal, and the atomic group cap

## Docs: sync project documentation with current code

- [x] Verify every in-scope task and story status against the current `dev` code, tests, migrations, and endpoints (controllers, services, frontend routes, and Flyway migrations through V27)
- [x] Correct in-scope reference docs, README.md, CONTRIBUTING.md, and ai/spikes/README.md where they contradict the current implementation
- [x] Run `python scripts/archive_completed_tasks.py` after all verified TASKS.md updates (no eligible section to archive)
- [x] Review the documentation diff for stale claims and the repository writing rules

### Open implementation gaps found in this pass

- [x] Add `POST /api/groups/{groupId}/members/{memberId}/remove`, restricted to the group admin, and close the removed member's active group sockets (`GroupController`, `GroupService`, and `GroupMemberRemovalListener`)
- [x] Protect the AI service's `/metrics` route with `X-Internal-Api-Key` and disable its interactive API documentation outside development (`ai/app/main.py`)
- [x] Disable Swagger UI and `/v3/api-docs` outside development (`application-prod.properties`)
- [x] Add the CSP, `frame-ancestors`, and `Referrer-Policy` headers in `frontend/next.config.ts`
- [x] Replace `next/font/google` with checked-in local font assets so frontend builds do not request Google Fonts (`frontend/app/fonts` and `frontend/app/layout.tsx`)
- [x] Cap each bulk or background playlist import at 200 songs and enforce a shared 500-new-songs-per-user daily limit (`ImportQuotaService`)
- [x] Limit group join-code attempts to 10 per 10 minutes (`GroupService`)
- [x] Require a session song pool of at least the player count times the configured win condition (`GameSessionService.requireEnoughSongs`)
- [x] Map validation failures to 400, access denial to 403, conflicts to 409, rate limits to 429, and unexpected failures to a generic 500 response (`GlobalExceptionHandler`)

## Story 9: DJ real YouTube link-out

Stories 10, 11, and 39 have all shipped (backend). Story 9's draft tasks confirmed accurate against the real code: no DJ view exists in the frontend, the backend session model tracks the DJ per round but no link-out, audio capture, or DJ-role enforcement is built. Ready.

The DJ's only in-app action is "Open YouTube Link"; playback, pausing, and closing the tab or app all happen on YouTube itself, never mirrored into the game. The round's own flow, the betting countdown and window, the reveal, and advancing to the next player, runs automatically off timers the game already has once the active player locks in a placement, with no manual trigger from the DJ or any player (see `GAME_DESIGN.md`'s Roles section and story 10's automatic-reveal task).

Backend slice (this batch). The DJ needs the current round's YouTube watch URL to link out, and the round DTO deliberately withholds a song's `youtubeId` until the reveal, so playback data reaches only the DJ and only during the playback window. `GET /api/sessions/{sessionId}/link-out` returns the current round's video id and canonical watch URL, restricted to that round's DJ and refused once the round is REVEALED or SCORED. The watch URL is built by `YoutubeLinkParser.buildWatchUrl` from the stored video id rather than assembled inline. Round flow stays driven by placement-lock-in timers, so "the DJ opened the link" needs no server-side state and belongs to story 28 along with the rest of the frontend. The audio-stream cutoff on guess lock-in is a client reaction to the existing `GUESS_LOCKED` broadcast, so it too is a story-28 concern with no new backend state.

- [x] Backend: expose the current round's YouTube link-out (video id plus canonical watch URL) to the round's DJ, gated so only that DJ can fetch it and only before the reveal (`GameSessionService.getCurrentRoundLinkOut`, `GameSessionController` `GET /api/sessions/{sessionId}/link-out`, `RoundLinkOutDTO`)
- [x] Backend: build the watch URL from the stored video id through `YoutubeLinkParser.buildWatchUrl` rather than a hardcoded URL string
- [x] Build the DJ view: an "open in YouTube" link-out for remote sessions, opening a new browser tab, never an embedded player, behind an explicit "Open YouTube Link" action (story 28) (done: the session page's "Open on YouTube to play" link-out, `app/(app)/sessions/[sessionId]/page.tsx`)
- [x] Add a UI warning shown alongside that action, explicit that clicking it starts broadcasting the DJ's tab or system audio to the rest of the group (story 28) (done: "Shares your tab or system audio with the group." beside the share action on the session page)
- [x] Wire WebRTC tab audio capture to that new tab and stream it to the other players, starting only once the DJ has actually opened the link, not before (story 28) (done: `use-voice-mesh.ts` tab-audio capture, started from the DJ's click)
- [x] Add deep-link handling for in-person sessions (Android intent, iOS universal link, fallback to a plain browser link) (story 28) (done: `lib/youtube-link-out.ts` builds the Android intent, the iOS universal link, and the plain-link fallback)
- [x] Wire the active player's audio-stream cutoff over WebSocket: cuts off immediately on guess lock-in, regardless of what's still playing on the DJ's end (story 28, client reaction to the existing `GUESS_LOCKED` broadcast) (done: `use-voice-mesh.ts` silences the DJ for the active player from lock-in until the next round)
- [x] Backend: restrict the link-out to the DJ role specifically, a non-DJ player's or non-member's attempt is rejected

Tests:
- [x] Unit test: `YoutubeLinkParser.buildWatchUrl` produces the canonical watch URL and rejects a non-video-id input (`YoutubeLinkParserTest`)
- [x] Integration test: the round's DJ fetches the link-out and gets the correct watch URL for the current round's song; a non-DJ player is denied; a non-member is denied; the link-out is refused once the round is revealed (`GameSessionLinkOutIntegrationTest`)
- [x] Unit test: the audio-stream cutoff fires on guess lock-in regardless of playback state, and only for the active player's stream (story 28) (done: `use-voice-mesh.test.ts`, "silences the DJ for the active player from lock-in until the next round")
- [x] Frontend test: the "Open YouTube Link" action shows the audio-sharing warning before WebRTC tab capture starts (story 28) (done: the session page test "asks the voice sidebar to share tab audio from the DJ's own click")
- [x] Integration test: deep-link handling falls back to a plain browser link when the YouTube app isn't installed (story 28) (done as a unit test: `lib/youtube-link-out.test.ts` covers the Android intent's encoded browser fallback)

## Story 13: Group-scoped text chat

Checked against real code: no chat model or endpoint exists. Blocked on story 11 (WebSocket layer) and story 39 (group): chat is scoped to the group's lifetime, not the game session's, and rides the WebSocket layer. Both have now shipped (backend). Draft tasks confirmed accurate: no `ChatMessage` entity or send/receive endpoint exists. Ready.

- [x] Implement `ChatMessage` as an ephemeral Postgres row (sender, group, body, timestamp). Built as `ChatMessage` with a `group_id`, `sender_id`, `content`, and `createdAt`, table `chat_messages` in migration `V14`, scoped to the group's lifetime rather than a game session's.
- [x] Client-to-server STOMP channel to send a message, riding the WebSocket layer built in story 11. `GroupChatController` maps `/app/groups/{groupId}/chat` (destination `GroupDestinations.chatDestination`), resolving the sender from the socket's authenticated principal the same way `GameActionController` does.
- [x] Broadcast new messages to the group's STOMP topic. `ChatService` sends the persisted message to `GroupDestinations.chatTopic` through `SimpMessagingTemplate`, serializing with the application `ObjectMapper` for the same reason `GroupBroadcastListener` does.
- [x] Load message history when a client joins or reconnects to a group. `GET /api/groups/{groupId}/chat/messages` returns the most recent messages, member only, bounded by a named page-size constant.
- [x] Purge chat history when the group is deleted, matching the group's ephemeral lifecycle. The `chat_messages` group foreign key is `ON DELETE CASCADE`, so both the expiry sweep and an admin's final leave clear a group's messages with the group.
- [x] Message length limit (500 characters) and a per-user send rate limit (5 messages per 10 seconds) to prevent spam within a group. Enforced in `ChatService` against named constants, reusing `RateLimiterRegistry`; a breach rejects the send and writes the stubbed `TODO: story 34` rate-limit event through `AbuseVisibilityEvents`.
- [x] Frontend: semi-transparent bottom-left overlay, toggled by a keybind or a clickable button, rather than a persistent input field, plain username-and-message lines, no threading (story 28, see `GAME_DESIGN.md`'s Interaction and animation section) (superseded: the chat panel floats above its button per LAN playtest batch 4)

Tests:
- [x] Unit tests for the message length limit and the per-user send rate limit, including the boundary values (`ChatServiceTest`: at-limit passes, over-limit rejects, blank rejected, under the rate limit passes, over it rejects and fires the stubbed event, and the limit is per user)
- [x] Integration test: message history loads correctly on join and on reconnect (`GroupChatIntegrationTest`: a member reads history most-recent-first, a non-member is denied)
- [x] Integration test: chat history is gone once the group is deleted (`GroupChatIntegrationTest`: the expiry sweep removes the group and its chat rows)

## Story 39: Group

Checked against real code: no group model exists, this is greenfield work. Based on `ARCHITECTURE.md`'s Group shape and lifecycle, and `GAME_DESIGN.md`'s Groups section.

- [x] Implement `Group` and `Member` as ephemeral Postgres rows; `Member` carries a per-group display name and avatar, separate from the user's account profile
- [x] `POST` endpoint to create a group; creator becomes admin
- [x] Enforce one active group membership per user
- [x] Generate a unique 4-letter join code alongside the existing invite link when a group is created
- [x] `POST` endpoint to join a group via invite link or join code, only while the group hasn't started a game session yet
- [x] On join, prompt for a per-group display name and avatar, defaulting to the user's account values but editable; other members only ever see this per-group identity, never the account profile
- [x] Settings (playlist(s), DJ mode, win-condition card count), editable by the admin only, broadcast to all members in real time. The data model and the admin-only update endpoint (`GroupService.updateGroupSettings`) persist correctly; `updateGroupSettings` now publishes a `SETTINGS_CHANGED` event story 11's `GroupBroadcastListener` forwards to the group's settings STOMP topic, see `DECISIONS.md`.
- [x] Chat available from group creation, stored for the life of the group. Backend built under story 13, which owns `ChatMessage` and send/receive/persistence; the chat overlay UI stays with story 28. `Group` uses a plain `Long` primary key, which is all `ChatMessage` needs to attach to it. See `DECISIONS.md`.
- [x] Voice joinable and leavable at any time (see story 12 for the WebRTC mechanics). Built as a plain `isInVoice` presence flag on `Member` plus join/leave-voice endpoints that flip it; the actual WebRTC mesh/signaling belongs to story 12, blocked on this story and story 11 both shipping. See `DECISIONS.md`.
- [x] 30-minute timer from group creation to the admin starting a game session, delete the group if it fires
- [x] Admin action to start a game session (see story 10), locks the group to new members. Only the group-side state transition (`GroupService.startGameSession`) exists; story 10's own session model doesn't, so nothing calls this yet outside tests.
- [x] 30-minute timer from a game session ending to the admin starting another, delete the group and remove every member if it fires. Both timers share one `expiresAt` column swept by a scheduled job (`GroupExpirySweeper`); `GroupService.recordGameSessionEnded` is the extension point story 10 calls once a real session ends, restarting this timer. See `DECISIONS.md` for the sweep-versus-per-instance-timer design choice.
- [x] Explicit leave vs. disconnect: disconnect only flips `isConnected`, explicit leave removes membership
- [x] Admin explicitly leaves: promote the next-earliest-joined member to admin, or delete the group if none remain
- [x] Admin action to voluntarily promote another member to admin at any time, independent of leaving
- [x] On app load, check the logged-in user's active group membership and prompt to return or leave, no link-based reconnect
- [x] Frontend: visually mark the admin, a crown icon, distinct from regular members. Deferred: this batch is backend-only, the frontend gets a full redesign under story 28. (done: the lobby's `Crown` icon on the admin member)

Tests:
- [x] Unit tests for join-code generation: uniqueness, and the 4-letter format
- [x] Unit tests for the one-active-group-per-user constraint
- [x] Unit tests for per-group profile isolation: a member's account profile is never exposed through group-scoped endpoints, only their per-group identity
- [x] Unit tests for admin transfer: both the explicit-promote action and the auto-promote-on-leave path, including the no-members-remain deletion case
- [x] Integration test: full group lifecycle, create, join via both invite link and join code, admin-only settings update persists, admin starts a session, group locks to new members. The live-broadcast half of the settings update isn't tested, there's no WebSocket layer yet to broadcast over, see the settings task note above.
- [x] Integration test: both 30-minute timers, pre-session and between-sessions, including that they don't fire early or fail to fire
- [x] Integration test: explicit leave removes membership while disconnect only flips the connection flag

## Story 46: Playlist membership: owner/admin, granular permissions, kick and ban, per-playlist identity

Surfaced during story 28's design pass on the Edit playlist and Join by invite screens, not part of the original backlog mapping. Backend built on `feature/playlist-membership`; the Edit playlist member list is implemented in story 28, while the Join by invite identity step remains open. See `DECISIONS.md`'s 2026-09 "Playlist membership" entry for the decided shape.

- [x] Add an owner/admin concept to `Playlist`: an `ownerId` (or equivalent), set to the creator on creation; only the owner can rename, change cover/color/description, toggle `isPublic` (story 30), delete the playlist, or manage other members
- [x] Replace the plain `Playlist.users` many-to-many with a `PlaylistMembership` entity (playlist, user, `canRead`/`canWrite`/`canDelete` booleans, joined-at, per-playlist display name and avatar), coordinate with story 15 since both touch `Playlist`'s relations
- [x] Migration for existing data: no pre-story-46 playlist or song has a real owner to assign, so the migration clears every existing playlist and song rather than backfilling a guessed one; users are untouched
- [x] The owner can leave a playlist at any time; if other members remain, leadership passes automatically to whichever member joined earliest, a playlist with other members left is never without an owner
- [x] Add an owner-only endpoint to transfer ownership to any current member at any time; the previous owner stays on as a regular member with their existing grants
- [x] Gate `PlaylistService`'s existing access checks by the new grants: reading requires `canRead`, adding a song requires `canWrite`, removing a song requires `canDelete`; an owner always has all three implicitly
- [x] Add owner-only endpoints to update a member's `canRead`/`canWrite`/`canDelete` grants independently, each revocable without affecting the others
- [x] Add a kick endpoint (owner only): ends the membership, the existing invite link or code still lets the kicked user rejoin
- [x] Add a `PlaylistBan` entity (playlist, user, banned-at) and a ban endpoint (owner only): ends the membership and blocks that user's future join attempts against this playlist
- [x] Update the join-by-invite endpoint to reject a banned user's join attempt, and to accept the per-playlist display name/avatar submitted with the join request, defaulting to the account's own when not overridden
- [x] Frontend: Edit playlist's member list (per-member read/write/delete toggles, kick and ban actions), owner-only, already designed
- [x] Frontend: Join by invite's identity step (avatar and display name, pre-filled from the account, editable before joining), already designed (done: `app/(join)/playlists/join/[inviteCode]/page.tsx` pre-fills and edits the display name and avatar)

Tests:
- [x] Unit tests for the owner-only gate: every owner-only action (rename, cover/color/description, public toggle, delete, kick, ban, grant changes) rejects a non-owner member
- [x] Unit tests for each of the three grants enforced independently: a member with `canRead` false can't view, `canWrite` false can't add a song, `canDelete` false can't remove one, and combinations of the three don't interfere with each other
- [x] Unit tests for kick versus ban: a kicked user's subsequent join-by-invite succeeds, a banned user's is rejected
- [x] Integration test: full lifecycle, join with a custom per-playlist identity, owner revokes a grant, the affected action is blocked, owner kicks the member, the member rejoins successfully, owner bans a different member, that member's rejoin attempt is rejected
- [x] Integration test: the migration clears every pre-existing playlist and song and leaves users untouched
- [x] Unit tests: the owner leaving a playlist with other members promotes the earliest-joined remaining member; the owner leaving alone deletes the playlist; a non-owner leaving never changes ownership
- [x] Unit tests: ownership transfer moves the owner to the chosen member and the previous owner stays a member; a non-owner can't transfer ownership; transferring to a non-member is rejected

## Story 14: Song search by link or keyword before submission

Checked against real code: `SongRepository` has zero custom query methods, no backend search capability exists. The only "search" today is `DataTable`'s client-side substring filter over an already-loaded playlist's songs, not a real query.

Backend-only for this batch, matching how the song-genre-and-print-redesign batch split its own backend/frontend work: the frontend is implemented under story 28 against the mockups in `docs/design/source/`. The remaining search wiring and broader state coverage stay tracked here for traceability and are completed or verified through story 28. See DECISIONS.md for the search-scope decision.

- [x] Add a backend search endpoint, `SongRepository` has no query methods to build on today (`GET /api/songs/search`, the first top-level `/api/songs/...` route)
- [x] Support search by artist/title keyword and by YouTube link/ID (the link-parsing logic already exists client-side as `extractYoutubeId` in `AddSongForm.tsx`; replicated server-side as `YoutubeLinkParser`)
- [x] Decide search scope: within one playlist, across the user's playlists, or catalog-wide, affects both the query and which of `PlaylistService`'s access checks apply (catalog-wide search would need one, since it isn't a per-playlist access check). Decided catalog-wide, see DECISIONS.md
- [x] Story 28: Wire `AddSongForm.tsx`'s submission flow to check search results first, so a song already in the catalog isn't resubmitted as a near-duplicate (distinct from story 16's pgvector-based similarity check; this is a plain keyword/link pre-check)
- [x] Story 28: Add the frontend search UI, replacing or extending the current client-side-only title filter in `DataTable`

Tests:
- [x] Unit tests for the search query: keyword matching and YouTube link/ID matching
- [x] Integration test: search results respect the chosen scope's access checks (catalog-wide plus plain authentication: any authenticated user can search the whole catalog, an unauthenticated request is rejected)
- [x] Deferred to story 28: Frontend test: the search UI returns and displays results correctly (done: `SongSearchStep.test.tsx`)

## Story 17: Community song reports and confirmations

Depends on story 40 for the admin review surface, which now owns the `ADMIN` role and access check absorbed from story 19. Resolution stays fully manual: an admin decides every report, nothing here auto-changes `verificationStatus` on its own, see `DECISIONS.md`. Every card is reportable, including `VERIFIED` ones. Story 40's `ADMIN` role, `AdminAccessGuard`, and `AdminAccessRequiredException`-to-403 mapping have landed on `dev`; the admin endpoints below reuse them. All frontend affordances (report button, thumbs-up, review surface UI) belong to story 28 per this file's preamble policy. The report-submitted abuse-visibility event is a stubbed structured log line marked `TODO: story 34` until story 34's event pipeline ships, per the standing policy above.

- [x] Add a `SongReport` entity (reporter, song, message, suggested correct year, sources, status), unique per reporter per song
- [x] `POST /api/songs/{songId}/reports` to submit a report, available to any authenticated user, on any card regardless of `verificationStatus`; fires the stubbed report-submitted event
- [x] Add a report button to the song detail page (`SongReadOnlyView.tsx`), available on every card regardless of `verificationStatus` (story 28)
- [x] Add a `SongConfirmation` entity (user, song, timestamp): the community thumbs-up, distinct from a report, one per user per song
- [x] `POST /api/songs/{songId}/confirmations` to submit a confirmation, accepted only on `NEEDS_REVIEW`/`MANUAL_ENTRY` cards
- [x] Add a thumbs-up affordance to the song detail page (`SongReadOnlyView.tsx`), visible only for `NEEDS_REVIEW` cards, "is this correct?" (story 28). `MANUAL_ENTRY` cards route to the editable `SongForm.tsx` instead of the read-only view, so the thumbs-up doesn't appear there; the report button and confirmation entity remain reachable through the API regardless
- [x] Admin review endpoint `GET /api/admin/song-reports/queue` (reuses `AdminAccessGuard`, non-admin gets 403) ordered by priority, not submission time:
  1. Converging reports: two or more independent reports on the same card suggesting the same year, ranked highest regardless of current `verificationStatus`, including `VERIFIED` cards
  2. Reported, no convergence (a single report, or several that disagree with each other): ranked below convergent reports, by `verificationStatus` (`MANUAL_ENTRY`/`NEEDS_REVIEW` before `VERIFIED`)
  3. Unreported `NEEDS_REVIEW`/`MANUAL_ENTRY` cards with at least one confirmation, ranked by confirmation count, a fast confirm rather than research
  4. Unreported `NEEDS_REVIEW`/`MANUAL_ENTRY` cards with no confirmations, ranked by `verificationStatus` alone (`MANUAL_ENTRY` before `NEEDS_REVIEW`)
  5. `VERIFIED` cards with no report never appear in the queue
- [x] Admin action endpoints (reuse `AdminAccessGuard`): `POST /api/admin/song-reports/{songId}/resolve` marks the open reports upheld and applies the admin's chosen corrected year (optional) and verification status (required, one of `VERIFIED`/`NEEDS_REVIEW`/`MANUAL_ENTRY`) unconditionally, overriding even a `VERIFIED` song's locked year and status. `POST /api/admin/song-reports/{songId}/dismiss` clears the open reports
- [x] The review endpoint exposes every signal behind a card's ranking (open report count, whether they converge and on what year, confirmation count) rather than a single opaque score, the admin makes the actual call
- [x] Review surface UI over the endpoint above (story 28) (done: the admin report queue at `app/(app)/admin/reports`)

Tests:
- [x] Unit tests for `SongReport` and `SongConfirmation` behavior: duplicate report/confirm prevention, confirmation rejected on a `VERIFIED` card
- [x] Unit tests for the queue-ranking logic covering all five priority tiers, including convergence overriding a `VERIFIED` card's default low priority
- [x] Unit test confirming the stubbed report-submitted event fires on submission
- [x] Unit tests confirming a resolve on a `VERIFIED` song overrides its locked year and status, a resolve with no corrected year leaves the year untouched, and a resolve with no open reports throws
- [x] Integration test: submitting a report end to end, visible on the admin review surface at the correct priority tier
- [x] Integration test: two reports on the same card suggesting different years don't count as convergence, and rank below a genuinely convergent pair
- [x] Integration tests: admin-only access enforced via `AdminAccessGuard` (non-admin gets 403 on the queue and on resolve), unauthenticated submission gets 401

## Story 18: Criteria for promoting a reported or newly submitted song to verified

Criteria decided, twice: an earlier `DECISIONS.md` entry ("Story 18: verification is a lock, not a score") was written without authorization and retracted; the actual criteria below are the ones later validated live against a real 70-song set (story 20's spike) and confirmed final. `TASKS.md` and `PROJECT_STATE.md` may still have stale references to the retracted entry's name, not yet cleaned up, don't trust a mention of that entry as meaning it exists. Story 23 landed `Song`'s `verificationStatus`, `confidence`, and `metadataRaw` fields, previously this story's blocker. The lock-evaluation logic and its wiring into story 40's submission pipeline are built and merged to `dev` (batch 20, `feature/verification-promotion`); the checked boxes below reflect that.

Final lock rule, validated: **only exact agreement among MusicBrainz, Discogs, and Wikidata locks with zero LLM involvement.** Anything short of that (partial agreement, a missing source, three-way disagreement) routes through Wikipedia (fetched and extracted via a dedicated LLM reading-comprehension call, DeepSeek-V4-Flash) and a four-source reconciliation call (gpt-5-nano) instead of a "2 of 3 plus an LLM judge" shortcut; that shortcut was never validated and is not what got built. Confirmed on real data: 53% of a 70-song test set locked with no LLM call at all, and the full pipeline (locked plus reconciled) hit 99% (69/70) accuracy, the one miss being a song no source had any data on at all.

- [x] Depends on story 23: `verificationStatus` field exists on `Song` before any of this can be implemented. It now does (`UNVERIFIED`/`VERIFIED`/`NEEDS_REVIEW`/`MANUAL_ENTRY`), matching this story's own state machine exactly
- [x] Add the lock-evaluation logic: given the release-year candidates from MusicBrainz, Discogs, and Wikidata, lock (set `verificationStatus` to verified, immutable from here) only when all three agree exactly; copy and adapt the validated logic in `ai/spikes/run_conditional_pipeline.py`
- [x] When the three don't agree, fetch and extract Wikipedia (the dedicated extraction prompt, `ai/spikes/combo_prompts.py`'s `build_wikipedia_extraction_prompt`, DeepSeek-V4-Flash) and run four-source reconciliation (`build_four_sources_prompt`, gpt-5-nano) to produce the year, confidence, and reasoning, sets status to needs-review, never silently promoted to verified even when the LLM answers with high confidence, the lock is reserved for source agreement alone
- [x] A genuine no-answer (no source, including Wikipedia, has anything at all) escalates to a human for manual entry of the release year, rather than guessing or leaving the song stuck. A manually-entered year gets its own status, below `needs-review`, the least-trusted tier the schema has, distinct from a year that at least one source or the LLM reconciliation actually produced
- [x] Once locked, no code path may overwrite the year, including story 17's community reports, a report against a locked song surfaces for admin judgment but can't auto-apply; enforced by `PlaylistService.EDITABLE_VERIFICATION_STATUSES` which already excludes `VERIFIED`
- [x] Wire this into story 40's submission pipeline (both the admin backlog drain and the on-the-spot path) as the step that runs immediately after source gathering, before any LLM reconciliation call, so the LLM is only invoked for the fraction of songs the lock doesn't resolve (done: `evaluate_lock` runs in `resolve_metadata` before any reconciliation LLM call)

Tests:
- [x] Unit tests for the lock-evaluation logic: exact 3-way agreement locks with no LLM call, every other combination (partial agreement, missing source, 3-way disagreement) routes to Wikipedia+reconciliation instead
- [x] Unit test confirming a locked song's year is immutable even via story 17's report path (superseded: resolving reports may override a locked year by design, covered by `SongReportServiceTest.resolvingReportsOnAVerifiedSongOverridesItsLockedYearAndStatus`)
- [x] Integration test: a submission with unanimous source agreement never triggers an LLM call at all (done as a unit test: `test_exact_agreement_locks_with_no_llm_call` in `ai/tests/metadata/test_verification.py`)
- [x] Integration test: a submission with no data from any source, including Wikipedia, routes to manual review rather than erroring or silently failing (done as a unit test: `test_all_sources_empty_routes_to_manual_review`)

## Spike: MusicBrainz and Wikidata sourcing

Handoff item 1. Story 25 (Discogs) is separate and already `Ready`. MusicBrainz, Wikidata, and Wikipedia are now implemented and wired into the metadata pipeline (`ai/app/metadata/sources/`), validated first through real experimentation against real submitted songs rather than guessed at.

- [x] Query MusicBrainz's live API against real submitted songs (42 total across a hand-picked mainstream/mid/niche/Romanian set and a real 34-song YouTube playlist): confirmed the release-group `first-release-date` field is reachable and correct, but only after also querying the parent album's release-group and taking the earliest of the two, a track-only query missed the true original date repeatedly (a niche track resolved to a 2019 reissue instead of 2011; 4 of 5 mismatches found against the real playlist were MusicBrainz alone disagreeing with Discogs/Wikidata/the video's own metadata, all consistent with this same track-only gap)
- [x] Query Wikidata's API against the same songs, decided: the `wbsearchentities`/`wbgetentities` REST actions, not SPARQL. Confirmed findings: search on the title alone (a combined "artist title" query returns nothing); `wbsearchentities` needs a wide result window, not just the top few, a common title can bury the real song 6+ results down (Katy Perry's "Dark Horse" ranked behind a Nickelback album, an unrelated film, and a restaurant); never fall back to an unrelated top-ranked result when nothing in the pool matches the artist, prefer no match over a wrong one; a song's own `P361` ("part of") claim can resolve its parent album directly, without needing to know or guess the album's title
- [x] Evaluate Apple's iTunes Search API as a candidate additional source: terms-of-use read done, rejected without live testing, its license is promotional/affiliate-only, see `DECISIONS.md`'s 2026-09 entry
- [x] Test multiple query-phrasing strategies per source: confirmed titles with a `(feat. X)`/`(ft. X)` clause return zero MusicBrainz and Wikidata matches until that clause is stripped (Discogs' search tolerates it fine as-is); confirmed native-script (non-romanized) titles work correctly when typed accurately, a niche track initially tested under a romanized guess found nothing, the same track under its real stylized title resolved correctly
- [x] Confirmed Discogs needs the same "check every candidate, take the earliest" treatment already applied to MusicBrainz: a track can belong to more than one distinct master (its own standalone-single release and the album it also appears on), each with its own year, trusting whichever master a search result lists first picked a 2015 remix single over the true 2014 album original for a real playlist song ("Hey Mama")
- [x] Fixed: Discogs' master resource uses `year: 0`, not null, for an unknown year (live-tested on "Titanium"), now treated as unknown rather than a literal date
- [x] Fixed: Wikidata's documented anonymous rate limit is 10 requests/minute, not the ~2/second this had been running at; a descriptive User-Agent isn't confirmed to buy the more lenient browser-identified tier, paced to the stricter number
- [x] Extract featured artists as a structured list (not just a stripped clause) when a title has a `(feat. X, Y & Z)` clause, feeds story 23's open question on multi-artist storage; verified against real playlist titles including mixed comma-and-ampersand lists and hyphenated names
- [x] A video's title and channel name don't always carry the real artist at all, confirmed on three real anime openings across three different channels (Crunchyroll, a Western licensor; TOHO animation, a Japanese broadcaster; and, contrast case, MAPPA's own studio channel and One Piece's official channel, which do embed artist and song directly in the title, just Japanese-formatted, not "Artist - Title"). Each of the two failing cases had the real artist only in the description, in a different format each time (an English "OP 2 'X' by Y" sentence; a mixed Japanese/English broadcaster paragraph that also carried a release date). Decided: don't regex per-channel description formats, they vary too much to keep up with case by case; extract title/artist/featured-artists from the raw title+channel+description via a structured-output LLM call instead, when the channel doesn't look like a real artist. This runs once per submission, so it's exactly the kind of call where story 20's cheap/free-LLM spike matters, not just the final synthesis step
- [x] Decide how the pipeline should call and reconcile MusicBrainz, Wikidata, and Discogs results against each other, see `DECISIONS.md`'s 2026-09 "Metadata pipeline call/reconcile shape" entry
- [x] Decided, final: the full patient/fast two-tier pipeline shape, model choices, and a fourth source (Wikipedia), all validated with real data against a 70-song set, not guessed at. See story 20's spike section for the full results (99% patient, 90% fast) and story 18 for the lock-evaluation logic this unlocks. Not revisited again barring a real problem found during implementation
- [x] Build `ai/app/metadata/sources/wikidata.py`, copy and adapt `ai/spikes/wikidata_spike.py`'s validated implementation (search/select/date-extraction logic, bot-password authentication, rate-limit pacing), following `youtube.py`'s pattern for HTTP client usage, timeout, and broad-exception-to-`UNKNOWN_DEFAULTS` fallback for anything the spike didn't need to handle. The track+album comparison uses the resolved track entity's own `P361` claim to find its parent album directly, rather than a separately-guessed album title
- [x] Un-stub `ai/app/metadata/sources/musicbrainz.py`, copy and adapt `ai/spikes/musicbrainz_spike.py`'s validated implementation (track+album query, `select_best_release_group`'s earliest-candidate selection, adaptive rate limiter)
- [x] Add `ai/app/metadata/sources/wikipedia.py`, a new source, doesn't exist yet: copy and adapt `ai/spikes/wikipedia_spike.py`'s validated implementation (full-text search, `select_best_page`'s track/album-aware disambiguation, lead-extract fetch). Unlike the other three sources, this one has no deterministic parsing step: it returns each matched article's lead-section prose as-is, the dedicated LLM extraction pass (`ai/spikes/combo_prompts.py`'s `build_wikipedia_extraction_prompt`) is story 18's scope, once story 23's schema exists for it to write a result to
- [x] Wire all three into `service.py`'s `_gather_all_metadata` and add matching `_append_musicbrainz_data`/`_append_wikidata_data`/`_append_wikipedia_data` sections in `prompt.py`, all three always show their section header even with no candidates, matching `ai/spikes/combo_prompts.py`'s validated reconciliation-prompt shape rather than omitting the section
- [x] Add `ai/app/clients/discogs_client.py` or equivalent, copy and adapt `ai/spikes/discogs_spike.py`'s validated implementation into story 25's `discogs.py` task, including the `masterless_release_years` fallback (a real, live-confirmed bug: releases with no linked master were silently discarding a correct year sitting right in the search result) (done: `ai/app/metadata/sources/discogs.py`, including `masterless_release_years`)

Tests:
- [x] Unit tests for `musicbrainz.py`, `wikidata.py`, and `wikipedia.py`'s request building, response parsing, and fallback behavior, mirroring `youtube.py`'s existing test pattern

## Spike: Local/cheap LLM option for bulk metadata processing

Handoff item 2. Its own branch, separate from the metadata-source spike, per the handoff's explicit instruction. Covers both hosted-API and locally-runnable options, any provider, closed or open-weight, the constraint is Pydantic-compatible structured output (`AGENTS.md`'s non-negotiable rule against regex-parsing LLM output), not a specific deployment shape.

Survey complete, verified against each provider's own official docs across three research passes. Shortlist, all confirmed with a hard structured-output guarantee (constrained decoding or strict JSON-schema mode, not best-effort JSON):

- ~~Zhipu/Z.ai~~, dropped on two independent grounds. Free tier (GLM-4.5-Flash and GLM-4.7-Flash both tested): confirmed live that structured output doesn't hold, `response_format` with a JSON schema is silently ignored in favor of markdown-fenced prose, and forced tool-calling on GLM-4.7-Flash hung indefinitely rather than responding at all, see `ai/spikes/README.md`. Paid tier (GLM-5.3-Flash, the newest and cheapest paid option): not price-competitive even before testing whether it works, its list price ($0.15/$0.50 per million) and promo price ($0.075/$0.25 through 2026-09-09) both cost more on input than `gpt-5-nano`'s $0.05, which is already confirmed working. No remaining Zhipu tier is both cheaper and functional.
- Groq gpt-oss-20b and gpt-oss-120b, real recurring free tier (30 RPM / 1,000 RPD / 8,000 TPM / 200,000 TPD), cheap paid overflow beyond that. Live-confirmed: structured output holds.
- DeepInfra Llama-3.1-8B-Instruct-Turbo, cheapest confirmed paid hosted option. Live-confirmed once balance was added: `response_format`'s json_schema mode gets a 405 from this specific model, but forced tool-calling works, `openai_compatible_spike.py` now tries the former and falls back to the latter automatically.
- ~~AWS Bedrock Nova Micro~~, dropped. Structured output was live-confirmed to hold via forced tool use before this, but every real call hit `ThrottlingException: Too many tokens per day` on the very first request despite the account's own Service Quotas showing a 5.76 billion token/day allowance nowhere near exhausted. A known AWS provisioning bug on newly enabled accounts, the backend token counter for a specific model sometimes never initializes correctly, not something Service Quotas can fix; only an AWS Support case can, and that's not worth waiting on for this spike.
- llama.cpp run locally, 7-8B class model, zero marginal cost, see `hardware-local-llm` in project memory for why the laptop and not the desktop. Structured output live-confirmed via forced JSON schema. Runs on CPU only (~10-12 tokens/sec), never the Arc iGPU: the GPU driver has no Vulkan ICD registered (`HKLM\SOFTWARE\Khronos\Vulkan\Drivers` empty on both registry views). Confirmed this isn't an install-quality problem, a full driver reinstall via Intel Driver & Support Assistant (32.0.101.8331 to 32.0.101.8991) made no difference; the driver's own INF has no registry section writing that key at all, some separate Vulkan runtime component would need to supply it. The one remaining fix, manually registering the ICD via a registry edit, was declined. GPU offload is dropped for this spike, llama.cpp stays CPU-only; this affects its real bulk-throughput number, not the accuracy comparison, which already ran on CPU. Its memory-only accuracy also came in far below its own DeepInfra-hosted twin, 5/19 (26%) versus DeepInfra's 12/19 (63%) on the identical base model (Meta-Llama-3.1-8B-Instruct), the local build's 4-bit quantization (Q4_K_M) most likely the cause; notably zero wrong answers, 14 of 19 were the model cleanly declining to answer rather than guessing badly, consistent with quantization eroding recall confidence rather than corrupting it. Weakens the case for llama.cpp as a real candidate independent of the offload/speed question.

OpenAI's own cheap tier (`gpt-5-nano`, `gpt-5-mini`) stays in as benchmarks, not shortlist candidates, since every option above already beats `gpt-5-nano` on price. They exist to answer "how much accuracy, if any, does the cheap/free tier give up." Both live-confirmed: structured output holds (`gpt-5-nano` rejects a non-default `temperature`, handled in the client, otherwise no surprises).

- [x] Survey current free/cheap hosted-API options and open-weight models runnable locally, for which ones support Pydantic-compatible structured output
- [x] Narrow to a shortlist of candidates that plausibly clear the structured-output bar
- [x] Set up a client for each shortlisted candidate in `ai/spikes/` (`openai_compatible_spike.py` covers OpenAI, Groq, DeepInfra, and llama.cpp, and kept the now-unused Zhipu config for reference; `bedrock_spike.py` covers Nova Micro), no production code
- [x] Expand the test set with adversarial cases, not just the agreement-heavy set already used in the metadata-source spike. `run_matrix.py`'s `SONGS` list pruned to 4 sanity-baseline entries plus 17 new ones across reissue/pressing disagreement, cover-attribution risk, thin/partial source coverage, title collisions, and multi-artist/remix credit strings. A separate `extraction_test_set.py` covers the extraction-from-a-messy-raw-submission dimension (typos, upload-artifact noise, reversed order, a deliberately unanswerable case), independent of source-API reconciliation since it tests LLM judgment directly against the rules already in `app/metadata/prompt.py`
- [x] Run a memory-only accuracy check (no source data, LLM guessing from training alone) across every live-confirmed candidate, to answer whether a zero-source-touch fast tier is viable at all: `memory_accuracy_test.py` against the 19 scoreable songs from `run_matrix.py`'s adversarial set. `gpt-5-mini` 18/19 (95%), Groq `gpt-oss-20b` 16/19 (84%), `gpt-5-nano` 15/19 (79%), DeepInfra Llama-3.1-8B 9/19 (47%), llama.cpp (local, same base model as the DeepInfra entry) result pending its first run, Bedrock Nova Micro invalid, see below. Confirms memory-only isn't reliable enough on its own, the fast tier needs at least one real source touch
- [x] Investigated Bedrock Nova Micro's `ThrottlingException: Too many tokens per day` on every call in the memory-only test: a known new-AWS-account issue, Bedrock model quotas can default to 0 or near-0 until a manual Service Quotas increase request, not a bug in the spike client. The IAM user built for this spike's $5 budget cap has no `servicequotas:*` permission (deliberately scoped tight), so checking or requesting the actual quota needs the AWS console directly, not something this spike can do for itself
- [x] Build a third MusicBrainz/Discogs-only test batch beyond the current 37-song set: `international_test_songs.py`, 12 Romanian pop, manele, and other regional/niche songs (Nigerian afrobeats, Brazilian reggaeton, Turkish Eurovision, Punjabi independent hip-hop, Balkan trap, Puerto Rican reggaeton, Filipino OPM). Manele coverage on Wikipedia turned out too thin to responsibly source a 13th ground-truth date, several otherwise-plausible titles couldn't be pinned down and were left out rather than guessed at, itself a data point that held up in every later result
- [x] Add a raw-response cache to the spike sourcing code (`response_cache.py`, one gitignored JSON file per source, keyed by song) so LLM-combination testing reuses fetched data instead of re-querying the live APIs
- [x] Run MusicBrainz, Discogs, and Wikidata across the combined 49-song set (`all_songs.py`) with the cache wired in, and every LLM-combination scenario (single-source, three-source, four-way rollup) described below, superseded by the corrected numbers further down once real bugs were found and fixed; see those entries for the final figures
- [x] Revisited Wikipedia as a fourth structured source, prompted by a live miss no other source caught: MusicBrainz, Discogs, and Wikidata all got "Hot" by Inna wrong or came up empty, while Wikipedia's own article plainly states the correct 2008 single date. Research confirmed Wikipedia and Wikidata are the same Wikimedia Foundation infrastructure, identical rate-limit tiers (10/min anonymous, 200/min authenticated) and User-Agent policy already implemented in `wikidata_spike.py`, reusable as-is; a bot password needed its own issuance per wiki though, the existing Wikidata one didn't carry over, a new one was issued for `hittiguess-spike-wp`. The real difference: Wikipedia returns article prose, not a structured claim like Wikidata's P577, so there's no field to parse directly, this needs a dedicated LLM extraction pass over the text (`combo_prompts.build_wikipedia_extraction_prompt`), a genuinely new step none of the other three sources required, and one deliberately kept separate from every reconciliation-style prompt since it tests reading comprehension, not judgment among candidates
- [x] Built `wikipedia_spike.py` and ran it against the full 49-song (later 70-song) set (`run_full_wikipedia.py`), caching results. Fixed two real disambiguation bugs live-testing this, not hypotheticals: the page-selection logic (`select_best_page`) originally preferred any result with "song"/"single" in its title without first checking the result was actually about the right song at all (picked "Together Forever (Rick Astley song)" for a "Never Gonna Give You Up" search), and once album lookups started reusing the same function, the same preference picked wrong result *types* for albums too (an unrelated "A Night at the Opera (film)" for the Queen album lookup); fixed by filtering to title-matching results first and giving `select_best_page` a `query_type` ("track" vs. "album") so the preferred/avoided keywords flip direction correctly
- [x] Extended Wikipedia to check both the track's own article and its parent album's, the same comparison MusicBrainz, Discogs, and Wikidata already did, Wikipedia was the one source missing it
- [x] Priced gpt-5-nano against comparable-tier models while researching whether anything undercuts it: no other OpenAI model does; Anthropic has no true nano-equivalent (Claude Haiku 4.5 is 20x nano's input price); Google's closest match (Gemini 2.5 Flash-Lite) is being retired 2026-10-16. Four DeepInfra-hosted candidates surfaced instead as genuinely comparable in price: gpt-5.6-luna (OpenAI's own newest budget tier), DeepSeek-V4-Flash, google/gemma-4-26B-A4B-it, and nvidia/NVIDIA-Nemotron-3-Super-120B-A12B, all structured-output-confirmed
- [x] ~~nvidia/NVIDIA-Nemotron-3-Super-120B-A12B~~, dropped. Hung indefinitely twice in a row on DeepInfra (zero progress, zero CPU growth, for 10+ minutes, even past a 90-second request timeout added specifically because of this), disqualifying on reliability alone regardless of accuracy potential
- [x] Fixed a real reliability bug the Nemotron hang surfaced: the OpenAI-compatible client had no request timeout at all; added a 90-second one to `openai_compatible_spike.py` so a hung provider-side request can't silently block an entire batch run again
- [x] Ran the three-source combo and the Wikipedia-extraction test across the remaining new candidates (gpt-5.6-luna, DeepSeek-V4-Flash, gemma-4-26B-A4B-it) alongside the original four: none beat gpt-5-nano on the three-source reconciliation task (DeepSeek-V4-Flash tied it exactly, the others scored lower), but on the separate Wikipedia-extraction task (reading comprehension, not reconciliation, a genuinely different skill) the ranking flipped: gpt-5-mini and DeepSeek-V4-Flash led, gpt-5-nano dropped well behind, and Groq collapsed to a near-total failure to extract from prose at all. Decided: gpt-5-nano stays the reconciliation model, DeepSeek-V4-Flash is the extraction model, two different models for two different steps, not one model for everything
- [x] Found and fixed a real ground-truth bug of this spike's own making: `international_test_songs.py` had "Migraine" (Moonstar88) marked as 2008 (the single's date) without checking it against the parent album "Todo Combo"'s 2007 date, the exact single-vs-album mistake this test set exists to catch other sources making. Corrected to 2007, this project's own "earliest of track/album" rule applied consistently
- [x] Found and fixed three real, live-confirmed bugs during four-source reconciliation debugging, none guessed at: (1) Discogs silently discarded every release with no linked master (`master_id: 0` is falsy, `find_master_ids`' truthy check dropped it), even when the release's own `year` field, sitting right in the search result, was correct; fixed with a fallback (`masterless_release_years`) that recovers those years, verified live on a niche single where 7 of 9 correct-year candidates had no master at all. (2) The Wikipedia-extraction prompt had no defense against cover-attribution confusion, an article covering both an original artist's earlier recording and a later cover in one paragraph could get the wrong artist's date extracted; added an explicit rule to use the date belonging to the specific artist asked about. (3) The reconciliation prompt let the model cherry-pick one plausible-looking candidate per source instead of taking the earliest across that source's own full candidate list, and separately carried an unstated bias toward trusting MusicBrainz as inherently more authoritative than the other sources; both are now explicit rules in `combo_prompts.py`
- [x] Dropped `STRUCTURED_OUTPUT_TEMPERATURE` from 0.1 to 0.0 in `openai_compatible_spike.py`: confirmed live that non-zero temperature produced real run-to-run answer variance on close reconciliation calls, not just wording differences
- [x] Reran MusicBrainz/Discogs/Wikidata/Wikipedia and every combination scenario with all of the above fixes in place: Discogs standalone rose from 71% to 80% (and 0 no-answers, down from 3); Wikipedia extraction (DeepSeek) rose from 76% to 86%, every candidate improved substantially (gemma-4-26B-A4B-it alone jumped from 61% to 86%); the three-source combo (gpt-5-nano) rose from 92% to 94%; the four-source combo rose from 90% to **96%** (47/49), reversing the earlier finding that adding Wikipedia made things worse, once the real bugs were fixed, it's a clear net win, exactly matching the complementary-error-coverage theory
- [x] Built a fourth batch, 21 more mainstream/well-known songs across eras and regions (`mainstream_test_songs.py`), pulling the combined set from 49 to 70, including one deliberate single-vs-album trap ("Smooth Criminal", single 1988, parent album "Bad" 1987, earlier)
- [x] Decided the patient pipeline's actual metadata-gathering shape: query MusicBrainz, Discogs, and Wikidata always (free, deterministic, zero LLM cost); if all three agree exactly, lock that as the answer with no LLM call at all; only when they don't agree, fetch and extract Wikipedia (DeepSeek-V4-Flash) and run four-source reconciliation (gpt-5-nano). Built and ran this (`run_conditional_pipeline.py`) across the full 70-song set: **69/70 correct (99%)**, the only miss being "Mor De Ochii Tai" (the manele track no source has ever had real data on). 37 of 70 songs (53%) locked with zero LLM calls; only 33 (47%) needed the Wikipedia+reconciliation path; 66 total LLM calls across all 70 songs, versus 140 if every song always used both LLM steps
- [x] Decide what a genuine no-answer (no source, including Wikipedia, has anything, like "Mor De Ochii Tai") should do downstream: per story 40/41's existing "escalate, don't guess" principle, this should route to manual review rather than auto-approve or guess, but that routing isn't built or tested in this spike (done: no data from any source routes to manual review (`verification.py`, `test_all_sources_empty_routes_to_manual_review`))
- [x] Tested the fast/on-the-spot tier: MusicBrainz and Wikipedia are this spike's two strongest single sources (87% and 89% standalone across all 70 songs, see the source-comparison entries above), so the fast tier routes each song to exactly one of the two, never both, via dynamic work-stealing dispatch, whichever lane is free grabs the next song, rather than a fixed pre-assigned split, so a slower lane doesn't leave the batch half-finished while the faster one idles. Built and ran this (`run_fast_tier_dispatch.py`) against all 70 songs, real cached answers, modeled per-lane timing grounded in this session's own observed latencies (MusicBrainz's paced API calls vs. Wikipedia's fetch-plus-LLM-extraction): **63/70 correct (90%)**, MusicBrainz's lane handled 42 songs, Wikipedia's handled 28, in line with MusicBrainz being the faster lane. That's a real, known 9-point accuracy gap against the patient pipeline's 99%, the deliberate cost of answering immediately instead of waiting; every fast-tier answer is still meant to get queued for the full patient pipeline afterward (already the existing two-pipeline design), which is what closes that gap, just not instantly
- [x] Decide whether any shortlisted LLM candidate, and this conditional-pipeline shape (patient and fast tiers both), is worth building into the real AI microservice (stories 18/40), or whether further validation is needed first. Greenlit, see `DECISIONS.md` and story 20's own task list for the narrow remaining client-infrastructure scope
- [x] Design the report/re-verification system floated during this spike: settled in full detail since, not just the lowest-confidence-first sketch this line originally described, see story 17's five-tier priority queue and the 2026-09 "Report and confirmation resolution" `DECISIONS.md` entry
- [x] `docs/TASKS.md`'s own story 18 section and `docs/PROJECT_STATE.md`'s story 18 row used to reference a `DECISIONS.md` "verification is a lock, not a score" entry that was explicitly retracted earlier in this project (never actually authorized). Both cleaned up, no longer point at the retracted entry; the lock concept it described is the same shape this spike later validated with real data (three-source agreement = lock), so the underlying idea held up even though that specific entry never existed

## Second LAN playtest: playlist copy and edit cancel

- [x] Copying the invite link or invite code from the playlist page's dropdown silently fails over plain-HTTP LAN: the legacy copy fallback appends its textarea to `document.body`, outside the open menu's focus trap, so the selection is pulled away before the copy runs. Mount the textarea inside the focused element's container and focus it before selecting
- [x] The edit-playlist Cancel button only resets the drafts and stays on the page. Discard the drafts and go back to the playlist detail

Tests:
- [x] Unit test: the copy fallback mounts inside the active element's dialog or menu and copies the selected text
- [x] Page test: Cancel on the edit page navigates to the playlist detail without saving

## Second LAN playtest: gameplay rules and the end of a game

Chore, no story: findings from the second multi-device playtest. The rule changes are owner decisions recorded in `GAME_DESIGN.md` and `DECISIONS.md`.

- [x] Finishing a game strands every player on "This game session is unavailable": the session rows are purged on completion, the session page's refetch 404s before it ever sees `COMPLETED`, and the lobby's cached active session keeps redirecting back to the dead session. Route every client to the results screen off the `SESSION_ENDED` event (which carries the group id), give the results page the group id through the URL instead of the purged session, broadcast a group event when the session ends so lobbies refetch, and stop the lobby from redirecting on a cached session once the group is open again
- [x] A round is a full pass through the players, not a single turn. Number turns and rounds separately; the round counter advances when the active-player rotation wraps
- [x] Reaching the win condition no longer ends the game on the spot: the current round plays out, and the game ends at the end of the round in which anyone reached it
- [x] Rankings share places on ties (competition ranking) for cards, artists guessed, and titles guessed, and the results screen shows every tied winner
- [x] Players start with two tokens
- [x] A token is earned for the title plus at least one artist, not every artist
- [x] Each round allows one title guess and artist guesses one at a time: a correct artist locks in and lets the player try another credited artist, a wrong artist ends artist guessing for that round, and repeating an artist already guessed is rejected
- [x] The guess fields stay on screen after the active player drops the card, next to the lock-in button, and disable themselves once their guessing is closed. Guess state survives a reload through a per-player guess-state endpoint

Tests:
- [x] Unit tests: round counter wraps with the rotation, fixed-DJ and rotating; the game continues after a mid-round win and completes at the round's end; tied ranks; two starting tokens; token for title plus one artist; one title guess; wrong artist closes artist guessing; repeated artist rejected; guess state
- [x] Integration test: the ended event carries the group id and the lobby is open again afterwards
- [x] e2e: a full two-player game ends on the results screen for both players, and the lobby stays reachable afterwards
- [x] Frontend tests: results page reads the group from the URL and renders tied ranks; session page routes to results on the ended event; guess fields render alongside the lock-in button and disable when closed

## Second LAN playtest: voice, audio sharing, sidebars, and joining by code

- [x] Voice and the DJ's tab audio never worked over the LAN: `getUserMedia` and `getDisplayMedia` exist only in a secure context, and the LAN playtest serves plain HTTP, so the sidebar reported "This browser can't share tab audio" and every microphone was refused. Add an HTTPS mode for LAN playtests: a dependency-free Node proxy that serves one HTTPS origin in front of both the Next dev server and the backend, with a keytool-generated certificate for the LAN address
- [x] Say plainly when voice or audio sharing is unavailable because the page isn't served over HTTPS, instead of blaming the browser
- [x] The DJ's audio share starts from the "Open on YouTube to play" click itself: the click asks for the capture, then opens YouTube in its own window so the picker stays visible, with no second share button
- [x] The right sidebar widens from 76px to 100px in a call. Keep both sidebars at 76px in every state
- [x] The right sidebar shows only in a call or on the group lobby page, not on the game page outside a call
- [x] Voice peers never connected on the first try: a joining player announced the join before its signalling socket was subscribed, so the other members' offers were lost until the eight-second retry, and the lobby socket's presence events only refreshed the group query, not the membership the voice sidebar reads. Connect signalling before announcing the join, re-read the member list once subscribed, and refresh the membership on group presence events
- [x] There is no way to type a join code. The sidebar's play button opens a small menu to create a lobby or join one with its four-letter code

Tests:
- [x] Unit test: the LAN proxy routes API, WebSocket, and OAuth paths to the backend and everything else to the dev server
- [x] Component tests: the voice sidebar keeps a fixed 76px width in and out of a call, and hides on the game page outside a call; the join-code menu joins by code and opens the lobby
- [x] Session page test: the DJ's YouTube click requests the audio share and opens the YouTube window
- [x] Manual check on the stack: over the HTTPS proxy the page is a secure context, login and sockets work, and two players in a call connect their voice peers

## Fast-tier parallel playlist import

Chore, no story: owner decision after the second playtest. Every user YouTube import, whatever its size, goes through the fast tier from the 2026-09 two-tier decision, in parallel, and every fast answer is rechecked by the patient tier in the admin backlog. Before this, every user import ran the full patient pipeline one song at a time, the fast tier existed only as a spike simulation, and each song then ran the same patient pipeline a second time from the backlog.

AI service:
- [x] Make every source's pacing safe under concurrent requests: one shared pacer per source hands out the next request slot under a lock, so parallel songs queue for MusicBrainz, Discogs, Wikidata, and Wikipedia instead of all sleeping and firing together
- [x] `POST /metadata/identify`: the first AI pass for one video (YouTube fetch, non-music hard filter, verified-duplicate check, the combined pre-check LLM call and content safety), returning the clean title, artists, and color, or a verified duplicate's full answer
- [x] `POST /metadata/date-fast`: the fast tier's year lookup, one source per song on whichever lane (MusicBrainz, or Wikipedia plus LLM extraction) frees up soonest given its in-flight work, falling back to the other lane when the first finds nothing; answers are UNVERIFIED with the lane named in the source
- [x] The two fast endpoints get their own rate limit sized for a whole playlist in flight, separate from the full pipeline's

Backend:
- [x] A fast-tier import runner: every new song's identify call is dispatched in parallel, and each song moves on to its year lookup the moment its own identify finishes, never waiting on another song; both stages run on bounded pools
- [x] The background playlist import links each song into the playlist the moment it resolves, instead of once the whole import finishes
- [x] Job items report IDENTIFYING and DATING while they are actively being worked, so waiting items stay PENDING
- [x] The on-the-spot bulk import uses the same runner
- [x] Each fast answer is queued for the patient recheck with its fast-tier year, marked as a fast-tier recheck; the patient run records its own year on the queue row
- [x] Admin backlog status lists recent fast-tier rechecks with the fast and patient years side by side, and the admin can run the drain now instead of waiting for the daily sweep
- [x] Test account 1 is seeded as an ADMIN, including an existing row

Frontend:
- [x] The import progress screen separates songs being worked on (identifying, finding the year) from those waiting, with counts for each, and shows each resolved song as soon as it lands
- [x] The admin backlog page shows the fast-tier rechecks with fast and patient years, highlights a changed year, and has a run-now button

Tests:
- [x] pytest: the pacer spaces concurrent callers; lane choice prefers the lane that frees up first and falls back when a lane finds nothing; identify returns a duplicate's answer and a rejection; the fast endpoints' rate limit
- [x] Unit tests: the runner starts every identify before any song finishes and dates each song as soon as its own identify returns; a failed identify marks only that song unresolved; each resolved song is linked immediately and queued for the recheck with its fast year
- [x] Unit tests: the patient run records its year on a recheck row; the seeder makes account 1 an admin
- [x] Integration test: a background import against a stubbed AI service links songs as they resolve and ends DONE with every item settled
- [x] Frontend tests: the progress screen's working and waiting states; the admin rechecks table and run-now button

## Fast-tier import: rate-limit enforcement review

- [x] The import page polled every two seconds, which with the sidebar's own import polling brings a single user close to the core service's 60-requests-per-minute general limit. Poll every five seconds again
- [x] The runner's full-pipeline fallback could send several `/metadata/resolve` calls at once and trip that endpoint's 30-per-minute limit. Run the fallback one song at a time
- [x] The backend relied on the AI service's 429 to keep fast-tier calls under that endpoint's per-minute cap. Pace the backend's fast-tier calls to the same cap
- [x] A Discogs breach cooldown and a MusicBrainz back-off only paused the thread that hit them, while other threads kept calling. Hold the shared pacer so every caller waits

Tests:
- [x] pytest: a hold pushes every later caller's slot back; a Discogs breach holds the pacer; a MusicBrainz retry holds it for the new delay
- [x] Unit tests: at most one fallback runs at a time; fast-tier calls from many threads are spaced by the cap's interval

## Fast-tier import: final results on the import screen

- [x] A parallel import often finishes between two five-second polls, and a finished job stops being the playlist's active import, so the import screen froze on the last mid-import counts (16 of 40 added for a 40-song import that fully resolved). Add a read of one import job by id, with a read-access check, and have the screen show the finished job's results from it

Tests:
- [x] Unit tests: a finished job reads by id with read access; a job can't be read through another playlist
- [x] Page test: a job that finished between polls shows its final counts

## Active playlist import requires read access

- [x] `GET /api/playlists/{playlistId}/import-jobs/active` returns a playlist's running import, with every video's title and outcome, to any logged-in user who knows the playlist id. Require read access to the playlist, the same check the finished-job read already makes

Tests:
- [x] Unit tests: the active import is refused without read access and returned with it

## CI failures after release playtest fixes

- [x] Assign the playlist invite-code migration the next unused Flyway version.
- [x] Preserve round payload type narrowing inside the session cache callback.
- [x] Validate backend migrations and frontend production compilation, then confirm PR checks pass.

## Story 24 completed backend implementation

Completed task history. Remaining requirements and tests are tracked under the same story in TASKS.md. Original baseline and completion notes are retained below.

Checked against current code: story 18 rewrote `resolve_metadata` (`ai/app/metadata/service.py`) into a conditional pipeline. The three structured sources (`musicbrainz.py`, `discogs.py`, `wikidata.py`) are gathered first, a lock is evaluated (`verification.py`, `evaluate_lock`), and only when the three do not agree is Wikipedia (`wikipedia.py`) fetched and reconciliation run. Each source is a synchronous function using synchronous `httpx` through `get_with_backoff`. Parallelization applies to the first gather only: the three structured sources take the same title and artist, share nothing, and are always fetched together before the lock check, so they run concurrently against each other. The conditional Wikipedia fetch and reconciliation stay after the gather, in story 18's order, and Wikipedia is still fetched only when the lock is not met. Each source keeps its own real, already-validated rate limiter (MusicBrainz's and Discogs' adaptive limiters, Wikidata's documented per-minute limit): parallelizing runs the three concurrently against each other, per-source pacing stays in effect underneath, this story only removes the artificial serialization between the structured sources.

Since the source functions are synchronous and `resolve_metadata` is a synchronous function that Starlette already runs in a worker thread, the concurrency primitive is a `ThreadPoolExecutor`, not an async rewrite: the three calls are network-bound, so one worker per source bounds the gather's wall-clock cost by the slowest single source instead of their sum, with no change to the source modules, their rate limiters, or their error handling. An async conversion would mean rewriting the source modules, `get_with_backoff`, and both adaptive rate limiters to async with no functional gain here, so it is not done.

- [x] Run the three structured-source fetches concurrently in the first gather (`_gather_structured_sources`) with a `ThreadPoolExecutor`, one worker per source, bounded by a named worker-count constant; each source's own adaptive or documented rate limiter stays in effect underneath the concurrency
- [x] Keep the conditional boundary intact: the lock check, the conditional Wikipedia fetch, and reconciliation stay strictly after the three-source gather completes, in story 18's order, and Wikipedia is fetched only when the lock is not met
- [x] Preserve each source's failure isolation: one source raising or timing out does not sink the others, each future is resolved through a per-source guard that logs and yields an empty result on failure, on top of the sources' own internal try/except
- [x] Preserve the synthesis ordering: the gather completes before the prompt is built and the synthesis call runs, unchanged from before

- [x] Wire the concurrent gather into story 40's on-the-spot path specifically and keep the admin backlog drain sequential: story 40 has since landed with both paths built; confirm whether they already exercise this story's concurrent gather or still need wiring into it (done: both paths call `resolve_metadata`, which gathers concurrently; the backlog drain is paced by the priority coordinator instead of running sequentially)

Tests:
- [x] Unit test confirming the three structured sources are fetched concurrently, not sequentially (controlled per-source delay, asserts total elapsed is bounded well under the sequential sum)
- [x] Unit test confirming one structured source raising does not prevent the others' results from reaching synthesis
- [x] Unit test confirming the three structured sources are gathered and passed through to the synthesis step
- [x] Unit test confirming Wikipedia is not fetched when the three structured sources lock, and is fetched after the gather when they disagree, so parallelization did not break the conditional ordering

## Story 30 completed backend implementation

Completed task history. Remaining requirements and tests are tracked under the same story in TASKS.md. Original baseline and completion notes are retained below.

Restructured to exactly two top-level modes, decided: Difficulty-Based (Auto-Generated), a card set assembled on the spot for the actual players in the group, and Custom, the player starts a session from an accessible playlist or pastes a playlist link directly. "Accessible" still covers three cases: a playlist the player owns, one they're a member of, or one someone has published for anyone to use; publishing a playlist publicly stays a real capability, not a distinction that only mattered for a dropped third mode. Theme-request generation, originally absorbed from story 21, is dropped along with story 21 itself, no on-the-spot themed generation is planned; see `PROJECT_STATE.md`.

A country/language filter dimension (a "Romanian songs only" mode alongside difficulty) is also dropped, decided against separately. A difficulty-generated set defaults to international scope instead, a song counts as international if its Wikidata sitelinks count (the number of language-edition Wikipedia articles covering it) clears some threshold, a signal already validated during the metadata-sourcing spike, not new testing.

That same sitelinks count also feeds difficulty tiering directly, not just the international-scope gate, decided: sitelinks is a real popularity proxy, a widely-covered song is a widely-recognized one, and it's available the moment a song is verified, unlike the aggregate guess-correctness score below, which needs real plays to exist first. Easy weights song selection toward higher-sitelink, more widely-recognized songs; hard carries no such weighting and can pull from low-sitelink, niche/obscure catalog entries same as any other song; medium sits between the two. This also covers the cold-start case, a newly-added song with too few real guesses for the aggregate score to mean anything yet still has a sitelinks count to place it provisionally.

Three tiers of signal feed difficulty, so this works from day one rather than waiting months for enough data:
- Sitelinks-based popularity (above) is available immediately for every verified song, no play history needed.
- A per-song aggregate difficulty score (percentage of all guesses on that song that were correct, across everyone) works as soon as a song has a handful of plays, and covers first-time players with no personal history.
- A personalized layer (collaborative filtering: for a given player and song, predict correct-or-not and roughly how fast, learned from patterns across all players and songs, same technique Netflix-style recommenders use, applied to interaction outcomes instead of ratings) only adds value once there's enough per-player history to beat the aggregate baseline. Depends on story 10 shipping and real rounds accumulating; realistically months of casual play before the personalized layer clearly outperforms the simple aggregate at this project's 100-200 user scale, see `PROJECT_STATE.md`.

Inference is cheap and local: scoring the whole catalog against a specific group's players is a small numeric comparison per song, no external API call, runs in well under a second even for a full catalog, unlike the metadata pipeline which costs money per call. The only real cost is periodic retraining, a scheduled batch job, cheap at this data scale.

Backend slice built on `feature/difficulty-generation`: the difficulty model lives in the core service (`org.dariusturcu.backend.difficulty`), computed on the fly from existing `Song` and scored `Round` data with no schema change. The play-derived signal, the three group-scoring strategies, and difficulty-tuned selection are built and unit-tested. The personalized collaborative-filtering layer is scaffolded behind an interface and deferred until real `Guess` volume exists. The sitelinks count is persisted, the generation and Custom-mode endpoints are built with the lobby frontend against them, and the `Playlist` public flag shipped narrowly ahead; see the per-task notes and `DECISIONS.md`.

- [x] Add a `SongDifficulty` aggregate view or table: per-song correct-guess percentage across all historical guesses, updated as new rounds complete (built as `RoundRepository.aggregatePlacementStatsBySong`, a grouped aggregate query over scored rounds rather than a stored table or view, so it stays current with no separate refresh and needs no migration; `SYSTEM_REFERENCE.md`'s planned `SongDifficulty` table is not needed for the on-the-fly model)
- [x] Add group-level difficulty scoring for "easy": the lowest individual predicted score among the group's actual players, not the average, so the least experienced player is protected rather than left behind by a group average that looks easy on paper
- [x] Add group-level difficulty scoring for "hard": a plain average across the group's players, no floor to protect, opt-in past the easy default
- [x] Add group-level difficulty scoring for "medium": the median of the group's individual predicted scores, a middle ground between easy's worst-case protection and hard's plain average, with no extra weighting factor to tune
- [x] Persist Wikidata's sitelinks count on `Song`, both the international-scope signal and the popularity signal for difficulty-generated sets (built as a nullable `wikidataSitelinksCount` column, `V21`, written by the AI metadata pipeline through both song persistence paths; the international-scope cutoff is 5 sitelinks and the easy/medium/hard popularity weighting rides the same count, both initial heuristics per `DECISIONS.md`, to tune once real catalog data exists)
- [x] Add the sitelinks-based popularity weighting to song selection: easy weights toward higher-sitelink songs, hard applies no such weighting, medium sits between; blends with, doesn't replace, the aggregate/personalized scoring below, and is what a newly-verified song with no real guesses yet falls back on (the weighting is built into `SongDifficultyScorer` as the cold-start fallback and blends with the play-derived signal by history weight; it produces the neutral default until the sitelinks column above lands, at which point real values flow in with no scorer change)
- [x] Add the Difficulty-Based generation endpoint: given a group, a difficulty tier, and a target card count, score the full verified catalog for the group's actual players (blending the aggregate baseline for first-time players with the sitelinks-based popularity weighting, personalized predictions plug in once trained), filter to international scope, return enough songs with headroom above the win-condition card count so a session doesn't run out or repeat (built as preview-then-confirm `POST /api/groups/{groupId}/session/generate` plus `POST .../start-with-songs` through a staged pool consumed once, covered by service and session-lifecycle integration tests)
- [x] Add an `isPublic` flag (or equivalent) to `Playlist` (coordinate with story 15), and an endpoint to publish/unpublish one (built as a `boolean isPublic` field on `Playlist`, default false, with owner-only `POST /api/playlists/{playlistId}/publish` and `POST /api/playlists/{playlistId}/unpublish` endpoints on `PlaylistController`/`PlaylistService`, enforced through the existing `PlaylistAccessService.requireOwner` check; `V15__add_public_playlists_and_saved_playlists` adds the column. `PlaylistAccessService.requireRead` now also passes for any playlist with `isPublic` true regardless of ownership or membership, which is what makes a published playlist readable and importable by anyone, see story 45. Built narrowly as its own slice ahead of the rest of this story, see `PROJECT_STATE.md` and `DECISIONS.md`)
- [x] Add a public-browse endpoint (`GET /api/playlists/public`) returning every playlist with `isPublic` true as a `PublicPlaylistSummaryDTO` (id, name, color, songCount, owner), open to any authenticated user, not owner/member-restricted
- [x] Add a "Save" capability distinct from membership: a `SavedPlaylist` entity (user, playlist, savedAt) with a unique constraint on (user, playlist), `POST`/`DELETE /api/playlists/{playlistId}/save`, and `GET /api/users/me/saved-playlists`; rejects saving a non-public playlist, the caller's own playlist, or an already-saved playlist
- [x] Add the Custom-mode endpoint: start a session from a playlist the player owns, is a member of, or that's published publicly, or from a playlist link or ID pasted directly (built as `POST /api/groups/{groupId}/session/start-custom` taking exactly one of a playlist id or a pasted link, pasted links expanding to video ids and skipping videos with no catalog song, covered by service and session-lifecycle integration tests including the pasted-link flow)

- [x] Add the frontend: a top-level choice between Difficulty-Based (Auto-Generated) and Custom; the former shows a difficulty selector (easy/medium/hard) and a review step to inspect and confirm the generated set before saving, the latter a playlist picker plus a paste-a-link field (built in the group lobby against the preview and start endpoints, covered by lobby review-and-confirm tests)

Tests:
- [x] Unit tests for the aggregate difficulty score calculation
- [x] Unit tests for all three group-scoring strategies (worst-case-protected for easy, median for medium, average for hard), including groups with a mix of experienced and first-time players
- [x] Unit test for the sitelinks-based popularity weighting: easy-tier selection biased toward higher-sitelink songs, hard-tier selection unweighted and able to draw low-sitelink songs, using a synthetic catalog with a controlled sitelinks spread (covered by the scorer tests over a controlled sitelinks spread and the selector tests over a synthetic verified catalog; the on-disk sitelinks column is not persisted yet, so the values are supplied directly through the scorer's `Optional` seam)
- [x] Unit test: a song with zero recorded guesses still gets a usable difficulty placement from its sitelinks count alone (covered against the neutral fallback while the sitelinks column is absent, and against a supplied sitelinks value through the scorer seam)
- [x] Unit tests for publish/unpublish: a non-owner is rejected the same way other owner-only playlist actions are rejected, an owner succeeds and the playlist's `isPublic` flag flips
- [x] Unit test for the public-browse endpoint: only playlists with `isPublic` true are returned, an unpublished playlist does not appear
- [x] Unit tests for the save/unsave flow: saving a public playlist succeeds, saving a non-public playlist is rejected, saving the caller's own playlist is rejected, saving an already-saved playlist is rejected as a conflict, unsaving a playlist that was never saved is rejected
- [x] Unit test: `PlaylistAccessService.requireRead` allows a non-member when the playlist is public
- [x] Migration test verifying `is_public` is not-null/default-false on `playlists` and `saved_playlists` carries a unique constraint on (user, playlist)

- [x] Integration test: Difficulty-Based generation for a full-sized group (up to 8 players) returns a scored card set in well under a second (covered for an 8-player group with a 1-second budget in `DifficultySessionStartIntegrationTest`)

- [x] Integration test: Custom mode starts a session from a pasted playlist link the player neither owns nor is a member of (covered in `DifficultySessionStartIntegrationTest`, expansion stubbed at the service boundary)
- [x] Integration test: publishing a playlist makes it selectable by a user who neither owns it nor is a member of it; unpublishing removes that access without affecting existing owners/members (covered by the public-browse, requireRead, and publish/unpublish tests rather than a dedicated integration test)
- [x] Frontend test: the review UI lets a user inspect and confirm the generated set before saving (covered by the lobby generate-for-review-and-confirm test)

## Story 40 completed backend implementation

Completed task history. Remaining requirements and tests are tracked under the same story in TASKS.md. Original baseline and completion notes are retained below.

Checked against real code: `Song` has a single `youtubeId` field and no lookup query for it, `SongRepository` has zero custom query methods. `@Scheduled` and `@EnableScheduling` are already in use in the backend (`GroupExpirySweeper`, `AnalyticsRetentionSweeper`, and the backlog drain's own sweeper), so the seeding drain is not the first scheduled job. Absorbs story 19's admin bulk-import scope, redefined as two genuinely separate mechanisms, not one:

- **Admin catalog seeding**: the admin (today, the sole developer) submits large batches of YouTube playlists or IDs to grow the catalog proactively, especially popular songs. This is patient, a multi-day backlog is fine, its whole point is reducing how often a normal user's own request needs to resolve a new song at all.
- **User on-the-spot bulk import**: any user submitting their own YouTube playlist or a list of video IDs needs those songs resolved immediately, even if some of them happen to already be sitting in the admin's backlog awaiting their scheduled turn. The two never share a queue; a song in both places gets resolved twice if the timing lines up that way, that's fine, on-the-spot always wins on priority.

Both paths depend on the same cheap first step: checking submitted YouTube IDs against the database before anything else happens. Both also depend on story 20's LLM choice for the admin backlog's scheduled drain (needs a daily quota to pace against) and on the metadata-sourcing spike's real implementation (`musicbrainz.py`, `wikidata.py`) actually existing, not just validated in `ai/spikes/`.

Story 18's verification lock (`DECISIONS.md`) changes the shape of this story's LLM dependency significantly: the story 20 spike's adversarial matrix found unanimous three-source agreement on 12 of 21 songs, meaning the LLM reconciliation call is only needed for a minority of songs, not every one. This narrows, but doesn't remove, the undecided items below.

Since story 20's spike concluded, the on-the-spot path is now two tiers, not one, both decided and validated against a real 70-song set:
- **Fast tier**: answers immediately from exactly one of MusicBrainz or Wikipedia (never both for the same song), dispatched dynamically, whichever of the two is free grabs the next song, rather than a fixed split, so a slower lane doesn't leave part of a batch waiting on it. 90% accuracy (63/70) on real data. Every fast-tier answer is provisional; the song still gets queued for the full patient pipeline afterward.
- **Patient tier**: the full lock-or-Wikipedia-plus-reconciliation pipeline from story 18. 99% accuracy (69/70). This is what the admin backlog drain always uses (no latency pressure), and what every fast-tier answer eventually gets re-run through.

Decided: how the alternate-YouTube-ID-to-`Song` mapping sequences against story 16's pgvector near-duplicate detection. This story's YouTube-ID check runs first, cheap, exact, no external calls; only a genuinely new ID reaches story 16's embedding check next. A high-confidence pgvector match against an existing `Song` means the same song under a different YouTube upload, not a new song: link the new ID into this story's alternate-ID table against that existing `Song` and stop there, no full pipeline run. The full metadata pipeline only runs once both checks come up empty.

**Decided: rate-limit contention between the admin backlog drain and on-the-spot traffic, a real concern surfaced during story 20's spike, now settled with a priority-queue design.** On-the-spot requests, including a user's playlist import, are always high priority and take precedence over the admin backlog for the shared external rate-limit budgets (MusicBrainz, Discogs, Wikidata, Wikipedia all come from the same outbound IP). The backlog drain pauses while any on-the-spot traffic is active and resumes once it's clear. Every song the fast tier resolves provisionally gets added back to the admin backlog queue afterward, to be reprocessed through the patient pipeline at low priority like everything else, this is how the fast tier's 90%-vs-99% accuracy gap gets closed, not instantly, but automatically.

- [x] Add an `ADMIN` value to `User.role` and an admin-only access check, neither exists today, absorbed from story 19; gates every admin-only endpoint below
- [x] Add a table mapping alternate YouTube video IDs to an existing `Song` (many YouTube IDs to one canonical song), separate from `Song`'s own primary `youtubeId`, so a different upload of an already-known track (a lyric video, a Topic-channel version, a re-upload) doesn't create a duplicate `Song` row or re-run the pipeline
- [x] Add a batch YouTube-ID lookup (`SongRepository` needs its first custom query methods for this): given a list of IDs, returns which are already known, checking both `Song.youtubeId` and the new alternate-ID table, no external API calls, this is the shared first step both paths below depend on

- [x] Add a `PendingImport` entity: a YouTube ID submitted by the admin for eventual processing, not yet resolved, with its own status (pending, processing, done, failed)
- [x] Add an admin-only endpoint to bulk-enqueue YouTube IDs (from a playlist link, a raw ID list, or both) into the backlog, running the batch lookup first so already-known songs never get enqueued at all. The playlist-link crawl runs through the AI microservice's `playlistItems.list` access (`fetch_playlist_video_ids`, `/metadata/playlist-video-ids`), the same access the on-the-spot path below now uses
- [x] Add a scheduled job that drains the backlog daily up to whatever the chosen LLM tier's daily free quota is (story 20), running the metadata pipeline per item and persisting results; follows the existing `GroupExpirySweeper`/`AnalyticsRetentionSweeper` sweep pattern (`@Scheduled` already exists in the backend, so this is not its first usage; the earlier task note claiming otherwise was stale). Per-item resolution delegates to the AI microservice through `SongMetadataService`; the fast/patient tier dispatch inside that service is validated-but-unbuilt in `ai/spikes/` and lands in stories 18/20/24
- [x] Add an admin view over backlog status: how many pending, how many processed today, quota remaining
- [x] Add a bulk-import endpoint open to any user (not admin-only), accepting a YouTube playlist link, a list of video IDs or links, or both
- [x] Playlist-link expansion: crawl the playlist to its video IDs through the AI microservice's YouTube Data API access (`fetch_playlist_video_ids` in `ai/app/metadata/sources/youtube.py`, paginating on `nextPageToken`, exposed as the internal `POST /metadata/playlist-video-ids` endpoint), called from the backend through `PlaylistExpansionService`; a submitted playlist link is expanded and merged (deduplicated) with any IDs or links submitted alongside it, on both the admin and on-the-spot paths. An invalid link or an upstream API failure surfaces as a `PlaylistImportException` (400) rather than importing nothing silently
- [x] Run the same batch YouTube-ID lookup first; only unresolved IDs proceed
- [x] Any unresolved ID from this path is processed immediately, independent of the admin backlog's schedule, even when the same ID is also sitting in that backlog waiting its turn
- [x] Query MusicBrainz and Wikidata first with the title/channel-derived artist (per the existing metadata-sourcing spike's design); if both return zero matches, that's the trigger for an LLM extraction pass over the raw title, channel, and description, not a subjective "does this channel look like an artist" judgment, then retry the same source queries with the corrected artist (superseded: `ai/app/metadata/service.py` runs one combined pre-check LLM call for title/artist extraction up front instead of a zero-match retry)
- [x] If the retry also comes up empty, route to manual review rather than guessing, title and artist need to be verified correct, never a confidence score the way the release year gets one (superseded with the item above: a submission no source can place routes to manual review (`verification.py`))
- [x] Once title/artist are resolved, run the fast tier first (story 18/20's dynamic MusicBrainz-or-Wikipedia dispatch, `ai/spikes/run_fast_tier_dispatch.py`) to answer immediately, then enqueue the same song into the admin backlog at low priority so the patient pipeline (story 18's lock-evaluation logic) resolves it properly afterward (the backend orchestration is in place: on-the-spot resolves immediately and re-enqueues for patient reprocessing; the fast/patient tier dispatch itself lives in the AI microservice and is unbuilt, stories 18/20)
- [x] Implement the priority queue itself: on-the-spot requests (including playlist imports) are always high priority against the shared external rate-limit budgets (MusicBrainz, Discogs, Wikidata, Wikipedia, one outbound IP); the scheduled backlog-drain job (above) pauses while any on-the-spot traffic is active and resumes once it clears, rather than the two paths contending for the same rate-limit budget in real time
- [x] Depends on story 24: run the three structured sources' fetches concurrently rather than sequentially for the on-the-spot path specifically, where a user is waiting on the result; the admin backlog drain has no such latency pressure and can stay sequential if that's simpler to build first (done: `resolve_metadata` gathers the structured sources concurrently (`ThreadPoolExecutor`), on both the on-the-spot and backlog paths)
- [x] Coordinate with story 23: `metadataRaw` should persist the curated, actually-used subset of each source's response, not the full raw API response, Wikidata's own entity dumps alone ran into the tens of KB per song during this spike's testing; at that size the 500MB Supabase free-tier cap holds roughly 10,000-50,000 songs instead of 170,000+ with a curated version (not applicable yet: nothing writes `Song.metadataRaw`; revisit if it starts storing source responses)
- [x] Raw YouTube API Data specifically (a video's title, description, channel name) has its own constraint on top of the size one above, coordinate with story 43's metadata-minimization rule, one limit on size, this one on retention: YouTube's Developer Policies (Section III.E.4) require non-authorized API Data to be deleted or refreshed within 30 calendar days, it can't be persisted indefinitely as-is. If any raw YouTube fields end up inside `metadataRaw`, they need their own refresh/delete cycle on that schedule; the derived facts (artist, title, release year, sourced from MusicBrainz/Discogs/Wikidata/Wikipedia) aren't YouTube API Data and aren't subject to this (not applicable yet: nothing writes `Song.metadataRaw`, so no raw YouTube API data is persisted there)
- [x] Backend: live per-song progress for the on-the-spot bulk-import path over a new per-user STOMP destination, `/user/queue/bulk-import-progress`, rather than only the request's final response. `BulkImportService` publishes a `BulkImportProgressEvent` (video id plus outcome: already-known, resolved, or unresolved) for every submitted id as its loop reaches it; `BulkImportProgressListener` forwards each one through `SimpMessagingTemplate#convertAndSendToUser`, the per-user counterpart to `GroupBroadcastListener`/`SessionBroadcastListener`. `WebSocketConfig` registers `/queue` as a broker prefix alongside `/topic`, required for per-user delivery to route at all. The admin catalog-seeding backlog does not get this: `AdminCatalogBacklogDark.dc.html` shows a backlog-count and quota summary, not live per-item progress, so that endpoint stays synchronous
- [x] Frontend, user-facing path only: the playlist-link crawl runs in the background rather than blocking the import screen. Leaving the screen doesn't cancel it: a temporary icon appears in the left sidebar (below Group lobby) while an import is active, and a toast appears once and fades after a few seconds; both reopen the import screen showing live per-song progress (raw YouTube title/channel updating in place to the resolved title, artist, and year as each one finishes), subscribing to the `/user/queue/bulk-import-progress` destination above. Surfaced during story 28's design pass. Story 45's from-an-existing-playlist path is synchronous and needs none of this (done: the background import job, the sidebar import icon, and the resumable import page)

Tests:
- [x] Unit tests for the admin-only access check, including a non-admin request rejected
- [x] Unit tests for the batch YouTube-ID lookup, including a mix of known, alternate-mapped, and unknown IDs in one batch
- [x] Unit tests for the alternate-YouTube-ID-to-`Song` mapping

- [x] Integration test: admin backlog enqueue skips already-known songs, only genuinely new IDs get added
- [x] Integration test: the scheduled drain job respects the daily quota and doesn't exceed it
- [x] Integration test: a user's on-the-spot request resolves immediately even when the same YouTube ID is also sitting in the admin backlog
- [x] Unit tests for the artist/title verification trigger: a zero-match escalates to LLM extraction, a successful retry clears verification, a failed retry routes to manual review rather than auto-approving (superseded with the artist/title verification items above)
- [x] Integration test: an on-the-spot song resolved by the fast tier gets re-enqueued into the admin backlog afterward, and later resolves through the patient pipeline without being skipped as already-done
- [x] Integration test: the backlog-drain job pauses while on-the-spot traffic is active and resumes once it clears, doesn't contend with on-the-spot requests for the same external rate-limit budget
- [x] AI microservice unit tests for `fetch_playlist_video_ids`: pagination across multiple pages, a playlist with zero items, the first page failing (raises `PlaylistFetchError`), a later page failing (stops and returns the partial result)
- [x] AI microservice unit tests for `extract_youtube_playlist_id`: a bare playlist id, a playlist URL's `list=` param, a watch URL's trailing `list=` param, and a plain video URL with no `list=` param rejected
- [x] AI microservice router tests for `POST /metadata/playlist-video-ids`: a successful expansion, an invalid playlist link (400), an upstream fetch failure (502), and the internal-API-key gate
- [x] Backend unit tests for `PlaylistExpansionService`: a successful expansion, an AI-service call failure, a null `video_ids` response, and `expandAndMerge`'s dedupe behavior with and without a playlist link
- [x] Backend unit tests for `BulkImportService`'s playlist-expansion-and-merge behavior, its re-enqueue-on-resolve behavior, one progress event published per submitted id with the right outcome, and a playlist-expansion failure propagating rather than importing silently
- [x] Backend unit tests for `CatalogSeedingService`'s equivalent playlist-expansion-and-merge behavior on the admin enqueue path
- [x] Backend unit test for `BulkImportProgressListener` routing an event to the right user's progress queue
- [x] Backend integration test proving the per-user STOMP wiring actually works against the real STOMP auth setup: subscribing to `/user/queue/bulk-import-progress` registers the session against the authenticated user in the real `SimpUserRegistry`, and publishing a `BulkImportProgressEvent` routes through the real broker without error
- [x] Frontend test: leaving and reopening the import screen mid-crawl (via the sidebar icon or the toast) shows the same live progress, not a reset state (done: the import page test "resumes the progress view from a stored job without starting over")

## Story 41 completed backend implementation

Completed task history. Remaining requirements and tests are tracked under the same story in TASKS.md. Original baseline and completion notes are retained below.

Checked against real code: `_append_youtube_data` in `prompt.py` already delimits the video description and instructs the LLM to treat it as data, not instructions, the only defense that exists today, and only on the final synthesis call. Applies to every submission path, not just story 40's bulk import, story 40 just raises the exposure by opening submission to any user's arbitrary YouTube content instead of only what's manually added one at a time today.

Two things settled through discussion:
- Reject compilations outright, even genuinely musical ones, a compilation isn't a single song and has no one correct answer for a round.
- Precision over recall on the non-music gate: a false reject is a cheap, recoverable resubmit or manual override; a false accept puts non-music content in front of a player mid-game, a worse and more visible failure. This project already holds itself to a "professional-grade, not just working" bar (`DECISIONS.md`).

- [x] Add a hard pre-pipeline filter, no LLM involved: reject when duration falls outside a generous song-length window (roughly 1-12 minutes) combined with YouTube's own `categoryId` not being Music (10), already-fetched data, no extra API cost
- [x] For the ambiguous remainder, non-Music category but song-length duration, add an LLM classification pass (structured output: `is_song`, `is_compilation`, confidence, reasoning) reading title, channel, and description for song-like versus gameplay-like signals

- [x] The combined structured precheck extracts title/artists/color and returns injection and song/compilation flags. Safety evaluation rejects flagged results before source gathering and year reconciliation
- [x] Full resolution and fast identification share this precheck. A verified duplicate can return earlier. No separate detector runs before every LLM call
- [x] Decided: a flagged injection attempt writes an abuse-visibility event (story 34's scope, alongside rate-limit-exceeded and report-submitted events), an attempted injection is evidence of intent, not just an uncertain submission, so it's tracked, not silently handled the same as an honestly ambiguous song. Depends on story 34's event pipeline existing. Whether the submission itself is also outright rejected, versus routed to manual review, still needs a call, not yet made
  - Settled and built: the submission is rejected outright (`DECISIONS.md`, 2026-09). The event write is a stubbed structured log line marked `TODO: story 34` until story 34's event pipeline ships

Tests:
- [x] Unit tests for the hard duration+category filter, including the boundary values of the song-length window
- [x] Unit tests for compilation rejection
- [x] Unit tests for the injection-detection check (mocked LLM call): flags known injection patterns, passes clean text through unaffected
- [x] Integration test: a submission through any path, single-song or bulk, that fails classification never reaches the full metadata pipeline
  - Covered for the resolve path: a rejected submission is asserted never to reach source gathering or the synthesis call. Story 40's bulk-import path has since landed but doesn't have this same integration test coverage yet

## Dropped story 26: Cache metadata pipeline results by artist/title or YouTube ID

Scope review: two other mechanisms already cover a chunk of what a cache would. Story 40's batch YouTube-ID lookup catches an already-known exact ID before the pipeline runs at all, no external calls, no LLM. Story 16's pgvector similarity check catches a near-duplicate submission (different wording, same song), which a plain artist/title or YouTube-ID cache key would miss anyway since it isn't an exact-key match. What a cache layer adds on top of both: avoiding a second full pipeline run for the same exact YouTube ID submitted twice in quick succession, before story 40's alternate-ID mapping exists to catch it structurally, or during a burst where both submissions arrive before the first is persisted. That's a narrower case than the story's original framing suggested.

- [x] Decide whether the narrower exact-repeat case needs its own cache (dropped: story 40's batch lookup and story 16's pgvector check cover the intended value)
- [x] Add a cache layer in front of `resolve_metadata` (dropped with the story)
- [x] Decide cache backend (not needed because the story is dropped)
- [x] Set a TTL or invalidation policy (not needed because the story is dropped)
- [x] Coordinate with story 16 (superseded by the documented story-16 and story-40 behavior)

Tests:
- [x] Unit tests for cache hit/miss behavior (not needed because the story is dropped)
- [x] Unit test for TTL expiration (not needed because the story is dropped)

## Story 28: Lobby light mockup synchronization

- [x] `GroupLobbyLight.dc.html` still reflected an older lobby layout: a sticker-card player list with stat rows and pill badges, where `GroupLobbyDark.dc.html` uses an orbiting-avatar layout with drift-animated positions and lobby-btn controls. Rebuilt to match; its `GroupLobbyChat`/`GroupLobbyEightPlayers`/`GroupLobbySettings`/`GroupLobbyTwoPlayers` variants were already in sync. Every `*Light.dc.html`/`*Dark.dc.html` pair in `docs/design/source/` is now structurally in sync, colors translated to this project's light palette rather than copied from dark.

## Story 28: Batch F: component boundary and accessibility

- [x] Keep shadcn primitives for interactive controls and add focused primitives only where the current set has a gap
- [x] Improve keyboard-visible focus, controls, and readable opaque surfaces for the chat overlay and voice sidebar
- [x] Audit the affected light and dark theme colors against WCAG AA text contrast

Tests:

- [x] Run frontend lint and production build

## Story 28: Marketing recovery and design-source synchronization

- [x] Recover the landing and auth mockup-fidelity implementation from the preserved 2026-09-17 checkpoint, without importing unrelated Batch A changes
- [x] Render the recovered landing and login pages against their Dark desktop and mobile mockups and correct any remaining layout differences
- [x] Reconcile the structurally divergent Light mockups with their authoritative Dark counterparts: `GameSessionDJ`, `JoinInvite`, `PlaylistDetail`, and `SongDetail`
- [x] Synchronize `docs/design/hittiguess-design.html`'s embedded source files with `docs/design/source`

Tests:
- [x] Verify every embedded mockup entry matches its source file after synchronization
- [x] Capture the recovered landing and login at their mockup viewport sizes in both themes

## Story 28: Marketing interaction polish

- [x] Remove the colored underlines from the three landing feature paragraphs
- [x] Make the landing and auth theme slider animate its thumb and page colors, with direct Light and Dark selection
- [x] Add the shared theme slider and a landing-page link to every authentication screen
- [x] Match the auth primary and Google button dimensions to the mockup controls
- [x] Make the wordmark waveform smoother, slower, and less repetitive

Tests:
- [x] Verify both theme choices on landing and authentication pages and capture the animated slider states
- [x] Render login and register in both themes and compare their control proportions with the mockups

## Story 28: Marketing theme-switch correction

- [x] Replace the two-choice theme control with a single full-track toggle that switches theme from any click target
- [x] Match the switch track and moving thumb proportions without clipping on landing or auth surfaces

Tests:
- [x] Verify a click on both ends and the center of the switch toggles the theme once

## Story 28: Marketing theme-switch animation

- [x] Animate the compact toggle thumb between its Light and Dark positions independently of the page color transition

Tests:
- [x] Verify the thumb visibly slides in both directions

## Story 28: Global scrollbar styling

- [x] Add a theme-aware custom scrollbar used by every page and reserve its layout gutter to prevent content shifts

Tests:
- [x] Verify the scrollbar track and thumb in both themes on a scrollable page

## Story 28: Global scrollbar refinement

- [x] Remove the scrollbar gutter from non-scrollable pages and make the scrollbar track transparent

Tests:
- [x] Verify scrollable and non-scrollable pages retain their intended layout in both themes

## Story 28: Marketing hydration and CI correction

- [x] Render the saved theme only after hydration so the server and client switch markup match
- [x] Initialize waveform variation after mounting to keep component render pure

Tests:
- [x] Run frontend lint and production build, then confirm the browser has no hydration errors

## Story 28: Global overlay scrollbar

- [x] Replace browser-owned scrollbars with a compact global overlay thumb that does not consume content width

Tests:
- [x] Verify the overlay appears only on scrollable pages and remains over content in both themes

## Completed LAN playtest readiness

Chore, no story: opening the local stack to other devices on the LAN for
multi-device testing before deployment. Confirmed against the code that no
behavior change is needed: all API and WebSocket URLs derive from
`NEXT_PUBLIC_API_URL`, invite links use `window.location.origin`, cookies
carry no `Domain`, and CORS plus email-link hosts are env-driven. Google
sign-in is unavailable over LAN; testers use local accounts.

- [x] Document the three LAN env values in both `.env.example` files
- [x] Add a local-network playtest section to `docs/DEV_SETUP.md`
- [x] Allow the dev server's LAN origin: Next.js blocks non-localhost origins on dev-only assets (`/_next/*` chunks return 403 with no client JS at all), so `next.config.ts` derives `allowedDevOrigins` from `NEXT_PUBLIC_API_URL` instead of hardcoding a machine-specific IP

- [x] The lobby's `min-h-[720px]` on `<main>` still pushed the footer actions below the fold on a short or zoomed window (verified at 1280x600, despite batch 3 recording it fixed). Give the member stage its own minimum height and let it scroll inside the middle section instead
- [x] When the admin and another member leave at the same moment, the admin's leave promotes a member whose row the other leave just deleted, and the request fails with a 500. Lock the group row while leaving, the same way joins do
- [x] The two lobby e2e specs seed fewer songs than a session now needs (players times the win condition), so their start-session steps fail. Seed enough songs
- [x] The story 47 e2e still looks for the per-song "Resolving song details..." rows that the inline import banner replaced. Assert the banner and its link to the import page instead

Tests:
- [x] e2e: the lobby's footer actions stay in the viewport at 1280x600
- [x] Integration test: an admin and a member leaving at the same moment both succeed
- [x] Fix the pre-existing session page test type error blocking `npm run build` (found during LAN verification, untouched by the LAN branch; the mock session type now allows a null round, build passes)
- [x] Hydration warning on the login page over LAN: caused by the Dark Reader extension rewriting SVG attributes before React hydrates, not by app code; extension-free browsers hydrate cleanly. No code change; disable Dark Reader for the site since it also fights the app's own theme toggle
- [x] Lobby start flow rework from playtest feedback: playlist chip opens a tier popup (easy/medium/hard/custom) instead of a separate modal; custom opens a fullscreen multi-playlist picker with a chosen list and a back path; the standalone custom-start entry point is gone; the two-player minimum shows only as a popup on Start instead of a persistent note; settings save reports success or failure by toast

## Completed LAN playtest findings (PC client vs laptop server)

Reported during the first multi-device playtest. Items marked reproduce-first may be stale-bundle symptoms from before the `allowedDevOrigins` fix; verify each against the fixed stack before changing code.

Confirmed bugs (verified in code, fix directly):
- [x] Clipboard copies assume `navigator.clipboard`, which is undefined over plain-HTTP LAN, so every invite and results copy throws. Add a shared copy helper with a non-Clipboard fallback and use it at all five call sites
- [x] Export renders as an inline section, not a dialog, with no preview and only info/QR plus A4/Letter. Convert to a dialog (done); combined info+QR output and extra paper sizes need backend support and land separately (see ARCHIVE.md's Fix: Export paper sizes, combined info+QR output, and playlist description backend section)
- [x] Library tabs run Owned-first with All last. Move All first and default it; put Join left of Create playlist with a code/link popup below it
- [x] Edit-playlist save has no toast and fails silently; cancel gives no feedback. Add success toast with detail redirect (already redirects) and an error message
- [x] Playlist description has no backend support at all (no column, no update field), so the edit-page description field silently drops input. Needs an entity/migration/endpoint slice before the field can work (see ARCHIVE.md's Fix: Export paper sizes, combined info+QR output, and playlist description backend section)

Reproduce-first on the fixed stack:
- [x] YouTube playlist import stuck at connecting (real bug, not stale-bundle: the page never opens the socket it gates on. Fixed by dropping the gate since expansion is REST)
- [x] Add-by-YouTube-link metadata fetch failures (fixed and verified end to end on LAN in batch 2, see `ARCHIVE.md`: the failures were the AI service being down plus the add page's queue-hook 500)
- [x] Export download and print doing nothing (verified working on LAN in batch 2, see `ARCHIVE.md`: the failures were a stale bundle)
- [x] Edit-playlist save and cancel reported dead (covered by the save fix above; cancel resets the name draft, description waits on backend support)

Lobby redesign (own batch):
- [x] Join-call button placement (settled in batch 3, see `ARCHIVE.md`: the slim rail keeps the join action on top)
- [x] No sidebar collapse control; hide the right sidebar outside calls except on the group lobby page; match the left sidebar width (done in batch 3, see `ARCHIVE.md`)
- [x] Playlist selection as a near-fullscreen overlay with bottom lobby actions still visible (superseded by batch 3's tier popup and fullscreen custom picker, see `ARCHIVE.md`)
- [x] Difficulty options (easy/medium/hard/custom) directly in playlist selection; custom opens the fullscreen multi-playlist picker with a back path; remove the standalone custom start (done in batch 3, see `ARCHIVE.md`)
- [x] Two-player minimum only as a popup on Start, never persistent (done in batch 3, see `ARCHIVE.md`)
- [x] Lobby content shrinks on zoom instead of pushing bottom actions off screen; avatar and name animate as one unit (avatar and name done in batch 3; the zoom half didn't hold and is fixed below)

Library and shell redesign (own batch):

- [x] Sidebar logo animation genuinely random per sound-wave input, not a fixed loop (done in batch 4 below)
- [x] Group nav button only when in a group, stronger highlight on the group page, Play routes into the existing group (done in batch 4 below; Play already opens the active group)
- [x] Playlist member stack matches the uploaded reference (overlapping avatars plus overflow count) (done in batch 4 below)

Settings and chat (own batch):
- [x] Restyle settings dropdowns off the native control look, verified by screenshot (done in batch 4 below)
- [x] Chat panel floats above its button like the settings panel does (done in batch 4 below)

Feedback polish (cross-cutting, own batch):

Audited against the real code: every page-level `useGet*` query already renders a spinner, `animate-pulse` block, or placeholder text while loading except the two gaps below; most mutations show a pending state on their triggering control but skip the toast half; there's no exit-animation tooling (no `framer-motion` or equivalent) anywhere in the repo, so this batch adds entrance motion via the already-used `tw-animate-css` `animate-in`/`fade-in-0` utilities (the same primitive every shadcn overlay already uses), not new exit-animation infrastructure.

- [x] Loading: the playlist edit page renders `useGetPlaylist`/`useGetMembers` with no `isLoading` handling at all, showing blank name/color/description fields and an empty member list on first paint. Add a loading skeleton for the whole page
- [x] Loading: the admin catalog backlog's stat tiles swap to a bare `"..."` string while loading; use the existing shadcn `Skeleton` component instead
- [x] Loading: the explore and library pages showed bare "Loading..." text; swapped both for a `Skeleton` card grid matching the loaded layout's shape
- [x] Toasts: playlist edit page's `uploadCoverImage`, `selectColor`, `togglePublish`, and the invite-link copy button; `MemberRow`'s `toggleGrant`/`kickMember`/`banMember`
- [x] Toasts: explore page's save mutation (no unsave action exists on this page, a saved card just shows disabled)
- [x] Toasts: group lobby's `startSession` and `leaveGroup` mutations had no error feedback at all (button just stopped spinning on failure); `generateSet`/`startWithSongs`/`startCustom` already show errors inline via `startError` and their successes navigate or open a visible review step, so those were left as-is rather than adding a redundant toast
- [x] Toasts: admin catalog backlog's enqueue mutation; admin report queue's resolve/dismiss mutations
- [x] Toasts: copy-from-playlist import mutation gets a success toast naming the count added (`isError` already renders inline and the YouTube-link expand mutation's failure already renders inline too, so neither needed a toast on closer look, just the copy-from-playlist success case was genuinely silent)
- [x] Toasts: group and playlist join-by-invite mutations already render their failure inline (`setError`) and their success navigates, so no toast was actually missing here on closer look, corrects the earlier audit
- [x] Motion: apply `animate-in fade-in-0` (with `slide-in-from-top-1` where a list is involved) to the playlist edit page's loaded content, the admin backlog and report queue lists, and the explore/library grid, matching the existing Radix overlay convention rather than inventing a new one

Tests:
- [x] Frontend lint and the two password-flow page tests stay green
- [x] Frontend tests for the new toast coverage on the playlist edit page, the explore save action, and the admin backlog/report queue actions
- [x] Frontend test for the playlist edit page's loading skeleton rendering before `useGetPlaylist` resolves

## Completed LAN playtest findings, batch 4 (library, shell, settings, chat)

- [x] Playlist member stack shows the overlap plus overflow count per the reference
- [x] Explore call-to-action carries an icon
- [x] Sidebar logo bars randomize per mount instead of a fixed loop
- [x] Group nav button exists only in a group, stronger highlight on the group page
- [x] Settings dropdowns use the shadcn Select instead of native controls
- [x] Lobby chat floats above its button

Tests:
- [x] Stack overflow, Radix settings selects, group button rules, floating chat variant, plus screenshot verification of settings and chat

## Release playtest fixes: completed implementation slice

The following implementation exists in the current source. This record does not close browser rendering, reconnect recovery, live TURN/audio acceptance, or the remaining test tasks.

- [x] Keep the admin crown attached to the actual admin while lobby state refreshes
- [x] Sum all selected playlist song counts in the lobby selector and selected-playlist chip
- [x] Restore Join playlist as the Joined-library end tile
- [x] Replace playlist UUID invite codes with short unique letter codes and copy only the
  requested link or code
- [x] Drive every visual countdown from server deadlines, including the round intro
- [x] Surface actionable audio diagnostics for HTTP LAN origins and unsupported tab-audio
  browsers without weakening the real-YouTube playback rule
- [x] Stable lobby member sorting, member-ID-derived initial colors, and real avatar images are implemented in the lobby page; the final name-length and refresh acceptance checks remain open
- [x] The tier picker uses Confirm and contains Easy, Medium, Hard, and Custom with icons and a divider; the single-row layout acceptance check remains open
- [x] Lobby Chat, Settings, and playlist popups close competing popups and respond to outside clicks; final positioning and interaction acceptance remains open
- [x] Playlist detail exposes Import playlist inside Add song and uses the wider search control; rendered action-discoverability acceptance remains open
- [x] Session round changes clear placement and link-out state, and round events invalidate session, guess-state, and DJ link-out queries; recovery after missed disconnected events remains open
- [x] TURN credential minting and STUN-only failure fallback are implemented and covered by TurnCredentialsServiceTest; real configured TURN relay verification remains open

Existing tests cover Confirm and outside-click lobby interactions, round-event query invalidation, session intro states, and mocked TURN response/fallback behavior. The combined test requirements remain active until all their requested cases are covered.

## Completed roadmap implementation history

Historical roadmap summaries and the completed frontend implementation batch plan. Remaining verification and deployment work is tracked in ROADMAP.md and TASKS.md. Original milestone claims below describe the earlier roadmap rather than current acceptance status.

### Shipped implementation summary

Story 39 (group), story 10 (game session), story 11 (WebSocket sync), story 9 (DJ link-out), story 12 (voice chat), story 13 (group text chat), story 14 (song search), story 17 (community reports and confirmations), story 18 (verification promotion), story 24 (parallelized metadata fetches), story 30 (difficulty-tuned generation, including public playlists), story 35 (ground-truth API), story 37 (privacy/GDPR), story 40 (catalog seeding and bulk import), story 41 (submission content safety), story 45 (playlist-to-playlist import), and story 46 (playlist membership) are all `Implemented` on the backend. So is nearly everything else from the original Phase 0/1 backlog (song schema reconciliation, the relational fix, pgvector duplicate detection, Discogs sourcing, rate limiting, the analytics data store, open-source collaboration files, test user infrastructure, the database split and metadata-minimization cross-references, the DeepInfra LLM client). Stories 22 (test coverage), 48 (comment cleanup), and 49 (naming consistency) have shipped and archived in full, and their rows are removed from `PROJECT_STATE.md`'s table entirely. See `ARCHIVE.md` for the full completed list and `PROJECT_STATE.md` for each remaining story's row.

Story 28 now contains the implemented frontend surface for those backend stories through Batch F, and story 47's build clusters have landed on top of it. The remaining work is verification and the explicitly open states listed in `TASKS.md`, not a second frontend implementation pass for every backend story.

### Completed story 28 implementation batches

The old batch plan sequenced one story per PR through Phase 1 and Phase 2; that work is done; most of those stories landed out of the order that plan originally picked, through branches it never named, and the plan itself stopped tracking reality several batches ago. It's replaced here with a batch plan for the one piece of sequential work actually left: story 28's implementation phase, broken into the same screen clusters `docs/design/hittiguess-design.html` and `FRONTEND_CONTENT.md` already group the 53 mockups into, each batch wiring its cluster against the real backend the stories above already shipped rather than mock state.

Per `AGENTS.md`'s workflow, each batch is still its own branch off `dev` and its own PR; several may be open for review at once, and the project owner reviews and merges them asynchronously. Update this list's checkmarks as each batch's PR merges into `dev`.

- [x] Batch A: Existing pages redesign, with the new visual system implemented across auth, landing, the shared shell, and playlist/song pages. Route smoke and rendered comparison remain open.
- [x] Batch B: Playlist screens, with Explore, Edit playlist, YouTube import, and copy-from-playlist implemented, including processing, progress, sidebar, and toast states. The choose-source and existing-playlist loading, empty, and error states remain open, along with route smoke and rendered comparison.
- [x] Batch C: Song review and search, with `AddSongForm.tsx` link and keyword search, review states, report, and confirmation actions implemented. Broader search/report state coverage and rendered comparison remain open.
- [x] Batch D: Admin views, with the catalog backlog and report review queue implemented behind the `ADMIN` route guard. Auth/action coverage, route smoke, and rendered comparison remain open.
- [x] Batch E: Gameplay screens, with the group lobby, game session/timeline, betting, DJ link-out and audio cutoff, voice/WebRTC, text chat, turn notification, away widget, and results/leaderboard implemented against the real backends. The visual matrix, representative-state tests, and accessibility checks remain open. Live Playwright gameplay validation requires the backend services to be started from this same checkout.
- [x] Batch F: Cross-cutting implementation tasks, the component/token boundary decision (shadcn primitives versus outright replacement), and the accessibility pass (color contrast, keyboard navigation, the semi-transparent chat overlay and voice sidebar specifically)

## Resolved project-state questions

- Where is the current Postgres instance hosted? Supabase, confirmed.
- Beta deployment: Vercel hosts the frontend, Azure Container Apps Consumption hosts the Spring core and FastAPI AI service, and Supabase remains the database provider. The two services use separate Container Apps in one environment; the core app has external HTTPS/WebSocket ingress while the AI app stays internal-only. A fixed-price Hetzner deployment remains the migration path if sustained Azure usage exceeds the whole-deployment cost ceiling. See stories 7 and 8 in TASKS.md.
- Beta database layout: two new Supabase projects, one transactional Postgres+pgvector database used by both backend services and one append-only analytics Postgres database used only by the Spring core. No database-per-service split is planned.
- Metadata source API usage: source set is MusicBrainz, Discogs, Wikidata, and Wikipedia; Genius, Last.fm, and iTunes were reviewed and dropped rather than fixed (see `docs/DECISIONS.md`).
- Compliance and production-readiness: privacy policy, terms of service, GDPR compliance, and observability were a stated goal in `docs/VISION.md` with no story attached. Now stories 37 and 38.
- Story 10's session-state persistence and win-condition scaling were open questions. Both resolved: ephemeral Postgres rows, and an admin-configured card count bounded by player count. The group/game-session split that resolved them is documented in `ARCHITECTURE.md` and `GAME_DESIGN.md`, and logged in `DECISIONS.md`. Now story 39.
- Flutter app: keep. In the meantime it follows the same real-link-out rule as the rest of the product, opening the real YouTube app instead of an embedded or hidden player, no exceptions.
- The community verification/report system: report resolution stays fully manual, an admin decides every case, nothing auto-changes `verificationStatus`. The admin review queue ranks by a five-tier priority order (converging reports first, then non-converging reports, then confirmed-but-unreported cards, then unconfirmed cards, `VERIFIED` cards with no report never appear at all), not submission time. See `DECISIONS.md`.
- Story 23: release year is one mutable field plus `verificationStatus`, not a separate `submittedYear`/`verifiedYear` pair. Once verified, the field doesn't change except through the same review process that verified it in the first place.
- Story 30: medium-difficulty group scoring uses the median of the group's individual predicted scores, a middle ground between easy's worst-case protection (lowest individual score) and hard's plain average, with no extra weighting factor to tune.
- Story 32 (periodic LLM catalog audit) is dropped, so its relationship to story 18 no longer applies: every song already flows through story 18's verification pipeline on submission, and again through the patient tier after the fast tier answers, making a separate scheduled audit pass redundant.
- Story 35: read YouTube's actual Developer Policies directly rather than a summary. Section III.E.4.h prohibits substituting or deriving new metrics from YouTube's own numeric/engagement data (its own example is about view/like counts), it does not restrict publishing independently-sourced facts (artist, title, release year, all CC0 from MusicBrainz/Discogs/Wikidata) merely because a YouTube video's title or channel name was used as a lookup key to find them. Story 35 itself is clear to ship as designed; see the new open item above for a related but separate constraint this same research surfaced.
- Story 23: a song's artists are an ordered list, each tagged `MAIN` or `FEATURED` (more than one `MAIN` artist is allowed, a joint credit like "Queen & David Bowie" has two, neither featured). The role tag is display-only, guessing and scoring treat every artist on the list identically. A remix, cover, or mashup clause is the only thing that survives the AI microservice's title-cleaning pass, a `(feat. X)` clause instead gets stripped and extracted into the artist list; each remix/cover/mashup becomes its own separate `Song`, not a variant of the original.
- Story 30: a country/language filter dimension is dropped. A difficulty-generated set defaults to international scope instead, using Wikidata's sitelinks count (already validated during the metadata-sourcing spike) as the signal. That same sitelinks count also weights difficulty tiering directly (easy leans toward higher-sitelink, more widely-recognized songs; hard carries no such weighting), not just the international-scope gate, doubling as a cold-start proxy before a song has enough real guesses for the aggregate score to mean anything. Playlist selection is exactly two top-level modes, Difficulty-Based (Auto-Generated) and Custom (a playlist the player owns, is a member of, or that's published publicly, or one pasted directly); theme-request generation is dropped, publishing a playlist publicly stays a real capability, see the 2026-09 `DECISIONS.md` entry.
- Story 10: every player except the DJ, the active player included, can guess a round's title/artist. Only the active player's guess affects tokens; every guess, active or not, feeds two session-long leaderboards ("Most Artists Guessed," counting every correct individual artist name regardless of a song's total artist count, and "Most Titles Guessed"), shown alongside the main card-count ranking at session end.
- Typo tolerance for in-round artist/title guesses (see `GAME_DESIGN.md`'s Earning tokens section): normalize both the guess and the canonical answer (lowercase, strip punctuation, strip diacritics, collapse whitespace) and compare with Damerau-Levenshtein edit distance, a flat budget of 1 regardless of title length. See `DECISIONS.md`.
- Database split: the project has one database split worth defining explicitly, the boundary between the core transactional Postgres+pgvector instance and story 33's separate analytics/event store. Now story 42. No second transactional database (e.g. one per service) is planned.
- Story 37's legal/static pages: exactly two, Privacy Policy and Terms of Service. A cookie-consent page stays gated on story 34 shipping, no other legal or static page is planned.

Current product rules are maintained in GAME_DESIGN.md, ARCHITECTURE.md, and SYSTEM_REFERENCE.md. DECISIONS.md retains the decision history. Contradictions listed in the documentation audit remain pending review.

## Documentation audit: 2026-10-05

- [x] Identify completed work retained in active docs and distinguish archive candidates from valid open follow-ups
- [x] Compare design docs and mockups with frontend, backend, and AI implementation; record conflicts and omissions without choosing a source of truth
- [x] Identify incomplete documentation claims contradicted by implementation
- [x] Validate report references and distinguish source evidence from runtime verification

Findings and source references are recorded in `docs/DOCUMENTATION_AUDIT_2026-10-05.md`. Archive and source-of-truth changes remain subject to the owner's review.

## Story 10: Game session

Checked against real code: no session model exists, this is greenfield work. Based on the `GameSession` shape and round flow in `ARCHITECTURE.md`, and the round/token/reconnect rules in `GAME_DESIGN.md`.

Built on `feature/game-session`, stacked off `feature/websocket-sync`. The gameplay frontend is implemented in story 28; its visual matrix, representative-state tests, and accessibility checks remain open. See `DECISIONS.md` for the round-rotation, timer-scheduling, and betting-concurrency design choices this batch resolved.

- [x] Implement `GameSession`, `Player`, `Round`, and `Guess` as ephemeral Postgres rows, purged when the session ends
- [x] Initialize a session from the group's current settings when the admin starts it (playlist(s), DJ mode, win-condition card count), snapshotting the group's connected members as the roster
- [x] Assign round 1's active player and DJ
- [x] Turn rotation: the active player rotates each turn, and a round completes after every eligible active player has taken a turn. The DJ stays fixed or rotates per the group's setting, skipping players marked `Left`
- [x] Guess placement and lock-in: before/after/between on the active player's timeline. Lock-in sound effect is a frontend concern, not built this batch (backend only)
- [x] 3-5 second countdown after lock-in, then a 15-second betting window; skip the window entirely if no player holds a token
- [x] Betting: every token-holding player may bet during the window, at the same time as each other, each on a different gap in the active player's own timeline than the one the active player just locked in, one bet per interval rather than one bet per round; concurrency-safe so only the first bet on a given gap is accepted and a losing attempt doesn't cost a token; a player still can't bet twice in the same round; a skip-betting action ends the window early
- [x] Automatic reveal once the betting window closes: broadcast the song's artist, title, and year to every player, off the same window timer, with no DJ or player action triggering it
- [x] Artist/title guess box, available to every player except the DJ during placement until lock-in, independent of timeline placement; only the active player's fully correct guess awards a token, matching normalizes both strings (lowercase, strip punctuation, strip diacritics, collapse whitespace) and compares them with Damerau-Levenshtein edit distance, a flat budget of 1 regardless of length (see `DECISIONS.md`). For a song with more than one artist (main or featured, story 23), naming any single one of them correctly is enough for the token, not all of them
- [x] Scoring: apply the four outcome rules in `GAME_DESIGN.md` (correct placement keeps the card even on a tied release year, and every bet is lost regardless; a wrong placement with a bet sitting on the gap that's objectively correct for the active player's timeline gives the card to that bettor's own timeline, inserted at wherever it objectively belongs there; a wrong placement with no bet on the correct gap, whether no one bet there or no one bet at all, discards the card)
- [x] Track two running per-player tallies for the session, fed by every player's guesses, active or not: total individual artists correctly named (every correct name, main or featured, from any song, adds one, regardless of how many total artists that song has) and total fully-correct title guesses. A non-active player's guess never earns a token or affects placement/betting, it only feeds these two tallies
- [x] Win condition: the current full-pass round completes after a player reaches the group's configured card count; the highest card count wins, with joint winners on a tie, bounded 5-20 for a 2-3 player group or 5-15 for a 4-8 player group (reuses `GroupService`'s existing validation, not re-implemented)
- [x] Player disconnect: mark `isConnected` false, leave timeline/tokens/turn order untouched
- [x] Player explicit leave: mark `Left`, exclude from future turns and DJ rotation, existing timeline cards still count toward the final results
- [x] Active-player turn timeout: if the active player is disconnected when their turn comes, or disconnects mid-turn, auto-skip after 90 seconds and mark them `Left`
- [x] Auto-abandon the session after 10 minutes with zero connected players, no results export in that case
- [x] Downloadable results export when a session completes normally, including the main card-count ranking and the two separate "Most Artists Guessed"/"Most Titles Guessed" leaderboards
- [x] Purge all session state (roster, rounds, guesses) once the session ends or is abandoned, hand control back to the group (`GroupService.recordGameSessionEnded` now also reopens the group, see `DECISIONS.md`)
- [x] Frontend: drag-and-drop timeline placement, cards animate apart to open a gap with no overlap, animate back into place once placed. Deferred to story 28, backend only this phase (done: drag placement with gap previews on the session page (`placementDrag`))

Tests:
- [x] Unit tests for the guess-matching function: normalization (punctuation, diacritics, whitespace) and the flat edit-distance-1 budget, covering both a correct-typo case and a same-distance wrong-word case (`DECISIONS.md`'s worked examples), plus the multi-artist any-one-correct rule (`GuessMatcherTest`)
- [x] Unit tests for scoring: all four outcome rules, including the tied-release-year case (`GameSessionServiceTest`)
- [x] Unit tests for the two leaderboard tallies: a non-active player's guess updates them without touching tokens or placement; a multi-artist song credits a correct featured-artist name the same as a correct main-artist name (`GameSessionServiceTest`)
- [x] Unit tests for win-condition bounds: 5-20 (2-3 players) and 5-15 (4-8 players), including the boundary values (`GroupServiceTest`, since `GameSessionService` reuses that validation rather than re-implementing it)
- [x] Unit tests for round rotation, both fixed and rotating DJ settings, and rotation skipping `Left` players (`GameSessionServiceTest`)
- [x] Unit tests for the active-player turn timeout, including the boundary at 90 seconds (`GameSessionServiceTest`)
- [x] Unit test: reveal fires automatically once the betting window closes, with no DJ or player trigger required (`GameSessionServiceTest`)
- [x] Integration test: full session lifecycle, admin starts, roster snapshot, several rounds, win condition hit, results export generated, state purged (`GameSessionLifecycleIntegrationTest`)
- [x] Integration test: auto-abandon path, session torn down after 10 minutes with zero connected players, confirms no export is generated (`GameSessionLifecycleIntegrationTest`)
- [x] Integration test: betting concurrency, multiple simultaneous bet attempts on the same guess, exactly one accepted, no token lost by the others (`GameSessionBettingConcurrencyIntegrationTest`, real threads against a real Postgres instance)
- [x] Integration test: betting window skipped entirely when no player holds a token (`GameSessionLifecycleIntegrationTest`)
- [x] Integration test: player disconnects mid-turn, doesn't reconnect within 90 seconds, ends up `Left`, and a later reconnect attempt after that point doesn't restore active status (`GameSessionLifecycleIntegrationTest`)

The remaining frontend requirements and tests are tracked under story 28 in TASKS.md.

## Story 11: Real-time game sync over WebSocket

Checked against real code: no WebSocket layer exists, this is greenfield work. Based on the sync model in `ARCHITECTURE.md` (REST for group/session creation and join, WebSocket for state changes). Covers both the group and the game session, not just the session.

This batch built the general STOMP infrastructure and the full group-side half. Story 10's batch (`feature/game-session`) then built the session-side half on top of it in full: destinations, broadcast, disconnect handling, and presence registration. The two frontend tasks stay deferred project-wide for this phase of batches, backend only. See `DECISIONS.md`'s corresponding entries for the destination-naming convention, the messaging pattern chosen, and the full list of deferrals.

- [x] Add the Spring WebSocket/STOMP dependency and base config to the core service (`WebSocketConfig`, `spring-boot-starter-websocket`)
- [x] Authenticate the WebSocket handshake against the existing JWT auth (`StompAuthenticationChannelInterceptor`, validated on the STOMP CONNECT frame)
- [x] Define per-group STOMP destinations for broadcast (membership, settings changes, chat, voice signaling) and a client-to-server channel for admin actions (`GroupDestinations`); chat and voice are naming-convention placeholders only, no send/receive logic, stories 13 and 12 still own that
- [x] Define per-session STOMP destinations for broadcast (round events) and a client-to-server channel for actions (guess, bet, reveal). Built in story 10's batch (`SessionDestinations`), following the parallel convention `GroupDestinations`' javadoc had already reserved
- [x] Broadcast group events: member joined/left, settings changed, game session started (`GroupService` publishes a `GroupBroadcastEvent` via `ApplicationEventPublisher`, `GroupBroadcastListener` forwards it to the right STOMP topic); member connection changes (disconnect/reconnect) and admin transfer broadcast the same way, a natural extension of "membership changes" beyond the story's literal four events, see `DECISIONS.md`
- [x] Broadcast round events: round started, guess locked, bet placed, reveal triggered, round scored, next round. Built in story 10's batch (`GameSessionService` publishes a `SessionBroadcastEvent`, `SessionBroadcastListener` forwards it to the session's round or ended topic)
- [x] Handle disconnect, group-member half: on WebSocket disconnect, mark the member's `isConnected` flag false without ending the group (`GroupSessionEventListener` reacting to `SessionDisconnectEvent`, calling `GroupService.disconnectMember`)
- [x] Handle disconnect, in-session-player half: built in story 10's batch (`SessionSessionEventListener` reacting to `SessionDisconnectEvent`, calling `GameSessionService.disconnectPlayer`, which also starts the 90-second turn-timeout clock when the disconnecting player is mid-turn)
- [x] Wire group creation/join to register the joining client on the group's topic: the client subscribing to its group's membership topic is what registers presence in `GroupPresenceRegistry` (`GroupSessionEventListener` reacting to the subscription)
- [x] Wire game session start to register the joining client on the session's topic. Built in story 10's batch: a client subscribes to the session's round topic once `GAME_SESSION_STARTED` fires, which `SessionSessionEventListener` reacts to the same way `GroupSessionEventListener` reacts to the membership-topic subscription
- [x] Log the group-side state events this story broadcasts (membership, settings changes) on a per-group timeline: a structured `groupEvent type=... groupId=...` line (`GroupBroadcastListener`), a shape a later addition can log alongside using the same two fields
- [x] Log round events alongside the WebRTC connection lifecycle events story 12 adds. Built in story 10's batch on the same two-field shape (`sessionEvent type=... sessionId=...`, `SessionBroadcastListener`); story 12's WebRTC events, not yet built, can log alongside it later
- [x] Frontend: keep the group/session WebSocket connection alive while navigating to other parts of the app, minimize the game to a small persistent widget instead of requiring the player stay on the game screen. Deferred: this batch is backend only, per standing instruction; revisit alongside story 28's redesign or whenever story 10's frontend lands (done: `components/active-session-widget.tsx` keeps a game-in-progress card on every other route)

Tests:

- [x] Integration test: a client can disconnect and reconnect to a group's topic and keep receiving broadcasts correctly, membership state isn't lost or duplicated across the reconnect (`GroupWebSocketIntegrationTest`, asserted against the real STOMP simple broker's own subscription registry)

- [x] Unit tests for the disconnect handler, group-member half: the flag flips without ending the group (`GroupServiceTest`, `GroupSessionEventListenerTest`)
- [x] Unit tests for the disconnect handler, in-session-player half: built in story 10's batch (`GameSessionServiceTest`'s and `GameSessionLifecycleIntegrationTest`'s disconnect/turn-timeout tests)
- [x] Unit tests for JWT handshake authentication: a valid token connects (sets the STOMP session's user), an invalid or missing one is rejected (`StompAuthenticationChannelInterceptorTest`)
- [x] Tests that the right group events actually get broadcast when `GroupService`'s methods run: member joins/leaves, settings change, session starts, disconnect/reconnect, admin transfer (`GroupServiceTest`'s event-publishing tests, `GroupBroadcastListenerTest`'s destination-routing tests)

The remaining frontend requirements and tests are tracked under story 28 in TASKS.md.

## Story 12: Voice chat

Blocked on story 11 (WebSocket layer) and story 39 (group): voice is scoped to the group's lifetime, not the game session's, and its signaling rides the WebSocket layer. Both have now shipped (backend). Confirmed against real code: `Member.isInVoice` and the member-gated `/api/groups/{groupId}/voice/join` and `/api/groups/{groupId}/voice/leave` endpoints already exist from story 39, flipping the presence flag but not broadcasting it. No WebRTC signaling relay, no TURN-credentials endpoint. The WebRTC mesh itself, `RTCPeerConnection`, `getUserMedia`, ICE handling, audio elements, and every piece of join/leave/mute UI live in the browser and belong to story 28's gameplay pass, per this file's frontend-consolidation preamble. Voice is inherently browser-side, so the backend slice is only the parts that must run server-side: relaying signaling between members, broadcasting presence, and minting TURN credentials.

Backend slice:
- [x] STOMP signaling relay: a member-gated `@MessageMapping` on `/app/groups/{groupId}/voice/signal` that forwards an SDP offer, SDP answer, or ICE candidate from one member to the group's voice topic, tagged with the sender and the intended recipient so peers route it client-side. Reuses the story 11 STOMP config, the CONNECT-frame JWT principal, and the group membership check, matching the `GameActionController` pattern
- [x] Broadcast voice presence on join and leave so other members see the roster change live, reusing story 11's group broadcast-event and listener indirection rather than a bespoke channel
- [x] TURN credentials endpoint: member-gated `GET /api/groups/{groupId}/voice/turn-credentials` returning the ICE server list a client feeds `RTCPeerConnection`. Reads the Cloudflare TURN key from a config property; when the key is absent, returns STUN-only ICE servers and omits TURN, deferred until the real Cloudflare key is provisioned in this environment
- Deferred to story 28: WebRTC mesh peer setup, `RTCPeerConnection` lifecycle, `getUserMedia`/microphone, ICE negotiation, audio elements, the join/leave/mute/deafen UI, the speaking-indicator sidebar, and its animations
- The 8-participant cap is enforced structurally: a group is capped at `Group.MAX_MEMBERS` (8), and only members can join voice, so a voice room can never exceed the group size. No separate voice-participant counter exists server-side; the mesh's own per-peer connection cap is a story 28 client concern

Tests:
- [x] Signaling relay forwards a member's offer, answer, and candidate to the voice topic, and rejects a non-member
- [x] TURN credentials endpoint is member-gated and returns STUN-only ICE servers when the Cloudflare key is absent

The remaining frontend requirements and tests are tracked under story 28 in TASKS.md.

## Story 37: Privacy policy, terms of service, and GDPR compliance

Checked against real code: `DELETE /me` (`UserController` → `UserService.deleteUser()`) already does a real hard delete of the `User` row, not a deactivation. It's not just unaudited for `Song.addedBy` references and shared playlists, both are confirmed live bugs, see this file's Bug fixes section. No analytics exist yet (story 34), so there's nothing to disclose there until it ships.

Scope decided: exactly two dedicated legal/static pages, Privacy Policy and Terms of Service. No separate cookie-consent page is needed yet, it's already gated below on story 34 shipping, and no other legal or static page (About, Contact) is planned.

- [x] Draft a privacy policy covering what's actually collected today: auth data (username, email, OAuth provider ID), playlist/song data. Lives at `docs/legal/privacy-policy.md`, see `DECISIONS.md`
- [x] Draft terms of service. Lives at `docs/legal/terms-of-service.md`, see `DECISIONS.md`
- [x] Add a GDPR data-export endpoint: a logged-in user can download their own account, playlist, and song data; `ExportController`/`ExportService` exist today but export playlist/song content, not a full personal-data dump, don't assume they already cover this. Built as `GET /api/users/me/export` and `PersonalDataExportService`, kept separate from `ExportController`/`ExportService`
- [x] Fix the `DELETE /me` bug in this file's Bug fixes section (FK violation on `Song.addedBy`, orphaned playlists on last-member deletion), then confirm no other edge case leaves orphaned references or unexpectedly deletes other members' shared playlists. Fixed; also found and fixed a related real bug surfaced while testing the shared-playlist ownership-transfer path against real Postgres, see `DECISIONS.md`

Tests:
- [x] Integration test: GDPR export endpoint returns the user's complete account, playlist, and song data
- [x] Integration test: `DELETE /me` with existing `Song.addedBy` references and shared-playlist memberships behaves per the decided handling, no orphaned references, no other member's playlist unexpectedly deleted

The deferred analytics consent notice is tracked under story 34 in TASKS.md.

## Documentation cleanup: approved archive candidates

- [x] Move completed records and sub-batches to ARCHIVE.md while retaining unfinished requirements and tests under their owning stories
- [x] Archive dropped story 26 and the misplaced web mockup correction
- [x] Trim completed roadmap history and resolved project-state questions while preserving references
- [x] Reconcile the implementation-backed release-playtest items without closing live acceptance gates
- [x] Validate checkbox preservation, archive eligibility, links, and the scoped documentation diff

## Login error reasons

The login page showed "Invalid email or password." for every failed login, including an unverified account and a locked-out account, so a user could not tell why a correct password failed.

- [x] Show the server's message on a 403 (email not verified) and a 429 (account locked or rate limited), and keep the generic message for wrong credentials and any other failure (`lib/login-error.ts`, `login/page.tsx`)
- [x] Offer to resend the verification email from the login page when the account is unverified, with a confirmation that does not reveal whether the account exists and the server's message when resending is rate limited

Tests:

- [x] Unit tests for the error message selection: wrong credentials, unverified, locked, a refusal with no readable reason, an unexpected server error, and no response
- [x] Login page tests: an unverified account shows the server's reason and wrong credentials keep the generic message
- [x] Login page tests: the resend option appears only for an unverified account, calls the resend request with that email, clears on the next attempt, and reports success and failure

## Playlist metadata and lobby controls

The approved review keeps later library, explore, playlist-detail, and lobby improvements. Updated mockups become the visual reference for those requirements. Account and legal-page work remains deferred to a later batch.

- [x] Update the design authority rule and screen requirements for approved filters, actions, member popup, import menu, and lobby picker states
- [x] Persist playlist creation timestamps and official YouTube song durations, expose them through API contracts, and render unavailable states for legacy records
- [x] Add group-admin transfer with confirmation, permission refresh, and failure feedback
- [x] Update dark/light design sources and the published canvas for library, explore, playlist detail, and lobby states
- [x] Add backend and AI tests for timestamp/duration persistence and API fields, including unavailable metadata
- [x] Add frontend tests for data display, admin-transfer permissions/failures, and affected filter/menu/picker interactions
- [x] Run relevant suites and rendered desktop comparisons in both themes against the updated mockups; only the landing page has mobile design scope
- [x] Record reviewed audit findings and deferred account/legal scope
- [x] Open the pull request and archive the completed tasks

Validation: 124 backend tests, 38 AI tests, 54 frontend tests, TypeScript checking, and desktop browser coverage in dark and light themes. The desktop mockups cover the library filters, import menu, playlist metadata, and lobby picker/admin states. Broader story 28 visual verification remains open.

## Automatic YouTube duration refresh

- [x] Replace hourly duration deletion with a six-hour refresh of due video IDs, index the due lookup, and batch official videos.list requests without the AI pipeline
- [x] Keep successful refresh timestamps, clear unavailable durations, and retry failed lookups without extending stale data retention
- [x] Add AI endpoint/source tests and backend scheduling, batching, persistence, concurrency, and failure tests
- [x] Document refresh behavior and shared YouTube quota costs, archive completed tasks, and update the existing pull request

## Gameplay, voice, and away-state feedback

The gameplay keeps automatic reveal, complete-pass rounds, joint winners, and guessing that closes when placement locks. Account and legal work remains deferred.

- [x] Align game rules, results copy, and design documentation with the implemented round and winner behavior
- [x] Add a clickable turn notification and sound outside the session page, without repeated alerts after reconnect
- [x] Show the active player, round, own tokens, and server deadline countdown in the away widget
- [x] Show earned-token feedback only for actual awards, distinct incorrect feedback, and reduced-motion behavior
- [x] Include a per-submission token award flag in the private guess result and test awards, partial guesses, and spectator guesses
- [x] Keep the voice rail at 76px with its join control at the top and visibility limited to the lobby or an active call
- [x] Show member avatars, speaking rings, synchronized mute and deafen status, and member exit/reflow feedback
- [x] Update affected mockups and their published canvas to match the approved behavior
- [x] Add unit tests for notifications, deadlines, token awards, and voice status synchronization
- [x] Verify desktop behavior in both themes, reconnect handling, and voice status across two clients
- [x] Run the relevant frontend checks and archive completed tasks before opening the pull request

Validation: 74 backend gameplay and broadcast tests pass, along with 314 frontend tests, TypeScript, and changed-file lint. Four desktop browser cases cover both themes, reconnect, token feedback, reduced motion, and two real WebRTC peers with fixture HTTP/STOMP responses. All 125 design sources match the published canvas; ten updated states render at desktop size.

## Fix: Verified song catalog reuse and metadata contracts

- [x] Save genuinely new fully verified pipeline results to the shared catalog immediately, including single-song previews, without adding playlist membership
- [x] Carry the matched song ID through AI responses and reuse the existing song for alternate uploads across previews, confirmations, imports, and patient processing
- [x] Serialize repeated and concurrent writes by YouTube ID and preserve the reused song's primary upload, duration, and metadata
- [x] Retain ten-minute preview reuse for results awaiting explicit confirmation; unverified previews do not create catalog songs automatically
- [x] Correct combined-precheck, multi-artist, admin-seeding, fast/patient import, and preview-cache documentation
- [x] Remove dropped Topic-upload and genre requirements from active docs and record the decisions
- [x] Unit tests: verified preview persistence, unverified preview exclusion, duplicate response identity, metadata preservation, and confirmation reuse
- [x] Integration tests: verified lookup without playlist membership, alternate-upload reuse, and simultaneous submissions without duplicate rows
- [x] Run the affected backend and AI suites, validate documentation links, and archive the completed task section before opening the PR

## Story 40: Catalog seeding queue and user-facing bulk import

Completed seeding, import, progress, and priority-coordination work is recorded in [ARCHIVE.md](ARCHIVE.md#story-40-completed-backend-implementation). Duplicate responses now carry the matched song ID and core links alternate uploads to the existing Song. PostgreSQL tests cover repeated and simultaneous reuse. Automatic embedding indexing remains a separate task.

- [x] When the pgvector check returns a high-confidence match for a new YouTube ID, link that ID to the matched Song and stop without another year lookup. AI responses carry the matched song ID; previews, imports, patient processing, and playlist confirmation reuse it

Tests:

- [x] Integration test: a matched alternate upload reuses the existing Song, preserves primary-upload metadata, and creates one alternate link under simultaneous submissions. AI tests verify the duplicate identity and skipped source/LLM processing

## Chore: Current metadata model references

- [x] Remove obsolete model references from metadata documentation and benchmark descriptions
- [x] Use provider-independent model labels in mocked metadata responses
- [x] Check runtime model defaults and local overrides for obsolete model selections
- [x] Run affected metadata unit tests and verify that no obsolete model references remain

## Chore: History and difficulty scope agreement

- [x] Record the approved database boundary, fields, retention, and start flow
- [x] Define implementation and test tasks against the current result, selection, and deletion paths
- [x] Extend the analytics retention default to 365 days and run the retention integration test

## Game history and prepared global difficulty

The agreed core/analytics boundary and fields are in [SYSTEM_REFERENCE.md](SYSTEM_REFERENCE.md#game-history-and-difficulty-data). Core history retains every compact game summary, analytics retains research observations, and prepared core scores serve generation at Start. Personalized ML remains deferred.

- [x] Add core-owned migrations and models for GameSummary, GameParticipantSummary, and SongDifficulty, plus analytics SongPlayObservation and SongDifficultyAggregate
- [x] Save a participant-only summary before session purge, preserve ties and departure status, distinguish interruptions from competitive completion, and deduplicate repeated completion
- [x] Add paginated history/detail and user-visible statistics endpoints reading core only; keep access after group expiry and deny later group joiners
- [x] Extend account deletion and personal export for history and research data; anonymize shared results, remove research identity, and delete summaries with no remaining account-linked participants
- [x] Deliver scored-turn observations reliably to analytics without making game completion depend on analytics availability; deduplicate retries and exclude skipped placements from attempt counts
- [x] Retain raw observations for 365 days and preserve anonymous per-song aggregates across raw-event expiry without counting retries twice
- [x] Calculate global difficulty in the background and publish prepared scores into core; provide popularity-based cold starts and preserve the last good scores on analytics failure
- [x] Persist the selected tier on Confirm and generate only at Start, using the current connected player count times the win target times three; select varied eligible songs without loading and scoring the entire catalog
- [x] Unit tests: summary totals, ties, interruptions, difficulty aggregation, cold starts, and sampling boundaries
- [x] Integration tests: core and analytics migrations, summary-before-purge, participant permissions, deletion/export, retry deduplication, one-year retention, and analytics outages
- [x] Performance tests: indexed selection on a representative large catalog, insufficient pools, and overlapping starts; record measured latency before setting a supported target

The indexed selection check used 100,000 generated catalog songs, twenty selections, and a 96-song pool. The latest local mean was 13 ms; other local runs measured 12 to 33 ms. This measures indexed selection with a repository fixture, not full HTTP request latency or a production service target. Real PostgreSQL integration tests also cover Confirm without session creation, the full generated pool at Start, and overlapping starts producing one session.


## Game history and difficulty frontend

- [x] Define history list/detail and statistics mockups against the approved core summary fields before building the pages
- [x] Build participant-only paginated history, detail, and statistics views using the core endpoints
- [x] Save Easy/Medium/Hard selection on Confirm; generate and start only when Start session is clicked, without revealing the song pool
- [x] Frontend unit tests: selection persistence, no generation on Confirm, generation on Start, history permissions, deleted-player entries, loading/error/empty states
- [x] Desktop browser tests: difficulty and Custom starts, insufficient catalog, history after group expiry, and both themes

Desktop browser coverage uses API fixtures in both themes. Core PostgreSQL integration tests enforce participant access and group-independent retention. The history pages, account statistics, Custom selection, insufficient-catalog feedback, and tier selection at Start are implemented. Session completion refreshes history and statistics queries.

## Docs: Implemented API and product contracts

- [x] Correct song-edit status and shared-playlist permissions, group removal and invite rules, and group-scoped chat/voice wording
- [x] Add implemented API routes, export modes and paper sizes, and correct local email setup
- [x] Validate changed claims against code, check documentation links, and archive completed tasks

## Chore: Flutter DJ-model compliance completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Check the current Flutter code for any embedded or hidden YouTube playback (an in-app WebView or player widget); not yet confirmed against the real Flutter codebase (done: `mobile/lib/main.dart` embeds `youtube_player_iframe`, so the item below applies)

## Story 38: Observability completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Add Spring Boot Actuator to the core service for health/metrics endpoints, expose `/actuator/prometheus`
- [x] Add an equivalent health endpoint to the AI microservice (FastAPI has none today), expose metrics via `prometheus-fastapi-instrumentator`
- [x] Set up a Grafana Cloud free-tier account, point both services' Prometheus metrics at it .  a Grafana Alloy container (`observability/alloy/config.alloy`, wired into `backend/docker-compose.yml`) scrapes both services' local endpoints (`/actuator/prometheus`, `/metrics`) via `host.docker.internal` and remote-writes to Grafana Cloud's hosted Prometheus, since neither service runs as its own container for Alloy to scrape directly
- [x] Add OpenTelemetry auto-instrumentation to both services for distributed tracing, viewable in Grafana Cloud's Tempo .  a real trace, sent with `OTEL_SDK_DISABLED=false` and a real Grafana Cloud OTLP endpoint, was confirmed to land in Tempo. The AI microservice's exporter had a real bug fixed here: passing `endpoint` directly to `OTLPSpanExporter` skips the SDK's own per-signal path resolution, so every export 404'd silently until the `/v1/traces` suffix was added explicitly; confirmed via a direct HTTP check against Grafana Cloud's gateway before and after the fix
- [x] Ship both services' structured logs to Grafana Cloud's Loki .  logs ship through the same unified OTLP gateway as traces instead of a separate Loki-specific push path: the backend gets a second Logback appender (`OpenTelemetryAppender`), the AI microservice gets an OTLP `LoggingHandler` attached to the `app` logger specifically, not the root logger, since attaching to root captures the exporter's own HTTP transport logs and re-exports them, an unbounded feedback loop confirmed by reproducing it before fixing it. A real log record was confirmed to land in Grafana Cloud (`204` from the OTLP gateway) after the fix
- [x] Add error tracking (Sentry, free tier) to both services .  SDK wiring is in place in both services (`sentry-spring-boot-4`, `sentry-sdk`); real Sentry projects now exist for both services and real DSNs are configured
- [x] Add a request-id/correlation-id filter so one user action can be traced across both services' logs, and correlates with the OpenTelemetry trace for the same request
- [x] Build a basic Grafana dashboard: request rate, error rate, latency percentiles for both services .  written as dashboard-as-code at `observability/grafana/hittiguess-overview-dashboard.json`, not provisioned into a live Grafana instance since no account exists yet
- [x] Surface the AI microservice's per-source fetch failures and OpenAI call failures as visible alerts, rather than only the generic swallowed `status="ERROR"` response .  each metadata source and the OpenAI synthesis call now logs a structured error and calls the Sentry SDK's capture path distinctly, instead of disappearing into the pipeline's generic error response
- [x] Add a periodic check against Grafana Cloud's and Sentry's free-tier usage limits, so approaching them is noticed before either starts silently dropping data or asking for payment (a daily scheduled check reads Sentry accepted errors and Grafana active series against configurable quotas and warns at 80%; each half stays silent until its credentials exist; log and trace gigabyte billing has no stable code endpoint and stays a console billing alert)
- [x] Integration test: Actuator health endpoint reports correctly both when healthy and when a dependency (the database) is down
- [x] Integration test: a request-id set on an incoming request propagates through a core-service-to-AI-service call, appears in both services' logs, and correlates with a single OpenTelemetry trace .  the backend side (filter, MDC, span attribute, RestClient interceptor) is proven against the real production code path; the AI microservice side (reusing an incoming id, echoing it, logging it) is proven independently in its own suite. This sandbox's JDK cannot open real loopback sockets between processes (the same limitation `BackendApplicationTests` is excluded for), so a literal cross-process HTTP call between the two real running services could not be executed here; `MockRestServiceServer` stands in for the AI service's HTTP boundary while exercising every other real component.

## Story 28: UI redesign completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Design phase: establish the fresh visual direction (color, type, spacing, component style) and apply it across every existing page: landing, login, register, forgot-password, dashboard/playlist list, playlist detail, song detail, add song, join-by-invite. See `docs/design/hittiguess-design.html`
- [x] Design phase: extend the same visual system to the gameplay screens `GAME_DESIGN.md` specs but that don't exist as code yet: group lobby (member list, admin crown, join code/link, settings), game session/timeline (drag-and-drop cards, guess box, token count, betting window), DJ view (open-in-YouTube link-out), voice sidebar, text chat overlay, turn notification banner, the minimized "playing while away" widget state, and the results/leaderboard screen. See `docs/design/hittiguess-design.html`
- [x] Review pass against every mockup with the project owner before implementation starts, checking each gameplay screen against `GAME_DESIGN.md`'s spec for anything the design missed
- [x] Implementation: rebuild the existing pages' actual layouts to match their mockups across Batches A through D, not just their color/font tokens. The remaining route smoke and rendered comparison checks are listed below. See `docs/FRONTEND_IMPLEMENTATION_GUIDE.md` for the required per-page workflow and verification step
- [x] Implementation: build the new gameplay screens as real Next.js components/routes and wire them to stories 9/10/11/12/13/39's actual backends. Representative-state and rendered verification remain open
- [x] Component/token boundary: no longer retheme-only where a mockup's layout differs from the existing page's layout; `docs/FRONTEND_IMPLEMENTATION_GUIDE.md` supersedes the retheme-only rule for those pages. shadcn primitives (`components/shadcn/*`) are still used wherever they're the natural fit for a control (button, input, dialog, table), never replaced with hand-built markup for a form control or anything interactive; but a page's overall layout is rebuilt to match its mockup rather than kept as-is (decided in `DECISIONS.md`: Batch F keeps the primitives, layout is rebuilt per mockup)
- [x] Build the group lobby route and shell from the `GroupLobby*` mockups, including member presence, admin indicators, join link, group settings, and the empty, two-player, and eight-player layouts
- [x] Wire the group lobby to the generated group-management hooks and persistent group WebSocket events, with loading, forbidden, missing, and connection-error states
- [x] Bridge the browser's HTTP-only access-token cookie into the STOMP authentication flow, so the gameplay client can connect without exposing the token to JavaScript
- [x] Build the game-session route and shared round shell from the `GameSession*` mockups, including player, DJ, and spectator layouts, current-song card, timeline, token count, and persistent session connection
- [x] Implement timeline card placement and guess submission against the game-session API, including dragging, dropped, locked, and animated reveal states
- [x] Implement betting-window preparation and active states, including token-holder variants and round progression from session broadcasts
- [x] Build the DJ link-out action and audio-sharing warning, always opening the real YouTube page or app rather than embedding playback
- [x] Add the group text-chat overlay, turn notification, away widget, and voice sidebar to the gameplay shell, wired to the group-chat, WebSocket signaling, and TURN-credentials APIs
- [x] Build the results and leaderboard route from the `Results*` mockups for two-player and eight-player sessions, including the session export action
- [x] Add unit coverage for each new interactive component and Playwright coverage for lobby join, session start, placement, betting, link-out warning, chat, and results export
- [x] Frontend test: each redesigned existing page renders without regression (a smoke test per route) (every one of the 24 routes has colocated tests, verified by audit)
- [x] Frontend test: the new gameplay screens render correctly against representative mock state (empty, mid-game, varying player counts) (session tests cover active-player, DJ, spectator, and pre-round states plus guess submission)
- [x] Frontend test: the drag-and-drop timeline placement and the guess box's animated feedback behave per `GAME_DESIGN.md`'s Interaction and animation section (keyboard placement, placement feedback, feedback clearing, and guess submission are covered)
- [x] Accessibility check: color contrast and keyboard navigation for the new visual direction, specifically the semi-transparent chat overlay and the voice sidebar (chat focus and Escape handling plus sidebar labeled controls and toggle states are covered, contrast audited under Batch F)
- [x] Rebuild the shared app shell states from `AppShellDark`: active session, profile panel with statistics, and voice participant rail
- [x] Match the landing desktop and mobile structure, CTA copy, card fan, feature sections, final CTA, and footer
- [x] Match login and register control placement, card sizing, spacing, and Google button treatment
- [x] Add a deterministic capture state for the transient OAuth2 redirect screen
- [x] Match Your Playlists grid placement and remove the extra top-level join action
- [x] Match Playlist Detail's permanent member panel, header proportions, toolbar, banner, and song rows
- [x] Match Edit Playlist's description, cover edit affordance, explicit save and cancel actions, invite copy action, delete panel, and member metadata
- [x] Match Join by Invite's playlist summary, cover, member avatars, and join identity controls
- [x] Match verified, needs-review, and editable song detail card dimensions, actions, cover treatment, and footer structure
- [x] Match Add Song result density and selected-song panel, with deterministic editable and locked review states
- [x] Match Explore Public Playlists card mosaics and populated grid
- [x] Match the YouTube import link step and implement the processing list, progress bar, temporary sidebar progress icon, and progress toast states
- [x] Preserve the matching choose-source and existing-playlist import layouts while adding designed loading, empty, and error states (both steps render loading, retryable error, and empty states, covered by import page tests)
- [x] Expose and render the catalog backlog's per-item queue required by `AdminCatalogBacklogDark`
- [x] Match the report queue's artist metadata, convergence count, tier badges, and designed loading, empty, and error states
- [x] Frontend tests cover every new or changed interactive state in this remediation (colocated tests per route carry the states; the full suite passes)
- [x] Route smoke tests cover every Batch A through D route (every one of the 24 routes has colocated tests, verified by audit)

## Story 47: Product ground-truth pass completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Add an All filter beside Owned, Joined, and Saved
- [x] Give each tab its own end tile: Owned keeps New playlist, Joined gets Join playlist, Saved gets Explore public playlists
- [x] Add a Join playlist button beside Create playlist
- [x] Add All, Saved, and Not saved filters
- [x] Make each playlist card open its playlist detail; the Save/Saved action stays on the card
- [x] Enforce the six predetermined title colors everywhere a color is set, no other values
- [x] Build the four-tile mosaic cover: squared YouTube thumbnails with no black bars, placeholders filling empty tiles, thumbnails filling in progressively as songs are added
- [x] Show member circles only, opening a centered full member list popup on click
- [x] Show the ghost empty state with no songs, no in-list search box, and no redundant call to action
- [x] Scope the song search to songs inside the playlist
- [x] Start session opens gameplay with that playlist already selected
- [x] Confirm before leaving a playlist
- [x] Add the export options UI (content choice, paper size, download or print; duplex not built)
- [x] Offer invite by code and invite by link, each copying a ready message; invite URLs respect localhost versus production
- [x] Implement cover change, title, title color, description, public toggle, and invite-link copy
- [x] Confirm delete works behind its warning, and the members tab grants, kick, and ban all work
- [x] Decide the pixel-art cover rule (upload pixelized for direct database storage, same rule for profile pictures); pixelize and store won
- [x] Audit every clickable control for the pointer hand cursor
- [x] Link songs from an existing playlist instantly
- [x] Run YouTube imports in the background with a sidebar progress indicator, hover progress, greyed pending songs in the detail view, and a return path to the live progress screen
- [x] Show catalog recommendations by default with fetch-more, and keep the add-tray contents across navigation until committed
- [x] Show continuous staged progress on single-song fetch: submitted title and channel plus the sources being consulted
- [x] Animate lobby members floating per the design; keep Start game, Chat, and Settings
- [x] Keep the voice sidebar at 76px with no collapse control, visible on the lobby or during an active call
- [x] Order voice participants top to bottom, with the join control at the top when outside the call
- [x] Add the voice settings popup: speaker and microphone selection plus a test control (shipped without a prior design)
- [x] Narrow lobby settings to DJ mode (with a player picker for a fixed DJ) and cards to win; move playlist choice to a multi-select popup that merges duplicates into a temporary playlist
- [x] Close lobby popups on outside click
- [x] Block starting alone with an explanatory message (two players minimum)
- [x] Confirm before leaving the lobby; drop the stray Live label
- [x] Fix away-status reliability
- [x] Show the first-time countdown only, enforce DJ, turn, guessing, token, betting, skip, and leaderboard rules per `GAME_DESIGN.md`
- [x] Animate the unrevealed card as an audio-reactive visualizer
- [x] Offer results download options (PDF print, copy as text, CSV download; shipped without a prior design)
- [x] Verify admin pages update live with processing state
- [x] Confirm each cluster above against the real code before building it (the gate to Ready)
- [x] Playwright multi-user coverage for join, lobby, full rounds, results, and the import background flow

## Story 7: Beta hosting on Azure Container Apps completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Create the Azure subscription, the `hittiguess-rg` resource group in Germany West Central with tags, and the Consumption-only `hittiguess-env` Container Apps environment with no VNet, private endpoint, dedicated workload profile, Azure Database, or Container Registry
- [x] Create the `hittiguess-monthly` subscription budget of $20 with email alerts at $5, $10, $12, and $20 actual spend and at $20 forecast spend
- [x] Build the Spring core production image: multi-stage Dockerfile with a cached dependency layer, a non-root user, and container-aware heap sizing; `application-prod.properties` enables graceful shutdown and health probes, and `/actuator/health/**` is public for platform probes
- [x] Create the FastAPI AI service container definition (`ai/Dockerfile`)
- [x] Serve the core API from `api.hittiguess.com` with a managed certificate and set the auth cookies' `Domain` to `hittiguess.com`, so the frontend's `proxy.ts` can read the `session_hint` cookie the backend sets
- [x] Configure the Spring core Container App with external HTTPS and WebSocket ingress, `minReplicas: 0`, `maxReplicas: 1`, and single active revision routing; WebSocket traffic is not yet exercised against it
- [x] Configure the FastAPI Container App with internal-only ingress, `minReplicas: 0`, `maxReplicas: 1`, and the core app's internal service-discovery URL
- [x] Build commit-SHA-tagged backend images in GitHub Actions, publish them to GitHub Container Registry, and deploy them through Azure OpenID Connect federation without a long-lived Azure credential
- [x] Configure Container Apps secrets for the database connections, authentication, OAuth, email, YouTube, OpenAI, DeepInfra, and Discogs values; none entered a repository file, image layer, or workflow log
- [x] Configure production Spring settings: `APP_ENV=prod`, frontend URL and allowed origins, secure cookies, the cookie domain, and the internal AI-service URL
- [x] Configure `NEXT_PUBLIC_API_URL` in Vercel with `https://api.hittiguess.com`; the Content-Security-Policy on the deployed frontend allows `https` and `wss` for that origin
- [x] Core image smoke test: `scripts/smoke-test-core-image.sh` builds the image, starts it with production settings against throwaway Postgres containers, and checks the Flyway migrations, the vector extension, the non-root user, and the three health probes

## Story 8: Production Neon database provisioning completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Create the `hittiguess-core` Neon project in an EU region with the `vector` extension enabled
- [x] Create the `hittiguess-analytics` Neon project in the same region
- [x] Set production database credentials and TLS connection URLs as Container Apps secrets for the Spring core and AI service, with the AI service limited to the transactional database

## Closed-beta frontend showcase completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Add the flag, the access-code check, and the signed `beta_access` cookie (`lib/beta-access.ts`, `app/api/beta-access/route.ts`)
- [x] Redirect every route except `/`, `/closed-beta`, and the access-code endpoint to `/closed-beta` for visitors without the cookie (`proxy.ts`)
- [x] Skip the landing page's current-user request for visitors without access
- [x] Add the closed-beta screen with the legacy app link, a back-to-home link, and the access-code field
- [x] Unit tests for the proxy redirect rules, cookie validation, and flag-off behavior (`proxy.closed-beta.test.ts`)

## AI service production image completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Add `ai/Dockerfile`: a build stage that installs the locked dependencies with uv, and a slim runtime stage that runs as a non-root user and serves on port 8000, plus `ai/.dockerignore`
- [x] Enable TLS in `app/dedup/database.py` when `DATABASE_URL` carries `sslmode=require`, `verify-ca`, or `verify-full`
- [x] Unit tests for the connection parameters: no TLS for a plain URL or `disable`, `allow`, and `prefer`; TLS for `require`, `verify-ca`, and `verify-full`
- [x] AI image smoke test: `scripts/smoke-test-ai-image.sh` builds the image, starts it against a throwaway pgvector Postgres, and checks `/health`, the internal-key protection on `/metrics`, the non-root user, and the database connection

## Auth cookie domain for production completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Add `app.cookie-domain` (from `COOKIE_DOMAIN`) and apply it to every cookie `CookieUtil` creates, including deletions so a browser removes the same cookie
- [x] Create the core API's custom domain `api.hittiguess.com` on the Azure Container App and set `COOKIE_DOMAIN=hittiguess.com` in its production configuration
- [x] Add the API subdomain to the frontend's `NEXT_PUBLIC_API_URL` in Vercel and to the Content-Security-Policy through that variable
- [x] Give the `XSRF-TOKEN` CSRF cookie the shared parent domain when `COOKIE_DOMAIN` is set; the frontend reads it from `document.cookie` to send the `X-XSRF-TOKEN` header, and a host-only cookie on the API subdomain is hidden from it, so every state-changing request such as logout failed with 403
- [x] Unit tests for `CookieUtil`: host-only without a domain, the configured domain on all three auth cookies, deletion using the same domain, and the other cookie attributes unchanged
- [x] Unit tests for the CSRF cookie: host-only without a domain, the shared parent domain and a script-readable cookie with one

## Backend deploy workflow completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Add the workflow with the smoke-test, publish, and deploy jobs; the deploy job uses OpenID Connect through `azure/login` and the `production` environment, with no stored Azure credential
- [x] Create the `hittiguess-github-deploy` Azure app registration with a federated credential for the `production` environment and the Contributor role on `hittiguess-rg`, and set the `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, and `AZURE_SUBSCRIPTION_ID` repository variables; the credential subject uses GitHub's immutable form with the owner and repository IDs (`repo:dariusturcu22@39344694/hittiguess@1156579634:environment:production`), which the repository's OIDC settings require
- [x] Make the two published GHCR packages public so Container Apps can pull them without a registry credential
- [x] Create the `hittiguess-core` and `hittiguess-ai` Container Apps, which the deploy job updates but does not create
- [x] The workflow passes `actionlint`
- [x] First end-to-end run: smoke tests pass, both images appear in GHCR, and the deploy job moves each Container App to the new SHA

## Container Apps creation completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Add the creation script with Neon connection-string parsing that rejects pooled hosts, and secret-safe character validation
- [x] Create the Google OAuth client for the hittiguess account, a new YouTube Data API key, and a Resend API key, stored as Container Apps secrets
- [x] Run the script, then confirm `/actuator/health/readiness` on the core app answers 200
- [x] Add `api.hittiguess.com` to the core app with a managed certificate and set `NEXT_PUBLIC_API_URL` in Vercel
- [x] Set the `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, and `AZURE_SUBSCRIPTION_ID` repository variables once both apps exist
- [x] The script parses, and its connection-string and secret helpers are checked against valid, pooled, malformed, and unsafe inputs

## Catalog seeding concurrency completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Serialize saves of one YouTube ID and enqueue calls with a Postgres advisory lock held to the end of the transaction (`SongRepository.acquireTransactionLock`, used by `SongResolutionService` and `CatalogSeedingService`), which also holds across replicas
- [x] Integration test: a song is visible in the database while the next item of the same backlog is still being resolved
- [x] Integration test: six simultaneous resolutions of one video leave one song row
- [x] Integration test: six overlapping enqueues of the same videos queue each video once

## Core startup time completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Limit the core image's JIT to the fast compiler through `JAVA_TOOL_OPTIONS` in `backend/Dockerfile`

## Secrets in AI service logs completed records, October reconciliation

These tasks record completed work. Later approved requirements supersede older layouts; remaining requirements and acceptance checks stay in TASKS.md.

- [x] Redact the values of credential-bearing query parameters (`key`, `api_key`, `apikey`, `token`, `access_token`, `client_secret`, `consumer_secret`, `password`) in every log message and exception text before it reaches stdout, and in records shipped over OTLP (`app/observability/logging_config.py`)
- [x] Unit tests for the redaction: one parameter, every known parameter name, lookalike parameters left alone, the JSON formatter's message and exception text, and the filter on a record with format arguments

## Production validation completed tests, October reconciliation

- [x] AI service image smoke test: the Dockerfile and scripts/smoke-test-ai-image.sh exist; the first successful deployment record includes both image smoke tests
- [x] Fresh-database integration tests: BackendApplicationTests and HistoryResearchIntegrationTest provision both complete migration histories independently, including pgvector

## Story 35: Public ground-truth data API

The verified-triples endpoint and its tests are implemented. The direct YouTube-terms read and recorded conclusion already exist in ARCHIVE.md's resolved project-state questions. This closes the duplicate read task; it does not constitute a new legal review.

- [x] Add a public read-only endpoint exposing verified `(artist, title, release_year)` triples only, no YouTube-sourced fields (built as `GET /api/ground-truth/songs`, joint MAIN-artist display string plus title plus locked year)
- [x] Filter to verified songs only, depends on story 23's `verificationStatus` field existing (the query filters on `VERIFIED`; covered by service and data integration tests)
- [x] Add pagination and rate limiting for public consumption (coordinate with story 27) (Spring page parameters with an explicit envelope, plus the general 60-per-minute anonymous bucket, covered by page-boundary and rate-limit tests)
- [x] Final confirmation read of YouTube terms, recorded in ARCHIVE.md's resolved project-state questions

Tests:
- [x] Integration test: the endpoint returns only verified songs, unverified songs never appear
- [x] Integration test: no YouTube-sourced field (`youtubeId` or anything derived from it) appears in the response shape
- [x] Unit tests for pagination and the rate limit, including boundary values

## Story 50: Auth hardening

The backend authentication slice is built: `User` carries email-verification and two-factor fields, `AuthController` exposes verification, password-reset, and two-factor endpoints, and `EmailService` sends through Resend. The forgot-password request and confirmation pages are wired to their generated hooks. The two-factor setup and second-login-step screens remain open. Gates Beta, not Local.

Email provider: Resend, chosen for its free tier (3,000 emails/month) and simple REST API, matching this project's existing pattern of picking the smallest free-tier service that does the job (Grafana Cloud, Sentry). A production Resend key is recorded as configured; sender-domain verification remains in the Container Apps tasks.

Two-factor authentication: TOTP (an authenticator app, e.g. Google Authenticator or Authy generating a 6-digit code from a shared secret), not SMS. No third-party SMS provider, no per-message cost, and it's the standard low-cost second factor for a project at this scale.

- [x] Add the Resend Java SDK (or a plain `RestClient` call to its REST API, whichever this project's existing HTTP-client conventions favor once checked against `AiServiceConfig`'s pattern) and an `EmailService` wrapping it; document `RESEND_API_KEY` in `backend/.env.example`
- [x] Add `emailVerified` (boolean, default false) to `User`; a Google OAuth2 signup sets it `true` immediately, since Google has already verified that email, only a local username/password signup starts unverified
- [x] Add an `EmailVerificationToken` entity (user, token, expiresAt), issued on registration and re-sendable; `POST /auth/register` sends a verification email with a link/token instead of (or alongside) completing signup, and a new `POST /auth/verify-email` endpoint marks the account verified when a valid, unexpired token is submitted
- [x] Decide and enforce what an unverified account can and can't do: block login entirely until verified (the simpler rule, avoids gating every downstream endpoint individually) versus allowing login but restricting real actions; document whichever is chosen in `DECISIONS.md`
- [x] Add a resend-verification-email endpoint, rate-limited the same way other auth endpoints are (see story 27, `RateLimitingFilter`'s existing `/auth/*` bucket)
- [x] Add a `PasswordResetToken` entity (user, token, expiresAt, used), `POST /auth/password-reset/request` (accepts an email, always returns success regardless of whether the email exists, to avoid leaking which emails are registered, and emails a reset link/token only if it does), and `POST /auth/password-reset/confirm` (token plus new password, single-use, expires after a short window)
- [x] Wire the frontend forgot-password request and confirmation pages to the generated request/confirm hooks (`frontend/app/(auth)/forgot-password`, `frontend/app/(auth)/reset-password`)
- [x] Add `totpSecret` (nullable, encrypted at rest or at minimum never returned by any DTO once set) and `twoFactorEnabled` (boolean, default false) to `User`
- [x] Add `POST /auth/2fa/setup` (admin/self, authenticated): generates a TOTP secret and a provisioning URI/QR code, not yet enabled until confirmed
- [x] Add `POST /auth/2fa/confirm`: the user submits one valid code generated from the new secret to prove they've actually added it to an authenticator app before `twoFactorEnabled` flips true
- [x] Add backup/recovery codes: a set of one-time-use codes generated alongside 2FA setup, shown once, each usable exactly once in place of a TOTP code if the authenticator app is unavailable
- [x] Add `POST /auth/2fa/disable` (requires the current password or a valid code, not just being logged in, to prevent a hijacked session from silently turning it off)
- [x] Change the login flow for a `twoFactorEnabled` account: `POST /auth/login` with a correct password but 2FA enabled returns a short-lived, narrowly-scoped intermediate token (not a real access/refresh pair) instead of completing login; a new `POST /auth/2fa/verify` endpoint accepts that intermediate token plus a TOTP or backup code and only then issues the real access/refresh cookies
The remaining two-factor frontend requirements and explicit tests are transferred to story 28. The completed backend slice does not complete those screens.

Tests:
- [x] Unit tests for `EmailService` (mocked HTTP call to Resend, not a real send in any test)
- [x] Unit and integration tests for the email-verification flow: an unverified account can't log in (or is restricted, per whichever rule was chosen), a valid token verifies the account, an expired or already-used token is rejected, a resend request is rate-limited
- [x] Unit and integration tests for password reset: a request for a nonexistent email still returns success and sends no real error signal, a valid token resets the password and is then rejected on reuse, an expired token is rejected
- [x] Unit tests for TOTP setup/confirm/disable: an unconfirmed secret doesn't enable 2FA, a wrong code during confirm doesn't enable it either, disable requires the extra proof and a bare authenticated request alone is rejected
- [x] Integration test for the two-step login flow: a 2FA-enabled account's login with just a password doesn't issue real tokens, a correct second-factor code completes it, a wrong or reused backup code is rejected
- [x] Unit test confirming no DTO or API response ever includes `totpSecret` or an unused backup code in plain form after initial generation
- [x] Wire the frontend's existing forgot-password form to the new request/confirm endpoints (already wired: the request page calls `useRequestPasswordReset`, the confirm page calls `useConfirmPasswordReset` with a mismatch guard, both covered by colocated tests)

## Docs: Remaining audit reconciliation

- [x] Recheck unresolved audit findings against merged code and reviewed source-of-truth decisions
- [x] Archive completed clusters while retaining missing features and uncompleted acceptance checks
- [x] Validate remaining claims, documentation links, and task references

## Fix: Verified catalog embedding indexing

Verified catalog inserts, upgrades, and artist/title edits schedule durable embedding indexing. Core owns the queue migration and scheduler; the authenticated AI worker claims and completes versioned leases.

- [x] Define and wire embedding population after verified catalog persistence, including upgrades of existing songs and retry behavior
  - Core migrations own a durable indexing queue for verified inserts, upgrades, and artist/title edits. An internal AI endpoint drains leased work; the core scheduler only calls it when work is due. Failed leases retry with backoff, and version checks discard results for changed metadata.
  - Migration and database integration tests cover backfill, invalidation, concurrent claims, lease recovery, and stale-result rejection. Scheduler and internal-auth tests cover production invocation.
- [x] Backfill verified songs with missing embeddings without changing their metadata or playlist membership
- [x] Unit tests: normalized artist/title indexing, verified-only writes, and retry behavior
- [x] Integration test: a newly verified song becomes searchable and a later alternate upload reuses it without another full pipeline run
