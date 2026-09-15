# ROADMAP.md: Implementation Order

This is the sequential order remaining stories get worked in, derived from the real blocking relationships already recorded in [PROJECT_STATE.md](PROJECT_STATE.md) and [TASKS.md](TASKS.md), not from story ID order. A story's status in `PROJECT_STATE.md` still governs whether it's actually startable today; this file governs the order to reach for the next one once it is. Stories already `Implemented` are in [ARCHIVE.md](ARCHIVE.md) and don't appear here. Dropped and consolidated stories don't appear here either, see `ARCHIVE.md`'s "Dropped and consolidated stories" section.

Within a phase, stories are independent of each other and can be worked in any order, including in parallel. A phase only starts once every story it depends on has actually shipped, not merely reached `Ready`.

## Readiness tiers

Three milestones sit above the phase breakdown below, each a different bar for who can actually use the app:

- **Local**: the project owner can play it, alone or with one other person in the same house over a locally opened port, admin side included. Covers Phase 0, Phase 1, and Phase 2 below, plus story 48 (comment cleanup), story 49 (naming consistency), and story 22 (test coverage) tacked onto the end of Phase 2, in that order. Story 22 runs last within Local because it audits everything else Local built; running it earlier would mean rewriting its tests as the rest of Local's stories still land. Local doesn't require a real deployment target, story 7 and story 8 both stay undecided until Local ships.
- **Beta**: deployed somewhere real and played with friends and colleagues in demo matches, everything working correctly. Needs story 7 (hosting) and story 8 (database) actually decided and executed, on top of everything Local shipped.
- **Finished**: the fully deployed, publicly announced version. Phase 3 only, and the lowest priority in this file, since nothing in Phase 3 blocks either Local or Beta.

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
- Story 27: Rate limiting
- Story 33: Analytics data store
- Story 36: Open-source collaboration readiness
- Story 37: Privacy policy, terms of service, GDPR compliance
- Story 38: Observability
- Story 42: Explicit database split
- Story 43: Metadata minimization
- Story 44: Test user infrastructure
- Story 20: LLM client infrastructure for the metadata pipeline, no schema dependency, moved here from Phase 2 once greenlit

Story 28's design phase (fresh visual direction across every existing page and every not-yet-built gameplay screen) is done, see `docs/design/hittiguess-design.html` and `TASKS.md`. Its implementation phase moves to Phase 2 below. Story 22 (test coverage) moves out of this phase entirely, see the Readiness tiers section above; it now runs at the end of Phase 2 instead.

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
- Story 28: UI redesign, implementation phase, wires the already-designed visual system to the gameplay screens as stories 10/11/39 land
- Story 48: Comment cleanup, done after everything else above for the same reason, catches comment drift from every story that lands before it, not just the code that exists today
- Story 49: Naming consistency, done after everything else above so it also catches any leftover old-name references those stories introduce along the way, not just the ones that exist today
- Story 22: Test coverage, the last story in Local, backfills and adds tests for everything Local shipped, including this phase, once there's a finished app surface to test rather than one still mid-implementation

## Phase 3: Depends on Phase 2, part of Finished, not Beta

Lowest priority in this file. Neither story blocks Beta; both wait until after Beta ships.

- Story 34: First-party usage analytics, needs story 33 and, for its abuse-visibility events specifically, stories 10, 13, 17, and 27 actually shipped
- Story 35: Public ground-truth data API, needs story 23's `verificationStatus` field and, in practice, story 18's lock rule actually producing verified rows to publish

## Beta: deploy and validate

Starts once every Local story above has actually shipped, not merely reached `Ready`.

- Story 7: Hosting migration off Fly.io, target platform decided and executed
- Story 8: Database migration off Supabase, whether to migrate at all and to what platform, decided and executed
- Deploy the app for real, invite friends and colleagues for demo matches, confirm everything works correctly before calling it Beta

## Batch plan: one PR per batch, worked in this order

Phase 1 lists its stories as independent and workable in any order, including in parallel. This section picks one concrete, sequential order for a single implementer working through them one PR at a time, so a session doesn't have to re-derive a starting point every time. A batch is normally one story; two batches combine a story's tasks only where the docs already say to (15 and 23 both touch `Song`). Update this list's checkmark as each batch's PR merges into `dev`; the list itself doesn't move to `ARCHIVE.md`, it stays as the reference for the next batch to work from.

Per `AGENTS.md`'s batching workflow: only one PR from this list is open for review at a time. Work on the next batch can proceed locally once the current PR is opened, but that next batch's own PR isn't opened until the current one merges.

- [x] Batch 1: Phase 0's metadata source implementation (`wikidata.py`, `musicbrainz.py`, `wikipedia.py`)
- [x] Batch 2: Story 20, the DeepInfra client
- [x] Batch 3: Story 23, Song schema reconciliation
- [x] Batch 4: Story 15, Song/playlist relational fix
- [x] Batch 5: Story 46, Playlist membership
- [x] Batch 6: Story 16, pgvector duplicate detection
- [x] Batch 7: Story 25, Discogs source
- [x] Batch 8: Story 14, Song search by link or keyword
- [x] Batch 9: Story 39, Group
- [x] Batch 10: Story 11, WebSocket sync
- [x] Batch 11: Story 10, Game session
- [x] Batch 12: Story 33, Analytics data store
- [x] Batch 13: Story 27, Rate limiting
- [x] Batch 14: Story 38, Observability. Merged, but the story itself stays `Ready`, not `Implemented`: uptime monitoring, a free-tier usage-limit check, and a CI-committed real-credentials test are still open, see `PROJECT_STATE.md` and `TASKS.md`
- [x] Batch 15: Story 37, Privacy policy, terms of service, GDPR compliance
- [x] Batch 16: Story 36, Open-source collaboration readiness
- [x] Batch 17: Story 44, Test user infrastructure
- [ ] Batch 18: dropped. Was story 22, test coverage; story 22 moved to the end of Phase 2, see the Readiness tiers section above, and gets a new batch number once Phase 2 is sequenced
- [x] Batch 19: Story 42, database split cross-references, and story 43, metadata minimization, combined into one small batch, both are already fully satisfied except a couple of standing re-check notes
- [ ] Batch 20: Story 28, UI redesign implementation phase for whatever of the gameplay screens Batches 9-11 have unlocked by this point; the design mockups themselves are done, see `docs/design/hittiguess-design.html`
- Phase 2 stories, including stories 48 and 49 and the relocated story 22, aren't broken into batches yet, their tasks may shift once Phase 1 actually ships (particularly stories 18, 40, and 30, which reference real entities Batches 3, 4, and 11 create); revisit this list once Phase 1 is done rather than pre-sequencing Phase 2 now
