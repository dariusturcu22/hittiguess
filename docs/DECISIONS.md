# DECISIONS.md — Decision Log

Append-only. Never edit or delete past entries. New decisions go at the bottom.

---

## 2026-06 | DJ model for YouTube compliance

Decision: use a DJ role as the architecture for YouTube compliance in online multiplayer. The DJ's device runs playback; other players receive only audio and see only the game UI.

Why: YouTube's IFrame API terms require an embedded player to be visible, unmodified, and not overlaid. In a multiplayer game where everyone has a player, this can't be enforced without breaking gameplay. A DJ model sidesteps this: only one device has any relationship to the player, and that player doesn't guess that round anyway.

Note, 2026-07: the mechanism described here was superseded, see the 2026-07 entry below. The DJ-per-round principle and zero embed on non-DJ devices still stand.

---

## 2026-06 | No unofficial APIs

Decision: use only official APIs for external services. No unofficial or reverse-engineered clients.

Why: unofficial libraries aren't authorized by the underlying service and carry meaningfully higher terms-of-service risk than the official API for the same functionality.

---

## 2026-06 | Multi-source metadata pipeline

Decision: verify song metadata using multiple external sources (YouTube, MusicBrainz, Wikipedia, Genius, Discogs), synthesized by an LLM, rather than relying on a single AI model's training data.

Why: LLM training data is unreliable for niche and underground music. Fetching structured data from specialist databases first, then using the LLM only for synthesis and reconciliation, is more accurate. MusicBrainz and Discogs cover underground and electronic music that mainstream sources miss.

---

## 2026-06 | Separate submittedYear vs verifiedYear

Decision: store `submittedYear` and `verifiedYear` as separate fields, not a single `releaseYear`.

Why: without this distinction, there's no way to tell whether a year was changed after review, which the verification workflow depends on.

---

## 2026-06 | Persist metadata pipeline output

Decision: store the full pipeline output as a JSON column, and persist the confidence value.

Why: without this, provenance is lost once the frontend consumes the response. Auditing and re-verification become impossible.

---

## 2026-06 | pgvector for deduplication before the pipeline

Decision: check pgvector similarity against existing verified songs before running the full pipeline. Reuse on a high-confidence match, skipping the LLM call.

Why: reduces cost and latency for songs that are already in the database.

---

## 2026-06 | Flutter deprioritized

Decision: the Flutter app is deprioritized in favor of the web-based game.

Why: playback now happens through a browser tab or the real YouTube app, which works on mobile browsers too, removing the original reason for a dedicated native playback app.

---

## 2026-07 | DJ model refined: link out to the real YouTube instead of embedding

Decision: stop embedding the YouTube player entirely. The DJ is sent to the real YouTube page, a new browser tab, for remote sessions, or the real YouTube app for in-person sessions. Physical cards encode the YouTube URL directly in the QR code.

Why: an earlier prototype hid the iframe and blocked ads, a direct violation of YouTube's developer policies. This exposure doesn't shrink just because the audience stays small or grows without active marketing. It depends on what the software does, not on audience size. The fix is architectural: a plain outbound link to YouTube's own page or app is not an embedded player at all, so the rules governing embedded players don't apply. This holds regardless of user count.

Trade-off accepted: no programmatic access to the playback state on a page we don't control, so round reveal is a manual trigger instead of automatic. Ads always play, unmodified.

Supersedes: the 2026-06 DJ model entry, mechanism only. The underlying DJ-per-round principle is unchanged.

---

## 2026-07 | Split the backend: Spring Boot core, Python/FastAPI AI microservice

Decision: split the backend into two services. Spring Boot keeps auth, CRUD, game session, and WebSocket. A Python/FastAPI service takes over the metadata pipeline, LLM synthesis, and embeddings. Spring AI is removed from the Java side.

Why: Python has a stronger, faster-moving ecosystem for LLM and embeddings work than the Java equivalent. The metadata pipeline was already a distinct component; this gives it its own process and dependency footprint, separate from the core app's CRUD and auth concerns.

Why not a full rewrite to Python instead: the existing Spring Boot auth and CRUD layer already works. Rewriting it would discard working infrastructure for no functional gain.

Implementation notes: the core service calls the AI microservice over an internal endpoint, not publicly exposed. The core service owns all migrations; the AI microservice never alters schema. Both services run in the same Azure Container Apps environment.

---

## 2026-07 | Move backend hosting from Fly.io to Azure Container Apps

Decision: migrate both backend services to Azure Container Apps. Database moves to Azure Database for PostgreSQL Flexible Server, pgvector enabled. Frontend stays on Vercel.

Why: Fly.io kept compute running regardless of actual traffic, which doesn't fit a usage pattern that's bursty and mostly idle. Azure Container Apps' Consumption plan scales to zero and charges nothing while idle. AWS Fargate and App Runner both maintain a non-zero baseline cost even at low traffic, reproducing the same problem this migration is meant to fix.

Open item: current Postgres host needs confirming before data migration. See PROJECT_STATE.md.

---

## 2026-08 | Community verification through reports, not thumbs up or down

Decision: replace the earlier thumbs-up/thumbs-down concept with a report flow: a report button, a free-text message, a field for the year the reporter believes is correct, and separate fields for one or more sources.

Why: a simple up or down vote trusts the crowd without capturing any actual information behind the disagreement. Separate structured fields, year and sources, give an admin or a future automated process something concrete to act on, instead of an unexplained vote count.

Open item: what promotes a reported or newly submitted song to fully verified is not yet decided.

---

## 2026-08 | Voice chat: mesh peer-to-peer, no media server, Cloudflare TURN as fallback

Decision: voice chat between players in a session uses a mesh WebRTC topology, signaling over the existing WebSocket layer, capped at 8 participants per session. Cloudflare's pay-as-you-go TURN service is used only as a fallback for connections that can't be established directly. Video is out of scope for now.

Why: a full media server removes the participant limit and enables group video, but requires either self-hosting a real-time media server or paying a per-participant-minute provider, both ruled out. At realistic group sizes for this game, mesh audio-only is well within what peer-to-peer can handle reliably; video mesh is not, since video bitrate is far higher than audio and mesh bandwidth scales with each additional participant. A relay, TURN, is still required regardless of group size, since a meaningful share of real-world network connections can't establish a direct path due to NAT type. At this usage scale, the actual relay cost is negligible, well under the cost of any managed media server or self-hosted alternative.

Why not self-hosting a TURN server instead: self-hosting only becomes cheaper than a pay-as-you-go relay at usage volumes far beyond this project's expected scale. Below that threshold, a managed relay is both cheaper and less operational work.

---

## 2026-08 | Round mechanic: active player has priority on valid placements

Decision: the player whose turn it is places their guess and locks it in first. Other players holding a token may then bet on their own guess, first come, first served. If the active player's placement is correct, including when it shares a release year with an existing card on the timeline, they keep the card regardless of any bet, and the bet is lost.

Why: this matches the official Hitster ruling for the equivalent situation. A same-year tie doesn't override a correct placement, and a bettor only wins the card if the active player was actually wrong.

---

## 2026-08 | Sessions are ephemeral, not persistent groups

Decision: a game session is created with an invite link and exists only while it's being played, the same way a Gartic Phone round works. Everyone who joins is a full player. There's no spectating and no joining a session already in progress. Voice and text chat are scoped to the session's lifetime. When the session ends, nothing persists except a downloadable results export.

Why: an earlier idea involved persistent groups, similar to Discord servers, that would stick around between games. Given the actual usage pattern, friends starting a game together and playing it through, that persistence adds storage and complexity without a clear benefit. The simpler model also removes an entire category of open questions about group membership, moderation, and long-term data retention.

---

## 2026-08 | Project state as a story backlog, with a task gate

Decision: `PROJECT_STATE.md` holds a table of stories with stable IDs and a status: Implemented, Ready, In Progress, or Needs Definition. `TASKS.md` holds concrete, checkable tasks. A story can have draft tasks written against it while still marked Needs Definition; that alone doesn't unlock feature work. A story becomes Ready only once its tasks have been checked against the real codebase and confirmed accurate. Feature work requires a Ready story with tasks; fix, chore, and docs work, including bugs listed directly in TASKS.md, doesn't need a story at all. Fully implemented stories move out of both files into `ARCHIVE.md`.

Why: without a defined-before-built gate, an AI coding agent will happily start implementing a half-formed idea. Separating "a task exists" from "a task is confirmed against real code" matters specifically because task breakdowns written during planning, without access to the actual repository, can turn out to be wrong once the real code is visible. Archiving completed stories keeps the active files from growing indefinitely.

---

## 2026-08 | Branching: main, dev, legacy

Decision: three persistent branches. `legacy` is frozen at the current implementation and stays live in production while the new architecture is built. `dev` is the active integration branch, worked on exclusively through pull requests from `feature/*`, `fix/*`, `chore/*`, and `docs/*` branches. `main` is not touched until the new architecture is ready to replace what `legacy` is currently serving.

Why: this keeps the current, working deployment available to actual players throughout the rework, rather than breaking it mid-refactor.

---

## 2026-08 | Core-to-AI-microservice auth: shared secret header

Decision: the core service authenticates to the AI microservice's internal endpoint with a shared secret header, `X-Internal-Api-Key`, checked against `INTERNAL_SERVICE_API_KEY` on both sides.

Why: the original split decision left the mechanism open, shared secret header or network-level restriction. A header works identically in local dev and in Azure Container Apps, with no dependency on Container Apps-specific network configuration, and needs no extra infrastructure to set up.
