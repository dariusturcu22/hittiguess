# ROADMAP.md: Implementation Order

[TASKS.md](TASKS.md) owns requirements and tests; [PROJECT_STATE.md](PROJECT_STATE.md) governs story readiness. [REMAINING_WORK.md](REMAINING_WORK.md) lists the complete remaining scope. Completed implementation records are in [ARCHIVE.md](ARCHIVE.md).

## Audit completion

Automatic verified-song embedding indexing is ready for review in [PR #274](https://github.com/dariusturcu22/hittiguess/pull/274). The documentation reconciliation resolves stale claims and archives completed clusters. No source-of-truth decision from the reviewed audit batches remains unanswered. Account/legal requirements and personalized models retain their deferrals; production and visual acceptance remain separate tasks.

## Local and Beta priorities

1. Revoke the YouTube key recorded as exposed in production logs and configure its restricted replacement. Complete the remaining Azure cost controls.
2. Close missed-event reconnect recovery and its tests. Finish two-factor frontend states under story 28, against the implemented story 50 backend.
3. Complete the desktop dark/light route/state comparison and focused coverage gaps. Only the landing page has mobile scope. Reconcile remaining mockup states with approved requirements, then complete contrast, keyboard, motion, and navigation checks.
4. Verify production login, email delivery, shared cookies, HTTP probes, WSS, core-to-AI communication, live observability, and configured TURN. The Azure apps, Vercel frontend, and Neon projects already exist.
5. Load-test capacity and admission, validate resource limits and cold-start behavior, verify Neon boundaries/idle wake, and rehearse backups and the Hetzner fallback.
6. Complete HTTPS two-client and final LAN full-game/voice acceptance, including import, reconnect, DJ capture, and results before Beta invitations.

Catalog uniqueness cleanup and concurrent-gather priority verification remain backend follow-ups. Optional deployment reviewers, removed-item exit motion, and the exact Joined-tab copy retain their own decisions in TASKS.md.

## Later work

Story 34 needs confirmed event/dashboard requirements before implementation. It covers internal first-party usage and abuse visibility, tests, and the reviewed notice behavior. Core participant history and statistics already exist.

Account/legal pages and disclosure work, personalized difficulty models, source-match safety tuning, gather-boundary timeout, raw-evidence storage, and Flutter link-out remain deferred or require definition. Genre enrichment and Topic-upload upgrade suggestions are dropped.
