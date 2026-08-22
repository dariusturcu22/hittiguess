# TASKS.md: What To Actually Work On

This is the source of truth for day-to-day work. Consult PROJECT_STATE.md only when you need the bigger picture behind one of these.

The tasks below, under stories 7, 9, and 12, are drafts written during planning, before this repository's actual code was available to check against. They have not yet been confirmed against the real implementation. Before starting any of them, check them against the current code: some tasks may already be done, some may not apply the way they're written, and some may be missing. Once a story's tasks are confirmed accurate, update its status to Ready in PROJECT_STATE.md.

"Next available task" means the earliest unchecked box under a Ready or In Progress story.

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

## Session tooling

No story required for these. Chore branch.

- [x] Write a script that moves fully-checked-off `TASKS.md` sections into `ARCHIVE.md`, so the session-end habit in `CLAUDE.md` doesn't depend on remembering to do it by hand. For a `## Story N — ...` section, only archive it once `PROJECT_STATE.md` also has that story's status as `Implemented`, and remove its row there too. Update `CLAUDE.md`'s session-end habit to point at running the script.

## Local dev tooling

No story required for these. Chore branch.

- [x] Add a root `Makefile` with a `dev` target that starts the local Postgres container, the core service, the AI microservice, and the frontend with one command, `make dev`. Extend it with the TURN server once story 12 (voice chat) adds one.

## Docs cleanup

No story required for these. Docs branch.

- [x] Replace the em dash in every doc file's H1 title (`# FILE.md — Description`) and in `TASKS.md`'s `## Story N — Name` headings with a colon, the writing-style rule against em dashes applies to every markdown file in the repo and these headers are the only place it had slipped through.
