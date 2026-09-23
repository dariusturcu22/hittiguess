# ROADMAP.md: Implementation Order

This is the sequential order remaining work gets worked in, derived from the real blocking relationships already recorded in [PROJECT_STATE.md](PROJECT_STATE.md) and [TASKS.md](TASKS.md), not from story ID order. A story's status in `PROJECT_STATE.md` still governs whether it's actually startable today; this file governs the order to reach for the next piece of work once it is.

Phase 0, Phase 1, and nearly all of Phase 2 have shipped their backend scope; those stories are `Implemented` in `PROJECT_STATE.md` and no longer listed as upcoming work below. Stories 22 (test coverage), 30 (difficulty-tuned generation), 35 (ground-truth API), 48 (comment cleanup), and 49 (naming consistency) have also since shipped in full and are archived in `ARCHIVE.md`. What's actually left toward the Local milestone is narrow: story 28's remaining verification gates (the only place any of those stories' frontend lives, per `TASKS.md`'s standing policy), story 38's dashboard import and uptime monitoring, and story 47's still-open items (design-mockup sync, feedback-polish batch, a manual voice check). See the Readiness tiers section below and the Remaining work section that replaces the old phase breakdown.

## Readiness tiers

Three milestones sit above the remaining work below, each a different bar for who can actually use the app:

- **Local**: the project owner can play it, alone or with one other person in the same house over a locally opened port, admin side included. Every backend piece Local needs has shipped, and story 28's frontend implementation now covers Batches A through F. Story 28 still has visual comparison, route smoke, and representative-state gates open at set breakpoints, along with a small number of import states. Story 38's dashboard import and uptime monitoring stay open (uptime monitoring is meaningless without a real deployment, which Local doesn't need). Story 47's design-mockup sync, its feedback-polish batch (loading states, motion, toasts), and a manual voice check also remain. Stories 22, 30, 35, 48, and 49 have all shipped in full. Local doesn't require a real deployment target, story 7 and story 8 both stay undecided until Local ships.
- **Beta**: deployed somewhere real and played with friends and colleagues in demo matches, everything working correctly. Needs story 50 (auth hardening: email verification, password reset, 2FA) built, and story 7 (hosting) and story 8 (database) actually decided and executed, on top of everything Local shipped.
- **Finished**: the fully deployed, publicly announced version. Story 34 (first-party usage analytics) is the one piece of scope left for it, since nothing it covers blocks either Local or Beta.

## What's shipped

Story 39 (group), story 10 (game session), story 11 (WebSocket sync), story 9 (DJ link-out), story 12 (voice chat), story 13 (group text chat), story 14 (song search), story 17 (community reports and confirmations), story 18 (verification promotion), story 24 (parallelized metadata fetches), story 30 (difficulty-tuned generation, including public playlists), story 35 (ground-truth API), story 37 (privacy/GDPR), story 40 (catalog seeding and bulk import), story 41 (submission content safety), story 45 (playlist-to-playlist import), and story 46 (playlist membership) are all `Implemented` on the backend. So is nearly everything else from the original Phase 0/1 backlog (song schema reconciliation, the relational fix, pgvector duplicate detection, Discogs sourcing, rate limiting, the analytics data store, open-source collaboration files, test user infrastructure, the database split and metadata-minimization cross-references, the DeepInfra LLM client). Stories 22 (test coverage), 48 (comment cleanup), and 49 (naming consistency) have shipped and archived in full, and their rows are removed from `PROJECT_STATE.md`'s table entirely. See `ARCHIVE.md` for the full completed list and `PROJECT_STATE.md` for each remaining story's row.

Story 28 now contains the implemented frontend surface for those backend stories through Batch F, and story 47's build clusters have landed on top of it. The remaining work is verification and the explicitly open states listed in `TASKS.md`, not a second frontend implementation pass for every backend story.

## Remaining work

- **Story 30**: fully shipped, backend and frontend. The difficulty model, the three group-scoring strategies, sitelinks-weighted selection, the persisted Wikidata sitelinks count, the international-scope filter, the Difficulty-Based generation and Custom-mode endpoints, public playlists, and the lobby frontend are all built and tested. Only the personalized collaborative-filtering layer stays scaffolded behind an aggregate baseline until real `Guess` volume exists to train and beat it, not blocking Local.
- **Story 28**: the design phase is done, all 53 screens are mocked up in `docs/design/hittiguess-design.html`, and the frontend implementation through Batch F is present. Visual comparison, route smoke, and representative-state passes at set breakpoints remain open, see the batch plan below.
- **Story 22**: shipped and archived in full, backfilling tests across all three services and wiring them into CI.
- **Story 35**: shipped. Only a manual, one-time confirmation read of YouTube's terms stays open as a pre-shipping gate, not code.
- **Story 38**: the observability wiring (metrics, tracing, logs, Sentry) and the automated free-tier usage-limit check are both live; still open is a live dashboard import and uptime monitoring (no production deployment exists yet to monitor).
- **Story 47**: the owner's full feature review pass. Its build clusters (library, explore, playlist detail/edit, imports, lobby/voice/gameplay) have shipped; still open are the `docs/design` mockup sync (needs the canvas editor), a cross-cutting feedback-polish batch (loading states, minor motion, toasts for actions with no other visible result), and a manual voice-delivery spot check.
- **Story 48**: shipped and archived in full.
- **Story 49**: shipped and archived in full.

## Beta: deploy and validate

Starts once everything above has actually shipped, not merely reached `Ready`.

- Story 50: Auth hardening, email verification, real password reset, and TOTP two-factor authentication, all needed before real accounts and real friends are involved
- Story 7: Hosting migration off Fly.io, target platform decided and executed
- Story 8: Database migration off Supabase, whether to migrate at all and to what platform, decided and executed
- Deploy the app for real, invite friends and colleagues for demo matches, confirm everything works correctly before calling it Beta

## Finished

- Story 34: First-party usage analytics, needs story 33 (shipped) and, for its abuse-visibility events specifically, stories 10, 13, 17, and 27 actually shipping too, which they now have. Deliberately last: nothing it covers blocks either Local or Beta.

## Batch plan: story 28's screen clusters, worked in this order

The old batch plan sequenced one story per PR through Phase 1 and Phase 2; that work is done; most of those stories landed out of the order that plan originally picked, through branches it never named, and the plan itself stopped tracking reality several batches ago. It's replaced here with a batch plan for the one piece of sequential work actually left: story 28's implementation phase, broken into the same screen clusters `docs/design/hittiguess-design.html` and `FRONTEND_CONTENT.md` already group the 53 mockups into, each batch wiring its cluster against the real backend the stories above already shipped rather than mock state.

Per `AGENTS.md`'s workflow, each batch is still its own branch off `dev` and its own PR; several may be open for review at once, and the project owner reviews and merges them asynchronously. Update this list's checkmarks as each batch's PR merges into `dev`.

- [x] Batch A: Existing pages redesign, with the new visual system implemented across auth, landing, the shared shell, and playlist/song pages. Route smoke and rendered comparison remain open.
- [x] Batch B: Playlist screens, with Explore, Edit playlist, YouTube import, and copy-from-playlist implemented, including processing, progress, sidebar, and toast states. The choose-source and existing-playlist loading, empty, and error states remain open, along with route smoke and rendered comparison.
- [x] Batch C: Song review and search, with `AddSongForm.tsx` link and keyword search, review states, report, and confirmation actions implemented. Broader search/report state coverage and rendered comparison remain open.
- [x] Batch D: Admin views, with the catalog backlog and report review queue implemented behind the `ADMIN` route guard. Auth/action coverage, route smoke, and rendered comparison remain open.
- [x] Batch E: Gameplay screens, with the group lobby, game session/timeline, betting, DJ link-out and audio cutoff, voice/WebRTC, text chat, turn notification, away widget, and results/leaderboard implemented against the real backends. The visual matrix, representative-state tests, and accessibility checks remain open. Live Playwright gameplay validation requires the backend services to be started from this same checkout.
- [x] Batch F: Cross-cutting implementation tasks, the component/token boundary decision (shadcn primitives versus outright replacement), and the accessibility pass (color contrast, keyboard navigation, the semi-transparent chat overlay and voice sidebar specifically)
