# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 7, 9, and 12, are drafts written during planning, before this repository's actual code was available to check against. They have not yet been confirmed against the real implementation. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Story 6: Two-service split

Tasks confirmed against the current codebase on 2026-08-22 by reading the full metadata pipeline (`SongMetadataService`, the four source integrations, `MetadataParser`, `UrlBuilder`, `HttpUtils`, `MetadataPromptBuilder`, the controller and DTOs). The AI microservice never touches the database in this design; song creation is a separate call the frontend makes after previewing AI-suggested details, so "no migrations in the AI microservice" is already true by construction, not something to implement.

AI microservice:
- [ ] Scaffold the FastAPI project structure, with pytest configured
- [ ] Port the four metadata source integrations (YouTube Data API, MusicBrainz, Wikipedia, Genius) to Python; the current `YouTubeMetadataService`/`MusicBrainzService`/`WikipediaService`/`GeniusService` and their `UrlBuilder`/`MetadataParser` helpers are the reference, all plain HTTP calls with no Spring-specific coupling
- [ ] Port `MetadataPromptBuilder`'s prompt-construction logic
- [ ] Replace the current regex-stripped LLM response parsing (`raw.replaceAll("```json|```", "")` then a manual Jackson parse) with OpenAI's structured output / JSON schema mode and Pydantic model validation, per CLAUDE.md's rule against parsing LLM output with regex
- [ ] Add an internal endpoint, for example `POST /metadata/resolve`, that gathers all four sources and returns the synthesized result
- [ ] Add basic tests: prompt building, URL building, response parsing/validation
- [ ] Own `OPENAI_API_KEY` and `YOUTUBE_API_KEY` in its own config

Core service:
- [ ] Rewrite `SongMetadataService` to call the AI microservice's internal endpoint over HTTP instead of doing the work itself, keeping `SongMetadataController`'s public contract (`GET /api/metadata/song`, the `AiResponse` shape) unchanged so the frontend needs no changes
- [ ] Keep the one-in-flight-request-per-user rate limit in the core service; it already has the authenticated user via Spring Security, and the AI microservice has no reason to know about per-user concurrency. Reject before ever calling the AI microservice
- [ ] Decide and implement authentication between the two services (shared secret header or network-level restriction), so the AI microservice's endpoint isn't openly callable by anything else inside the Container Apps environment
- [ ] Remove the Spring AI dependency (`spring-ai-starter-model-openai`, the `spring-ai.version` property, the BOM import) once nothing in the core service uses it
- [ ] Delete the files whose logic moves entirely to the AI microservice: `YouTubeMetadataService`, `MusicBrainzService`, `WikipediaService`, `GeniusService`, `MetadataParser`, `MetadataPromptBuilder`, `UrlBuilder`, `HttpUtils`, `FlexibleYearDeserializer`. Confirmed none of them are used anywhere else in the core service
- [ ] Drop `OPENAI_API_KEY` and `YOUTUBE_API_KEY` from the core service's env once nothing there needs them

Frontend fix (found while confirming these tasks, not a pre-existing tracked bug):
- [ ] `AddSongForm.tsx`'s `handleGetDetails` only catches thrown errors; a 200 response with `status: "ERROR"` (which `SongMetadataService` returns on any pipeline failure) falls through the success path and silently populates an empty form with no indication anything failed. Treat it the same as a thrown error.

## Story 7: Azure migration

- [ ] Confirm the current Postgres host (see story 23, may need the schema reconciliation done first)
- [ ] Provision an Azure Container Apps environment
- [ ] Deploy the core service container
- [ ] Deploy the AI microservice container, same environment, for internal networking
- [ ] Provision Azure Database for PostgreSQL Flexible Server, enable pgvector
- [ ] Migrate data to the new Postgres instance
- [ ] Verify both services and the frontend work end to end against Azure
- [ ] Decommission Fly.io once verified

## Story 9: DJ real YouTube link-out

- [ ] Remove the current embedded/hidden iframe from the DJ view
- [ ] Add an "open in YouTube" link-out for remote sessions, opening a new browser tab
- [ ] Wire WebRTC tab audio capture to that new tab
- [ ] Add deep-link handling for in-person sessions (Android intent, iOS universal link, fallback to a plain browser link)
- [ ] Update QR code generation to point at the real YouTube URL
- [ ] Replace the automatic reveal-on-song-end behavior with the manual WebSocket reveal trigger

## Story 12: Voice chat

- [ ] Implement WebRTC signaling over the existing WebSocket layer
- [ ] Implement mesh peer connection setup between session participants
- [ ] Enforce the 8-participant cap per session
- [ ] Integrate Cloudflare TURN, pay-as-you-go, as the ICE server fallback
- [ ] Add join/leave voice UI within a session

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

## Docs cleanup

No story required for these. Docs branch.

- [x] Replace the em dash in every doc file's H1 title (`# FILE.md — Description`) and in `TASKS.md`'s `## Story N — Name` headings with a colon, the writing-style rule against em dashes applies to every markdown file in the repo and these headers are the only place it had slipped through.
