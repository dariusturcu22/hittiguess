# ROADMAP.md: Remaining work order

[TASKS.md](TASKS.md) contains the requirements and tests. [PROJECT_STATE.md](PROJECT_STATE.md) governs readiness. Completed records and the historical documentation audit are in [ARCHIVE.md](ARCHIVE.md).

1. Fix the production playtest findings (stories 52 and 53), the separate song stream first.
2. Finish Azure cost controls (anomaly alerts and the shutdown Action Group).
3. Fix missed-event reconnect recovery and finish two-factor frontend states.
4. Verify the rest of production access: password-reset delivery, HTTPS/WSS, and core-to-AI communication.
5. Complete live TURN, voice, and DJ audio checks, then production full-game acceptance.
6. Finish the desktop dark/light visual/state pass, mockup reconciliation, accessibility, and focused test gaps. Mobile scope covers only the landing page.
7. Validate capacity/admission, resource limits, cold-start behavior, Neon boundaries/idle wake, backups, production observability, and the Hetzner migration.

Story 51 (admin console and the parallel source pipeline) follows the production access checks. Catalog uniqueness and concurrent-gather priority are backend follow-ups. Optional deployment reviewers, Joined-tab wording, and removed-item motion remain in TASKS.md.

Later work includes usage analytics after definition; account/legal views; personalized difficulty after sufficient data; safety-confidence tuning; gather timeout; curated evidence storage; and Flutter link-out. Genre enrichment and Topic-upload upgrades are dropped.
