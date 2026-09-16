# ROADMAP.md: Implementation Order

This is the sequential order remaining work gets worked in, derived from the real blocking relationships already recorded in [PROJECT_STATE.md](PROJECT_STATE.md) and [TASKS.md](TASKS.md), not from story ID order. A story's status in `PROJECT_STATE.md` still governs whether it's actually startable today; this file governs the order to reach for the next piece of work once it is.

Phase 0, Phase 1, and nearly all of Phase 2 have shipped their backend scope; those stories are `Implemented` in `PROJECT_STATE.md` and no longer listed as upcoming work below. What's actually left toward the Local milestone is narrow: story 28's frontend implementation (the only place any of those stories' frontend lives, per `TASKS.md`'s standing policy), story 30's own remaining backend scope, and stories 22, 35, 38, 48, and 49 finishing up. See the Readiness tiers section below and the Remaining work section that replaces the old phase breakdown.

## Readiness tiers

Three milestones sit above the remaining work below, each a different bar for who can actually use the app:

- **Local**: the project owner can play it, alone or with one other person in the same house over a locally opened port, admin side included. Every backend piece Local needs has shipped; what's left is story 28's frontend implementation (all of it, across every screen the newly-shipped backend stories unlocked), story 30's own remaining backend scope, and stories 22 (test coverage), 35, 38, 48, and 49 finishing up. Story 28 runs before story 22, 48, and 49 wrap up: story 22 audits the finished app surface rather than one still mid-implementation, and 48/49 catch drift the whole build introduces, frontend included. Local doesn't require a real deployment target, story 7 and story 8 both stay undecided until Local ships.
- **Beta**: deployed somewhere real and played with friends and colleagues in demo matches, everything working correctly. Needs story 50 (auth hardening: email verification, password reset, 2FA) built, and story 7 (hosting) and story 8 (database) actually decided and executed, on top of everything Local shipped.
- **Finished**: the fully deployed, publicly announced version. Story 34 (first-party usage analytics) is the one piece of scope left for it, since nothing it covers blocks either Local or Beta.

## What's shipped

Story 39 (group), story 10 (game session), story 11 (WebSocket sync), story 9 (DJ link-out), story 12 (voice chat), story 13 (group text chat), story 14 (song search), story 17 (community reports and confirmations), story 18 (verification promotion), story 24 (parallelized metadata fetches), story 37 (privacy/GDPR), story 40 (catalog seeding and bulk import), story 41 (submission content safety), story 45 (playlist-to-playlist import), and story 46 (playlist membership) are all `Implemented` on the backend. So is nearly everything else from the original Phase 0/1 backlog (song schema reconciliation, the relational fix, pgvector duplicate detection, Discogs sourcing, rate limiting, the analytics data store, open-source collaboration files, test user infrastructure, the database split and metadata-minimization cross-references, the DeepInfra LLM client). See `ARCHIVE.md` for the full completed list and `PROJECT_STATE.md` for each story's row.

Every one of those stories still carries open frontend tasks, all deferred to story 28 per `TASKS.md`'s standing policy, that's the bulk of what's actually left to build.

## Remaining work

- **Story 30, backend**: the difficulty model, the three group-scoring strategies, sitelinks-weighted selection through a fallback seam, and public playlists are built. Still open, confirmed against `TASKS.md`'s story 30 section: persisting the Wikidata sitelinks count on `Song`, the international-scope filter it gates, the Difficulty-Based generation endpoint, and the Custom-mode endpoint. The personalized collaborative-filtering layer stays scaffolded behind an aggregate baseline until real `Guess` volume exists to train and beat it, not blocking the rest.
- **Story 28**: the design phase is done, all 53 screens mocked up in `docs/design/hittiguess-design.html`. Every implementation task remains, see the batch plan below.
- **Story 22**: test coverage, backfills and adds tests for everything Local ships, runs after story 28 for the same reason it always has, auditing a finished app surface beats rewriting tests as the rest of Local still lands.
- **Story 35**: needs story 18's lock rule (shipped) actually producing verified rows to publish; ready to build.
- **Story 38**: the observability wiring (metrics, tracing, logs, Sentry) is live; still open is a live dashboard import, uptime monitoring (no production deployment exists yet to monitor), and an automated free-tier usage-limit check.
- **Story 48**: comment cleanup, runs after story 28 so it also catches comment drift the frontend implementation introduces, not just what exists today.
- **Story 49**: naming consistency, same reasoning as story 48, runs after story 28 so it also catches any leftover old-name references the frontend implementation introduces.

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

- [ ] Batch A: Existing pages redesign, apply the new visual system to auth (login, register, forgot-password, OAuth2 redirect), the landing page, and the dashboard/playlist/song pages already live today (playlist list, playlist detail, song detail, add song, join-by-invite), replacing the current shadcn theme tokens with the new ones
- [ ] Batch B: Planned playlist screens, build Explore public playlists (story 30), Import playlist (story 40's bulk import and story 45's copy-from-playlist, both with their live-progress and confirm-step UI), and Edit playlist (story 46's member list and grants, story 30's publish/unpublish toggle)
- [ ] Batch C: Song review and search, wire `AddSongForm.tsx` and `DataTable` to story 14's search endpoint, add the report button and thumbs-up confirmation affordances from story 17
- [ ] Batch D: Admin views, the catalog backlog status screen (story 40) and the report review queue (story 17), both gated on the `ADMIN` role
- [ ] Batch E: Gameplay screens, group lobby, game session/timeline (drag-and-drop placement, betting, animated guess feedback), DJ view (story 9's link-out action and its audio-sharing warning), voice sidebar (story 12), text chat overlay (story 13), turn notification, the playing-while-away widget, and the results/leaderboard screen, wired against stories 9/10/11/12/13/39's real backends
- [ ] Batch F: Cross-cutting implementation tasks, the component/token boundary decision (shadcn primitives versus outright replacement), and the accessibility pass (color contrast, keyboard navigation, the semi-transparent chat overlay and voice sidebar specifically)
