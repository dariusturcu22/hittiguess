# ROADMAP.md: Remaining work order

[TASKS.md](TASKS.md) contains the requirements and tests. [PROJECT_STATE.md](PROJECT_STATE.md) governs readiness. Completed records and the historical documentation audit are in [ARCHIVE.md](ARCHIVE.md).

1. Revoke the exposed YouTube key and complete Azure cost controls.
2. Fix missed-event reconnect recovery and finish two-factor frontend states.
3. Verify production access: ordinary accounts, email delivery, Google login, cookies, health probes, HTTPS/WSS, and core-to-AI communication.
4. Complete live TURN, voice, and DJ audio checks, then production and LAN full-game acceptance.
5. Finish the desktop dark/light visual/state pass, mockup reconciliation, accessibility, and focused test gaps. Mobile scope covers only the landing page.
6. Validate capacity/admission, resource limits, cold-start behavior, Neon boundaries/idle wake, backups, production observability, and the Hetzner migration.

Catalog uniqueness and concurrent-gather priority are backend follow-ups. Optional deployment reviewers, Joined-tab wording, and removed-item motion remain in TASKS.md.

Later work includes usage analytics after definition; account/legal views; personalized difficulty after sufficient data; safety-confidence tuning; gather timeout; curated evidence storage; and Flutter link-out. Genre enrichment and Topic-upload upgrades are dropped.
