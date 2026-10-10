# PROJECT_STATE.md: Story readiness

[TASKS.md](TASKS.md) owns unfinished requirements and tests. [ROADMAP.md](ROADMAP.md) sets order. Completed and dropped work is in [ARCHIVE.md](ARCHIVE.md); gaps in story IDs are expected.

**Ready** means tasks were checked against current code and feature work can start. **Needs Definition** requires a confirmed breakdown first. **Deferred** work waits for its stated dependency. Feature work on deferred stories must become Ready before implementation.

| ID | Remaining scope | Status |
| --- | --- | --- |
| 7 | Production hosting, cost/capacity controls, deployment acceptance, Hetzner fallback | Ready |
| 8 | Neon validation, idle wake, backups, catalog import decision | Ready |
| 24 | Concurrent-gather priority verification; optional gather timeout | Ready for verification; timeout deferred |
| 28 | Two-factor UI, desktop/state acceptance; account/legal pages later | Ready; account/legal scope deferred |
| 30 | Personalized difficulty training and monitoring | Deferred until enough real play data exists |
| 34 | Internal usage/abuse events, query surface, notice behavior | Needs Definition |
| 38 | Production observability configuration and delivery verification | Ready |
| 41 | Source-match confidence and uncertain-case review | Deferred until submission data supports tuning |
| 47 | Remaining mockup reconciliation during the visual pass | Ready |

The main game, catalog/import pipeline, participant history/statistics, prepared global difficulty, and automatic embedding indexing are implemented. Live deployment, media, and visual acceptance remain unfinished checks, not absent implementations.

Curated metadata evidence requires definition before storage. Flutter is deprioritized. Genre enrichment and automatic Topic-upload upgrades are dropped. Product/API behavior belongs in [GAME_DESIGN.md](GAME_DESIGN.md), [ARCHITECTURE.md](ARCHITECTURE.md), and [SYSTEM_REFERENCE.md](SYSTEM_REFERENCE.md); [DECISIONS.md](DECISIONS.md) preserves decision history.
