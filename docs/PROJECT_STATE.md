# PROJECT_STATE.md: Story readiness

[TASKS.md](TASKS.md) owns unfinished requirements and tests. [ROADMAP.md](ROADMAP.md) sets order. Completed and dropped work is in [ARCHIVE.md](ARCHIVE.md); gaps in story IDs are expected.

**Ready** means tasks were checked against current code and feature work can start. **Needs Definition** requires a confirmed breakdown first. **Deferred** work waits for its stated dependency. Feature work on deferred stories must become Ready before implementation.

| ID | Remaining scope | Status |
| --- | --- | --- |
| 7 | Cost alerts and shutdown, load test and admission limit, smoke test, production acceptance, Hetzner migration after the Azure trial | Ready |
| 8 | Neon validation, idle wake, backups, catalog import decision | Ready |
| 24 | Optional per-source gather timeout | Deferred until the latency budget needs it |
| 28 | Account settings, two-factor UI, desktop and state acceptance | Ready |
| 30 | Personalized difficulty training and monitoring | Deferred until enough real play data exists |
| 34 | Internal usage/abuse events, query surface, notice behavior | Needs Definition |
| 38 | Production metrics, dashboards, uptime, and delivery verification | Ready |
| 41 | Source-match confidence and uncertain-case review | Deferred until submission data supports tuning |
| 47 | Remaining mockup reconciliation during the visual pass | Ready |
| 52 | Pre-game audio check, pause on a stopped share (in progress on `feature/audio-check`); the rest of the story is built | Ready |
| 55 | Catalog accuracy: whole-catalog recheck page (in progress on `feature/recheck-whole-catalog`), bulk acceptance of proposed years, duplicate merge and uniqueness | Ready |
| 56 | Public release readiness: legal pages and compliance, site basics, security audit, operations, product polish, launch | Ready; owner decisions listed in Release 1 and 5 |
| 57 | Gameplay fixes: reconnect recovery, import-job 404, game-screen browser coverage | Ready |

The main game with the rotating and fixed DJ modes, song audio sharing for Chromium on Windows, ChromeOS and recent macOS, the catalog and import pipeline with its admin console, participant history and statistics, prepared global difficulty, voice and text chat, and the production deployment on Azure, Neon and Vercel are implemented and live. The app is open to anyone; the closed-beta gate was removed on 2026-10-16. What remains is the pre-game audio check, catalog accuracy work, the frontend completion, production operations, and the public release work in story 56, which blocks the public launch and its marketing. The release milestone is defined in [ROADMAP.md](ROADMAP.md).

Curated metadata evidence requires definition before storage. Flutter is deprioritized. Genre enrichment and automatic Topic-upload upgrades are dropped. Product/API behavior belongs in [GAME_DESIGN.md](GAME_DESIGN.md), [ARCHITECTURE.md](ARCHITECTURE.md), and [SYSTEM_REFERENCE.md](SYSTEM_REFERENCE.md); [DECISIONS.md](DECISIONS.md) preserves decision history.
