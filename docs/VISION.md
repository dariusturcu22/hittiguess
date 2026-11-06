# VISION.md: Product Vision

## What is this?

`hittiguess` is a Hitster-inspired multiplayer music game. Players hear a song, guess when it was released, and place it in chronological order on their personal timeline. The core loop: hear a song, guess the year, place the card, win or lose a token.

The library covers both widely recognized, mainstream music and obscure or niche tracks. Playlists can lean either way, or mix both, depending on who's playing.

## Who is it for?

Friend groups. The game is built around playing with people you know, not with strangers online. It works both at an in-person gathering and with friends who aren't in the same room.

## Open source, not a business

This isn't a product for sale, and there's no monetization plan, not now, not later. Built for friends and family, not to compete for users or ad revenue. Reaching the ~100-200 user scale of that group is a complete win, not a disappointing ceiling. The owner is also planning a public announcement, so the app is held to the standard of a public product (legal pages, security review, monitoring) while staying free, open source, and without ads or trackers.

No ads, no third-party trackers, no selling user data, ever. First-party analytics (story 34 in `PROJECT_STATE.md`) are fine and wanted, for improving the product for the people actually playing it.

Cost stays low deliberately. The whole deployment, every service combined, targets $0/month and tolerates up to roughly $20/month total if there is a genuinely good reason. If real usage outgrows that, the answer is to balance the game, cap concurrent sessions, or queue players, not to pay for more infrastructure.

## What does "done" look like?

A working multiplayer game where:

1. Someone creates a group and shares an invite link or a join code. Friends join live, chat and voice work immediately, and the admin can start as many game sessions as the group wants to play; once a session ends, nothing about it is kept except the final results.
2. Each round has a player whose turn it is, and a DJ responsible for playback. The DJ is sent to the real YouTube page or app to play the song, never an embedded player inside the game.
3. The active player guesses where the song fits on their timeline. Other players can bet a token on their own guess after the active player locks in.
4. Scoring works correctly, the DJ role can be fixed or rotating depending on group settings, and the game ends with a winner.
5. Playlists exist across genres, moods, and eras, and are trustworthy, meaning the years are correct.
6. Users can submit songs, either by pasting a link or searching by artist, title, or keyword. The pipeline finds good YouTube sources and verifies metadata automatically.
7. Players can voice chat and text chat with their group before, during, and between games, without relying on a separate app like Discord.
8. The UI is clean, fast, and works well on desktop; mobile support is a goal, not yet guaranteed to match the desktop experience.
9. Physical cards can be printed, cut out, and scanned to play a hybrid physical and digital game.

## What makes this different?

- Broad music coverage: mainstream and niche music are both first-class, not an afterthought.
- AI-assisted metadata pipeline: multi-source verification (YouTube, MusicBrainz, Discogs, Wikidata) synthesized by an LLM, with community reporting to catch errors.
- Online multiplayer that works: playback is fully compliant with YouTube's API policies by construction, and voice and text chat are built in for the group, no separate app needed.
- Community-maintained database: users can submit songs, report incorrect years, and add sources. The database improves through play.

## Built to a professional standard, not just a working one

Every external dependency uses official APIs and respects licenses. The product has privacy and terms drafts, GDPR account controls (settings, data export, deletion), and production observability. Publishing the legal pages, the remaining security audit items, and monitoring alerts are release tasks (story 56 in `PROJECT_STATE.md`) that the public launch waits on.

## Groups and game sessions

A group is the persistent space friends share; a game session is the round-by-round play inside it, ephemeral except for a results export. Everyone in the group is a full player, with no spectating and no joining a session in progress. See `GAME_DESIGN.md` for the rules and timers and `ARCHITECTURE.md` for the shape of both.
