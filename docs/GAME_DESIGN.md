# GAME_DESIGN.md: Game Rules and Mechanics

## Core concept

Players listen to a song and try to place it correctly on their personal chronological timeline. The player who completes their timeline first wins.

## Groups

Anyone can create a group; whoever creates one becomes its admin. A group is shared over an invite link or a 4-letter join code, both work at any time, capped at 8 members, and a player can only belong to one group at a time. The admin is visually marked, a crown icon, and can voluntarily promote another member to admin at any time, not just when leaving.

On joining, a player is prompted to set a per-group display name and avatar, defaulting to their account's own, but editable and private to that group: other members only ever see the per-group identity, never the player's real profile. There's no friends concept.

Chat and voice are open from the moment the group exists, voice is a standing room anyone can join or leave freely, not a call someone starts. Only the admin can configure the game settings, playlist(s), DJ mode, win-condition card count, everyone else sees them update live but can't change them.

Only the admin can start a game session. Once started, the group is locked to new members. A group can play more than one game session over its lifetime, the admin can start another once one finishes.

A group doesn't last forever: it's deleted if the admin doesn't start a game session within 30 minutes of creating it, or within 30 minutes of the previous one ending. If the admin leaves the group outright, another member takes over as admin; if no one's left, the group is deleted. Closing the app doesn't remove anyone, only leaving on purpose does, reopening the app points a player back to their still-active group if they have one.

## Setup

- Each player starts with one card on their timeline, as a starting anchor.
- A playlist, or a combination of playlists, is selected by the group's admin.
- Each group has a DJ setting: fixed, meaning one person stays DJ all game, or rotating, meaning the role passes each round. Set by the admin before the game session starts, changeable anytime up to then.
- The admin sets how many cards a player needs to win: minimum 5 always, maximum 20 for a 2-3 player group, maximum 15 for a 4-8 player group.

## Roles

The DJ and the active player, whoever's turn it is, are separate roles.

- DJ: controls playback and the round's flow. Opens the real YouTube page or app to play the song, and can pause, play, close the YouTube tab or app, end the current turn, and trigger the reveal. General players hold none of these controls. Does not guess and does not earn tokens.
- Active player: the player whose turn it is. Listens to the song and places their guess on their own timeline.

## Each round

1. The DJ plays the song, on the real YouTube page for remote sessions, or the real YouTube app for in-person sessions.
2. The active player places a guess: before, after, or between the cards already on their timeline. The guess is locked in, with a sound effect on lock-in.
3. The active player's audio stream cuts off immediately on lock-in, regardless of what's still playing on the DJ's end.
4. A 3-5 second countdown follows lock-in, giving other players a moment to get ready to bet.
5. A 15-second betting window opens: any player holding a token may bet on the active player's guess being wrong, first come, first served, one bet per round. If no player holds a token, this window is skipped entirely. A skip-betting button lets the group end the window early if no one wants to bet. Placing a bet is concurrency-safe: only the first successful bet is accepted, and a losing attempt doesn't cost the player their token.
6. Once the betting window closes, the DJ reveals the song: artist, title, and year. Reveal is the DJ's action alone, not any player's.
7. Scoring:
   - If the active player's placement is correct, they keep the card. This holds even when the new song shares a release year with an existing card on the timeline; either order counts as correct.
   - If a player bet a token and the active player's placement was correct, the active player keeps the card regardless of the bet, and the bet is lost.
   - If the active player's placement was wrong and a bet was correct, the card goes to whoever bet correctly instead.
   - If the active player's placement was wrong and no one bet, the card is discarded.
8. Next round: the active player role passes to the next player. The DJ role stays fixed or rotates, per the group setting.

## Earning tokens

At any point during their turn, independent of their timeline placement, the active player can submit a guess for the song's artist and title in a box available for the whole turn. A fully correct guess, both artist and title, earns a token, spendable on a future round's bet. Typo tolerance is decided: normalize both the guess and the canonical answer (lowercase, strip punctuation, strip diacritics, collapse whitespace) and compare with Damerau-Levenshtein edit distance, a flat budget of 1 regardless of title length, see [DECISIONS.md](DECISIONS.md).

## Winning

The first player to correctly build a timeline of the required length wins. Required length is the win-condition card count the admin set before starting, see Setup.

## Reconnecting and leaving

A player disconnecting, closed tab, network drop, is marked disconnected but stays in the session exactly as they were: timeline, tokens, and turn order unchanged. Reopening the app prompts them to rejoin their active session, the same pattern as rejoining an active group.

If the active player is disconnected when their turn comes, or disconnects mid-turn, the game doesn't wait for them indefinitely: after 90 seconds without reconnecting, their turn is auto-skipped and they're marked `Left`, the same status an explicit leave produces. A player marked `Left` stays visible in the game with a distinct marker, is excluded from future turns and DJ rotation, but the cards already on their timeline still count toward the final results.

## Playing while away

A player can use the rest of the app while a group or game session is active, creating a playlist or editing a song, without leaving the session. The session minimizes to a small persistent widget rather than locking the player into the game screen. If it's the player's turn while they're away from the game screen, a sound plays and a visual banner appears, clicking either returns them to the game.

## Interaction and animation

Timeline placement is drag-and-drop: dragging a card between two existing cards animates the gap opening to make room, with no overlap, and the layout animates back into place once the card is placed. The artist/title guess box gives immediate animated feedback on submission, a correct guess animates a token dropping into the player's token count, an incorrect guess animates distinctly from a correct one.

Voice chat renders as a persistent, collapsible right-hand sidebar: vertically stacked circular avatars with names underneath, a speaking indicator ring, and mute/deafen icon overlays when applicable. A join-call button appears as a trailing circle in the same list. A player leaving animates out, the remaining avatars animate into the gap. The sidebar stays available during the minimized "playing while away" state described above.

Text chat renders as a semi-transparent overlay in the bottom-left corner, toggled by a keybind or a clickable button rather than requiring a persistent input field, plain username-and-message lines, no threading.

## Online play

- The DJ opens the real YouTube page, a new browser tab, for remote sessions, or the real YouTube app for in-person sessions. Never an embedded player inside the game.
- For remote sessions, that browser tab is captured through WebRTC and streamed to the other players.
- Non-DJ players never see a YouTube embed or the YouTube app, only the game UI.
- Reveal is a manual trigger, and it belongs to the DJ alone: no general player can reveal, matching the DJ's other flow controls (pause, play, close YouTube, end turn) under Roles above.
- Screen or system audio sharing for a remote session's WebRTC capture only starts when the DJ clicks "Open YouTube Link," paired with an explicit UI warning that doing so broadcasts their tab or system audio to the rest of the group.
- Players can voice chat and text chat with the rest of their group, available from group creation, not just during a game session. See [ARCHITECTURE.md](ARCHITECTURE.md) for how this works.

## Ads

Ads play unmodified, exactly as YouTube serves them. The guessing timer starts once the DJ signals the actual song has begun, not when playback starts.

## Song source quality

The system prefers official "Topic" channel uploads on YouTube when available, and suggests an upgrade to the user if a better source is found. Enforcing this consistently across submissions is still an open design problem.

## Data quality

An incorrect year on a card breaks the game for everyone at the table. Players can report a song they believe has the wrong year, along with a message, the year they believe is correct, and one or more sources. What causes a reported or newly submitted song to become fully trusted is decided: exact agreement among MusicBrainz, Discogs, and Wikidata locks the year with no LLM involvement; anything short of that goes through Wikipedia extraction and reconciliation instead, landing at `NEEDS_REVIEW`, not automatically verified (see [DECISIONS.md](DECISIONS.md)). Admin-seeded songs are trusted immediately and skip this process entirely.

## Planned game modes

- Decade Challenge: songs only from a specific decade.
- Genre Round: songs tagged with a specific genre.
- Underground Mode: only songs below a certain mainstream threshold.
- Speed Round: shorter clip, faster guessing timer.
