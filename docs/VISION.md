# VISION.md: Product Vision

## What is this?

`hitguessr` is a Hitster-inspired multiplayer music game. Players hear a song, guess when it was released, and place it in chronological order on their personal timeline. The core loop: hear a song, guess the year, place the card, win or lose a token.

The library covers both widely recognized, mainstream music and obscure or niche tracks. Playlists can lean either way, or mix both, depending on who's playing.

## Who is it for?

Friend groups. The game is built around playing with people you know, not with strangers online. It works both at an in-person gathering and with friends who aren't in the same room.

## What does "done" look like?

A working multiplayer game where:

1. Someone starts a session and shares an invite link. Friends join through that link, similar to how a Gartic Phone round works: everyone joins live, plays together, and once the session ends, nothing about it is kept, except the final results.
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

## Sessions are temporary

A session exists only while it's being played. There's no persistent server or group to maintain between games, no message history to store, nothing carried over from one session to the next. Everyone who joins is a full player. There's no spectating and no joining an already-started session. When the session ends, players can download the results, the leaderboard, but the session itself, including all chat, disappears.
