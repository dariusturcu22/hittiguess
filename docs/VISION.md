# VISION.md: Product Vision

## What is this?

`hittiguess` is a Hitster-inspired multiplayer music game. Players hear a song, guess when it was released, and place it in chronological order on their personal timeline. The core loop: hear a song, guess the year, place the card, win or lose a token.

The library covers both widely recognized, mainstream music and obscure or niche tracks. Playlists can lean either way, or mix both, depending on who's playing.

## Who is it for?

Friend groups. The game is built around playing with people you know, not with strangers online. It works both at an in-person gathering and with friends who aren't in the same room.

## Open source, not a business

This isn't a product for sale, and there's no monetization plan, not now, not later. Built for friends and family, not to compete for users or ad revenue. Reaching the ~100-200 user scale of that group is a complete win, not a disappointing ceiling.

No ads, no third-party trackers, no selling user data, ever. First-party analytics (see stories 33 and 34 in `PROJECT_STATE.md`) are fine and wanted, they're for improving the product for the people actually playing it, not for anyone else's benefit.

Cost stays low deliberately, not as an afterthought. The whole deployment, every service combined, targets $0/month and tolerates up to roughly $20/month total if there's a genuinely good reason (a real skills investment, not convenience). That ceiling is shared across every service, backend, AI microservice, frontend, database, observability, everything, not $20 per piece. If real usage ever outgrows what free or near-free infrastructure can handle, the answer is to balance the game, cap concurrent sessions, queue players, something that keeps it playable within the budget, not to pay for more infrastructure to keep up with growth.

## What does "done" look like?

A working multiplayer game where:

1. Someone creates a group and shares an invite link or a join code. Friends join live, chat and voice work immediately, and the admin can start as many game sessions as the group wants to play; once a session ends, nothing about it is kept except the final results.
2. Each round has a player whose turn it is, and a DJ responsible for playback. The DJ is sent to the real YouTube page or app to play the song, never an embedded player inside the game.
3. The active player guesses where the song fits on their timeline. Other players can bet a token on their own guess after the active player locks in.
4. Scoring works correctly, the DJ role can be fixed or rotating depending on group settings, and the game ends with a winner.
5. Playlists exist across genres, moods, and eras, and are trustworthy, meaning the years are correct.
6. Users can submit songs, either by pasting a link or searching by artist, title, or keyword. The pipeline finds good YouTube sources and verifies metadata automatically.
7. Players can voice chat and text chat with their session directly in the game, for as long as the session lasts, without relying on a separate app like Discord.
8. The UI is clean, fast, and works well on desktop; mobile support is a goal, not yet guaranteed to match the desktop experience.
9. Physical cards can be printed, cut out, and scanned to play a hybrid physical and digital game.

## What makes this different?

- Broad music coverage: mainstream and niche music are both first-class, not an afterthought.
- AI-assisted metadata pipeline: multi-source verification (YouTube, MusicBrainz, Discogs, Wikidata) synthesized by an LLM, with community reporting to catch errors.
- Online multiplayer that works: playback is fully compliant with YouTube's API policies by construction, and voice and text chat are built in for the session, no separate app needed.
- Community-maintained database: users can submit songs, report incorrect years, and add sources. The database improves through play.

## Built to a professional standard, not just a working one

Every external dependency, official APIs only, licenses respected, no reverse-engineered or unofficial access. That standard extends to the product itself: a real privacy policy and terms of service, GDPR compliance, and production-grade observability (error tracking, monitoring) rather than a game that happens to run. None of this is scoped into stories yet; it's a standard the project is held to as stories get defined, not a checklist bolted on at the end.

## Groups are the lobby, game sessions are the round

A group is the persistent space friends share: created with an invite link or a join code, capped at 8 members, chat and voice live from the moment it's created. The admin configures game settings; a group can run any number of game sessions over its lifetime without anyone needing a new invite. A group that sits idle too long, or finishes a game session without starting another, gets deleted, see `GAME_DESIGN.md` for the exact timers.

A game session is the round-by-round play itself, created only when the group's admin starts one. It's fully ephemeral: rounds, guesses, and bets are purged once it ends, except for a downloadable results export. Everyone in the group is a full player, there's no spectating and no joining a session already in progress. See `ARCHITECTURE.md` and `GAME_DESIGN.md` for the full shape of both.
