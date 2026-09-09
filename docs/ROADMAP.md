# ROADMAP.md: Implementation Order

This is the sequential order remaining stories get worked in, derived from the real blocking relationships already recorded in [PROJECT_STATE.md](PROJECT_STATE.md) and [TASKS.md](TASKS.md), not from story ID order. A story's status in `PROJECT_STATE.md` still governs whether it's actually startable today; this file governs the order to reach for the next one once it is. Stories already `Implemented` are in [ARCHIVE.md](ARCHIVE.md) and don't appear here. Dropped and consolidated stories don't appear here either, see `ARCHIVE.md`'s "Dropped and consolidated stories" section.

Within a phase, stories are independent of each other and can be worked in any order, including in parallel. A phase only starts once every story it depends on has actually shipped, not merely reached `Ready`.

## Phase 0: Spike handoff completion

Unlocks the metadata/AI track's remaining stories. Both items are the last unstarted tasks under their own spike sections in `TASKS.md`, not new scope.

- Build `ai/app/metadata/sources/wikidata.py`, un-stub `musicbrainz.py`, add `wikipedia.py` ("Spike: MusicBrainz and Wikidata sourcing")
- Decide whether the fast/patient tier pipeline shape and shortlisted LLM candidate are worth building into production, or need further validation first ("Spike: Local/cheap LLM option")

## Phase 1: Independent foundational work

No blockers among these, and none block each other. Includes both game-session infrastructure and catalog/quality work that has nothing to do with it.

- Story 39: Group
- Story 10: Game session
- Story 11: Real-time sync over WebSocket, build alongside stories 10 and 39; both need it functionally even though each is independently startable
- Story 23: Song schema reconciliation, unlocks stories 18, 35, and coordinates with 15, 16, and 40
- Story 15: Song/playlist relational fix, coordinate with story 23
- Story 16: pgvector-based duplicate detection
- Story 25: Add Discogs as a metadata source
- Story 14: Song search by link or keyword
- Story 22: Test coverage
- Story 27: Rate limiting
- Story 33: Analytics data store
- Story 36: Open-source collaboration readiness
- Story 37: Privacy policy, terms of service, GDPR compliance
- Story 38: Observability
- Story 42: Explicit database split
- Story 43: Metadata minimization
- Story 44: Test user infrastructure
- Story 28: UI redesign, design phase (the implementation phase moves to Phase 2, see below)

## Phase 2: Depends on Phase 0 and Phase 1

- Story 18: Criteria for promoting a song to verified, needs story 23's `verificationStatus` field
- Story 40: Catalog seeding queue and user-facing bulk import, needs story 23 (schema) and Phase 0's sourcing-spike implementation
- Story 20: Local LLM option, needs the same schema and implementation work as story 40
- Story 24: Parallelize metadata pipeline fetches, needs story 25 and Phase 0's sourcing-spike implementation
- Story 41: Submission content safety, needs Phase 0's sourcing-spike implementation
- Story 17: Community song reports and confirmations, the report/confirmation submission flow itself is unblocked, but the admin review surface needs story 40's admin role
- Story 26: Cache metadata pipeline results, first task is the scope-review decision already noted in `TASKS.md`, may not proceed past it
- Story 9: DJ real YouTube link-out, needs stories 10, 11, and 39 actually built, not just `Ready`
- Story 12: Voice chat, needs stories 11 and 39 actually built
- Story 13: Group-scoped text chat, needs stories 11 and 39 actually built
- Story 30: Difficulty-tuned game session generation, needs story 10's `Guess` entity accumulating real data
- Story 28: UI redesign, implementation phase, wires the new visual system to the gameplay screens as stories 10/11/39 land

## Phase 3: Depends on Phase 2

- Story 34: First-party usage analytics, needs story 33 and, for its abuse-visibility events specifically, stories 10, 13, 17, and 27 actually shipped
- Story 35: Public ground-truth data API, needs story 23's `verificationStatus` field and, in practice, story 18's lock rule actually producing verified rows to publish

## Deferred by explicit decision, not blocked

Both stay open questions rather than scheduled into a phase; see `PROJECT_STATE.md`'s open questions for the reasoning behind deferring each until the app is closer to feature-complete.

- Story 7: Hosting migration off Fly.io
- Story 8: Database migration off Supabase
