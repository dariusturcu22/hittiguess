# ROADMAP.md: Remaining work order

[TASKS.md](TASKS.md) contains the requirements and tests, in the order they are done. [PROJECT_STATE.md](PROJECT_STATE.md) governs readiness. Completed records and the historical documentation audit are in [ARCHIVE.md](ARCHIVE.md).

## Where the project stands

The game, the catalog pipeline with its admin console, voice and text chat, game history, and the production deployment (Vercel, Azure Container Apps, Neon) are built and live. The app is open to anyone. Three playtests have been fixed. The remaining work falls into four groups, in this order.

## Release milestone

The next milestone is the public launch: the owner announces hittiguess publicly (a LinkedIn post and other marketing). It is reached when every task in TASKS.md up to the end of Release 6 is ticked or explicitly waived by the owner in DECISIONS.md. The owner-only decisions that unblock the most work are the legal basics (data controller, contact mailbox, minimum age), the mobile scope for launch, and whether remote DJ audio from YouTube playback is acceptable under YouTube's terms.

## Order

1. Finish the two branches in progress: the pre-game audio check (`feature/audio-check`, story 52) and the whole-catalog recheck page (`feature/recheck-whole-catalog`, story 55).
2. Catalog accuracy (story 55): bulk acceptance of proposed years, the production recheck, merging the known duplicate songs, and the uniqueness constraints.
3. Gameplay fixes (story 57): reconnect recovery, the import-job 404, and browser coverage of the game screens.
4. Frontend completion (stories 28 and 47): account settings with data export and deletion, the two-factor screens, the desktop visual pass, and the landing page on mobile.
5. Production operations (stories 7, 8, 38): cost alerts and shutdown, the load test and active-game admission limit, the deployment smoke test, Neon backups and restore, metrics and uptime monitoring, and the move to one Hetzner server before the Azure trial credit ends.
6. Release 1 to 6 (story 56): legal pages and compliance, site basics, the security audit, reliability and operations, product polish, and the launch tasks. The Release sections are ordered; the legal rewrite waits on the owner's decisions, and the launch tasks come last.

## After the launch

Usage analytics after definition (story 34); personalized difficulty after enough play data exists (story 30); safety-confidence tuning (story 41); a per-source gather timeout (story 24); curated evidence storage; Flutter link-out. Genre enrichment and Topic-upload upgrades are dropped. Optional deployment reviewers and removed-item motion stay in TASKS.md.
