# TASKS.md — What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 6, 7, 9, and 12, are drafts written during planning, before this repository's actual code was available to check against. They have not yet been confirmed against the real implementation. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Story 6 — Two-service split

- [ ] Scaffold the FastAPI project structure for the AI microservice
- [ ] Move metadata pipeline logic (source fetches and LLM synthesis) from Spring Boot into the AI microservice
- [ ] Replace regex JSON parsing with Pydantic structured output
- [ ] Remove the Spring AI dependency from the core service
- [ ] Add an internal endpoint on the AI microservice, for example `POST /metadata/resolve`
- [ ] Wire the core service to call the AI microservice internally
- [ ] Confirm schema ownership stays with the core service, no migrations in the AI microservice

## Story 7 — Azure migration

- [ ] Confirm the current Postgres host (see story 23, may need the schema reconciliation done first)
- [ ] Provision an Azure Container Apps environment
- [ ] Deploy the core service container
- [ ] Deploy the AI microservice container, same environment, for internal networking
- [ ] Provision Azure Database for PostgreSQL Flexible Server, enable pgvector
- [ ] Migrate data to the new Postgres instance
- [ ] Verify both services and the frontend work end to end against Azure
- [ ] Decommission Fly.io once verified

## Story 9 — DJ real YouTube link-out

- [ ] Remove the current embedded/hidden iframe from the DJ view
- [ ] Add an "open in YouTube" link-out for remote sessions, opening a new browser tab
- [ ] Wire WebRTC tab audio capture to that new tab
- [ ] Add deep-link handling for in-person sessions (Android intent, iOS universal link, fallback to a plain browser link)
- [ ] Update QR code generation to point at the real YouTube URL
- [ ] Replace the automatic reveal-on-song-end behavior with the manual WebSocket reveal trigger

## Story 12 — Voice chat

- [ ] Implement WebRTC signaling over the existing WebSocket layer
- [ ] Implement mesh peer connection setup between session participants
- [ ] Enforce the 8-participant cap per session
- [ ] Integrate Cloudflare TURN, pay-as-you-go, as the ICE server fallback
- [ ] Add join/leave voice UI within a session

## Audit fixes

A full security and bug audit of the pre-split monolith found 51 findings, batched into reviewable groups below. No story needed, this is fix work on the current codebase, same as any other bug. Each batch is its own PR. Every `// TODO` and `// FIXME` comment found in the backend during the audit gets resolved somewhere in these batches too, either fixed or, where the code was already correct, replaced with a real explanation. None should be left by the time batch 6 is done.

- [x] Batch 1: auth cookie and token security, CSRF, refresh token storage, login and registration enumeration
- [ ] Batch 2: OAuth2 hardening plus backend exception handling and logging, insecure deserialization, account linking, missing-photo crash, replace System.out/System.err/IO.println with real SLF4J logging (`@Slf4j` from Lombok, already a dependency), stop leaking internal errors, fix wrong status codes. Also closes out every `// TODO`/`// FIXME` in the backend exception-handling and security code: replaces the generic `RuntimeException` throws with a proper `ConflictException` for already-exists cases and Spring Security's own `AccessDeniedException` for access-denied cases (both then need no custom handler for the 403 case, Spring's `ExceptionTranslationFilter` already does that), fixes the deprecated `DaoAuthenticationProvider` constructor usage in `SecurityConfig`, and replaces the two confused TODOs in `JwtAuthenticationFilter` with a short explanation now that they're understood, both turned out to be correct code.
- [ ] Batch 3: backend input validation, injection hardening, and metadata pipeline safety. Adds `@Pattern` validation for `youtubeId` on create and update, makes `MetadataParser`'s fallback return null instead of the raw unvalidated input (closes the last remaining TODO), URL-encodes the YouTube API call, escapes MusicBrainz's Lucene query, makes `FlexibleYearDeserializer` reject malformed years instead of silently corrupting them, strengthens the LLM prompt's framing around untrusted video-description text, and rate-limits `/api/metadata/song` to one in-flight request per user rather than letting one user queue unlimited concurrent slow requests. Not included: the regex-stripped LLM parsing is left as is, since it gets replaced properly by Pydantic structured output during the story 6 split, fixing it twice isn't worth it.
- [ ] Batch 4: export/PDF fixes plus backend dead code and minor correctness
- [ ] Batch 5: frontend auth/routing plus forms and data quality. Fixes the dead processQueue so queued requests actually resolve when a token refresh completes. Makes proxy.ts actually redirect unauthenticated visitors instead of always calling next(), and along the way fixes its route list: "/landing" matched nothing real since (landing) is a route group stripped from the URL, the real landing page is "/", and "/forgot-password" was missing entirely. Clears the query cache on logout. Adds real youtubeId, release year, and hex color validation to both song forms, with visible error messages instead of silent failures on submit. Makes the non-functional forgot-password form honest about not being implemented yet instead of silently doing nothing, building a real password-reset flow is a new feature, not a bug fix, out of scope here. Also removes the leftover console.log in AddSongForm.tsx while already in that file, closing out that separately-tracked bug below.
- [ ] Batch 6: frontend small bugs and cleanup

## Bugs and minor fixes

No story required for these. Fix on a `fix` or `chore` branch.

- [x] Remove leftover `console.log` in `AddSongForm.tsx` (done as part of batch 5)
