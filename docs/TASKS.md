# TASKS.md — What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 7, 9, and 12, are drafts written during planning, before this repository's actual code was available to check against. They have not yet been confirmed against the real implementation. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

## Story 6 — Two-service split

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

## Session tooling

No story required for these. Chore branch.

- [x] Write a script that moves fully-checked-off `TASKS.md` sections into `ARCHIVE.md`, so the session-end habit in `CLAUDE.md` doesn't depend on remembering to do it by hand. For a `## Story N — ...` section, only archive it once `PROJECT_STATE.md` also has that story's status as `Implemented`, and remove its row there too. Update `CLAUDE.md`'s session-end habit to point at running the script.
