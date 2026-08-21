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

A full security and bug audit of the pre-split monolith found 51 findings, batched into reviewable groups below. No story needed, this is fix work on the current codebase, same as any other bug. Each batch is its own PR.

- [x] Batch 1: auth cookie and token security, CSRF, refresh token storage, login and registration enumeration
- [ ] Batch 2: OAuth2 hardening, insecure deserialization, account linking, missing-photo crash
- [ ] Batch 3: exception handling and logging, replace System.out/System.err/IO.println with real SLF4J logging (`@Slf4j` from Lombok, already a dependency), stop leaking internal errors, fix wrong status codes
- [ ] Batch 4: input validation and injection hardening on the backend
- [ ] Batch 5: metadata pipeline safety, rate limiting, prompt injection surface
- [ ] Batch 6: export/PDF fixes
- [ ] Batch 7: backend dead code and minor correctness
- [ ] Batch 8: frontend auth/routing, the token refresh hang, the no-op route guard
- [ ] Batch 9: frontend forms and data quality
- [ ] Batch 10: frontend small bugs and cleanup

## Bugs and minor fixes

No story required for these. Fix on a `fix` or `chore` branch.

- [ ] Remove leftover `console.log` in `AddSongForm.tsx`
