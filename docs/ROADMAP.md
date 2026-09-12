# ROADMAP.md: Implementation Order

This is the sequential order remaining stories get worked in, derived from the real blocking relationships already recorded in [PROJECT_STATE.md](PROJECT_STATE.md) and [TASKS.md](TASKS.md), not from story ID order. A story's status in `PROJECT_STATE.md` still governs whether it's actually startable today; this file governs the order to reach for the next one once it is. Stories already `Implemented` are in [ARCHIVE.md](ARCHIVE.md) and don't appear here. Dropped and consolidated stories don't appear here either, see `ARCHIVE.md`'s "Dropped and consolidated stories" section.

Within a phase, stories are independent of each other and can be worked in any order, including in parallel. A phase only starts once every story it depends on has actually shipped, not merely reached `Ready`.

## Phase 0: Spike handoff completion

Unlocks the metadata/AI track's remaining stories. The one remaining item is the last unstarted task under its own spike section in `TASKS.md`, not new scope. The spike's other handoff item, whether the fast/patient tier pipeline shape and shortlisted LLM candidate are worth building into production, is resolved: greenlit, see `DECISIONS.md` and story 20.

- Build `ai/app/metadata/sources/wikidata.py`, un-stub `musicbrainz.py`, add `wikipedia.py` ("Spike: MusicBrainz and Wikidata sourcing")

## Phase 1: Independent foundational work

No blockers among these, and none block each other. Includes both game-session infrastructure and catalog/quality work that has nothing to do with it.

- Story 39: Group
- Story 10: Game session
- Story 11: Real-time sync over WebSocket, build alongside stories 10 and 39; both need it functionally even though each is independently startable
- Story 23: Song schema reconciliation, unlocks stories 18, 35, and coordinates with 15, 16, and 40
- Story 15: Song/playlist relational fix, coordinate with story 23
- Story 46: Playlist membership (owner/admin, per-member permissions, kick/ban, per-playlist join identity)
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
- Story 20: LLM client infrastructure for the metadata pipeline, no schema dependency, moved here from Phase 2 once greenlit

## Phase 2: Depends on Phase 0 and Phase 1

- Story 18: Criteria for promoting a song to verified, needs story 23's `verificationStatus` field, and in practice story 20's DeepInfra client for its Wikipedia-extraction step
- Story 40: Catalog seeding queue and user-facing bulk import, needs story 23 (schema) and Phase 0's sourcing-spike implementation
- Story 24: Parallelize metadata pipeline fetches, needs story 25 and Phase 0's sourcing-spike implementation
- Story 41: Submission content safety, needs Phase 0's sourcing-spike implementation
- Story 17: Community song reports and confirmations, the report/confirmation submission flow itself is unblocked, but the admin review surface needs story 40's admin role
- Story 26: Cache metadata pipeline results, first task is the scope-review decision already noted in `TASKS.md`, may not proceed past it
- Story 9: DJ real YouTube link-out, needs stories 10, 11, and 39 actually built, not just `Ready`
- Story 12: Voice chat, needs stories 11 and 39 actually built
- Story 13: Group-scoped text chat, needs stories 11 and 39 actually built
- Story 30: Difficulty-tuned game session generation, needs story 10's `Guess` entity accumulating real data
- Story 45: Import songs from an existing playlist, needs story 15's join table
- Story 28: UI redesign, implementation phase, wires the new visual system to the gameplay screens as stories 10/11/39 land

## Phase 3: Depends on Phase 2

- Story 34: First-party usage analytics, needs story 33 and, for its abuse-visibility events specifically, stories 10, 13, 17, and 27 actually shipped
- Story 35: Public ground-truth data API, needs story 23's `verificationStatus` field and, in practice, story 18's lock rule actually producing verified rows to publish

## Batch plan: one PR per batch, worked in this order

Phase 1 lists its stories as independent and workable in any order, including in parallel. This section picks one concrete, sequential order for a single implementer working through them one PR at a time, so a session doesn't have to re-derive a starting point every time. A batch is normally one story; two batches combine a story's tasks only where the docs already say to (15 and 23 both touch `Song`). Update this list's checkmark as each batch's PR merges into `dev`; the list itself doesn't move to `ARCHIVE.md`, it stays as the reference for the next batch to work from.

Per `CLAUDE.md`'s batching workflow: only one PR from this list is open for review at a time. Work on the next batch can proceed locally once the current PR is opened, but that next batch's own PR isn't opened until the current one merges.

- [x] Batch 1: Phase 0's metadata source implementation (`wikidata.py`, `musicbrainz.py`, `wikipedia.py`)
- [x] Batch 2: Story 20, the DeepInfra client
- [x] Batch 3: Story 23, Song schema reconciliation
- [x] Batch 4: Story 15, Song/playlist relational fix
- [x] Batch 5: Story 46, Playlist membership
- [x] Batch 6: Story 16, pgvector duplicate detection
- [x] Batch 7: Story 25, Discogs source
- [x] Batch 8: Story 14, Song search by link or keyword
- [ ] Batch 9: Story 39, Group
- [ ] Batch 10: Story 11, WebSocket sync
- [ ] Batch 11: Story 10, Game session
- [ ] Batch 12: Story 33, Analytics data store
- [ ] Batch 13: Story 27, Rate limiting
- [ ] Batch 14: Story 38, Observability
- [ ] Batch 15: Story 37, Privacy policy, terms of service, GDPR compliance
- [ ] Batch 16: Story 36, Open-source collaboration readiness
- [ ] Batch 17: Story 44, Test user infrastructure
- [ ] Batch 18: Story 22, Test coverage
- [ ] Batch 19: Story 42, database split cross-references, and story 43, metadata minimization, combined into one small batch, both are already fully satisfied except a couple of standing re-check notes
- [ ] Batch 20: Story 28, UI redesign implementation phase for whatever of the gameplay screens Batches 9-11 have unlocked by this point; verify the design mockups against the shipped screens before starting rather than assuming they still match
- Phase 2 and Phase 3 stories aren't broken into batches yet, their tasks may shift once Phase 1 actually ships (particularly stories 18, 40, and 30, which reference real entities Batches 3, 4, and 11 create); revisit this list once Phase 1 is done rather than pre-sequencing Phase 2 now

## Deferred by explicit decision, not blocked

Both stay open questions rather than scheduled into a phase; see `PROJECT_STATE.md`'s open questions for the reasoning behind deferring each until the app is closer to feature-complete.

- Story 7: Hosting migration off Fly.io
- Story 8: Database migration off Supabase
