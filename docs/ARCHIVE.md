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
