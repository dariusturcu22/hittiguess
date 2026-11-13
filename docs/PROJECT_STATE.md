# PROJECT_STATE.md: Story readiness

[TASKS.md](TASKS.md) owns unfinished requirements and tests. [ROADMAP.md](ROADMAP.md) sets order. Completed and dropped work is in [ARCHIVE.md](ARCHIVE.md); gaps in story IDs are expected.

**Ready** means tasks were checked against current code and feature work can start. **Needs Definition** requires a confirmed breakdown first. **Deferred** work waits for its stated dependency. Feature work on deferred stories must become Ready before implementation.

| ID | Remaining scope | Status |
| --- | --- | --- |
| 7 | Cost alerts and shutdown with their test, production acceptance, password-reset email check, Hetzner decision, deployment reviewers | Ready |
| 8 | Neon validation, idle wake, backups, catalog import decision | Ready |
| 24 | Optional per-source gather timeout | Deferred until the latency budget needs it |
| 30 | Personalized difficulty training and monitoring | Deferred until enough real play data exists |
| 34 | Internal usage/abuse events, query surface, notice behavior | Needs Definition |
| 41 | Source-match confidence and uncertain-case review | Deferred until submission data supports tuning |
| 55 | Catalog accuracy: production recheck and year acceptance, duplicate merge, uniqueness constraints, per-tier song counts (the recheck page and bulk acceptance are built) | Ready |
| 56 | Public release readiness: legal pages and compliance, site basics, security audit, operations, product polish, launch | Ready; owner decisions listed in Release 1 and 5 |
| 57 | Gameplay acceptance: real-browser two-window test and TURN check (the gameplay fixes are built) | Ready |

The main game with the rotating and fixed DJ modes, song audio sharing for Chromium on Windows, ChromeOS and recent macOS, the pre-game audio check, the catalog and import pipeline with its admin console and whole-catalog recheck, participant history and statistics, prepared global difficulty, voice and text chat, account settings with two-factor authentication, data export and deletion, retention sweepers, the public about and credits pages with SEO metadata, and the production deployment on Azure, Neon and Vercel are implemented and live. The app is open to anyone; the closed-beta gate was removed on 2026-10-16. The security audit (story 56, Release 3) has its authorization matrix, CSRF, CORS, rate limiting, STOMP SEND guard, session-row locking and several security headers merged; input validation and injection, secrets handling, dependency and code scanning, remaining WebSocket items, moderation, admin hardening and the ZAP scan are still open. What remains overall is production operations, the legal and launch work in story 56, and the items in [TASKS.md](TASKS.md) under "Deferred from the Nov 2026 audit". The release milestone is defined in [ROADMAP.md](ROADMAP.md).

Curated metadata evidence requires definition before storage. Flutter is deprioritized. Genre enrichment and automatic Topic-upload upgrades are dropped. Product/API behavior belongs in [GAME_DESIGN.md](GAME_DESIGN.md), [ARCHITECTURE.md](ARCHITECTURE.md), and [SYSTEM_REFERENCE.md](SYSTEM_REFERENCE.md); [DECISIONS.md](DECISIONS.md) preserves decision history.
