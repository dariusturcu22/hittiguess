# ROADMAP.md: Remaining work order

[TASKS.md](TASKS.md) contains the requirements and tests, in the order they are done. [PROJECT_STATE.md](PROJECT_STATE.md) governs readiness. Completed records and the historical documentation audit are in [ARCHIVE.md](ARCHIVE.md).

## Where the project stands

The game, the pre-game audio check, the catalog pipeline with its admin console, voice and text chat, game history, account settings with two-factor authentication, and the production deployment (Vercel, Azure Container Apps, Neon) are built and live. The app is open to anyone. The remaining work falls into three groups, in this order.

## Release milestone

The next milestone is the public launch: the owner announces hittiguess publicly (a LinkedIn post and other marketing). It is reached when every task in TASKS.md up to the end of Release 6 is ticked or explicitly waived by the owner in DECISIONS.md. The owner-only decisions that unblock the most work are the legal basics (data controller, contact mailbox, minimum age), the mobile scope for launch, and whether remote DJ audio from YouTube playback is acceptable under YouTube's terms.

## Order

1. Catalog accuracy (story 55): the production recheck and acceptance of proposed years, merging the known duplicate songs, and the uniqueness constraints.
2. Production operations (stories 7, 8): cost alerts and shutdown, Neon backups and restore, the owner's two-window and TURN checks, and the decision on the move to Hetzner.
3. Release 1 to 6 (story 56): legal pages and compliance, site basics, the security audit (input validation, secrets, scanning, remaining security headers and WebSocket items, moderation, admin hardening, the ZAP scan), reliability and operations, product polish, and the launch tasks. The Release sections are ordered; the legal rewrite waits on the owner's decisions, and the launch tasks come last.

The audit follow-ups under "Deferred from the Nov 2026 audit" in TASKS.md are scheduled with the Release 3 and 4 work.

## After the launch

Usage analytics after definition (story 34); personalized difficulty after enough play data exists (story 30); safety-confidence tuning (story 41); a per-source gather timeout (story 24); curated evidence storage; Flutter link-out. Genre enrichment and Topic-upload upgrades are dropped. Optional deployment reviewers and removed-item motion stay in TASKS.md.
