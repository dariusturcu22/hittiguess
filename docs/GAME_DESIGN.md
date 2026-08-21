# GAME_DESIGN.md — Game Rules and Mechanics

## Core concept

Players listen to a song and try to place it correctly on their personal chronological timeline. The player who completes their timeline first wins.

## Setup

- Each player starts with one card on their timeline, as a starting anchor.
- A playlist, or a combination of playlists, is selected for the session.
- Each group has a DJ setting: fixed, meaning one person stays DJ all game, or rotating, meaning the role passes each round. This is set when the session is created.

## Roles

The DJ and the active player, whoever's turn it is, are separate roles.

- DJ: responsible for playback only. Opens the real YouTube page or app to play the song. Does not guess and does not earn tokens.
- Active player: the player whose turn it is. Listens to the song and places their guess on their own timeline.

## Each round

1. The DJ plays the song, on the real YouTube page for remote sessions, or the real YouTube app for in-person sessions.
2. The active player places a guess: before, after, or between the cards already on their timeline. The guess is locked in.
3. After the active player locks in, other players holding a token may place a bet on their own guess, first come, first served.
4. The song is revealed: artist, title, and year.
5. Scoring:
   - If the active player's placement is correct, they keep the card. This holds even when the new song shares a release year with an existing card on the timeline; either order counts as correct.
   - If a player bet a token and the active player's placement was correct, the active player keeps the card regardless of the bet, and the bet is lost.
   - If the active player's placement was wrong and a bet was correct, the card goes to whoever bet correctly instead.
   - If the active player's placement was wrong and no one bet, the card is discarded.
6. Next round: the active player role passes to the next player. The DJ role stays fixed or rotates, per the group setting.

## Winning

The first player to correctly build a timeline of the required length wins. Timeline length can scale with player count.

## Online play

- The DJ opens the real YouTube page, a new browser tab, for remote sessions, or the real YouTube app for in-person sessions. Never an embedded player inside the game.
- For remote sessions, that browser tab is captured through WebRTC and streamed to the other players.
- Non-DJ players never see a YouTube embed or the YouTube app, only the game UI.
- Reveal is a manual trigger: any player can reveal once the song has played.
- Players can voice chat and text chat with the rest of their session. See [ARCHITECTURE.md](ARCHITECTURE.md) for how this works.

## Ads

Ads play unmodified, exactly as YouTube serves them. The guessing timer starts once the DJ signals the actual song has begun, not when playback starts.

## Song source quality

The system prefers official "Topic" channel uploads on YouTube when available, and suggests an upgrade to the user if a better source is found. Enforcing this consistently across submissions is still an open design problem.

## Data quality

An incorrect year on a card breaks the game for everyone at the table. Players can report a song they believe has the wrong year, along with a message, the year they believe is correct, and one or more sources. What causes a reported or newly submitted song to become fully trusted is still undecided. Admin-seeded songs are trusted immediately and skip this process entirely.

## Planned game modes

- Decade Challenge: songs only from a specific decade.
- Genre Round: songs tagged with a specific genre.
- Underground Mode: only songs below a certain mainstream threshold.
- Speed Round: shorter clip, faster guessing timer.
