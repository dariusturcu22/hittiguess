# GAME_DESIGN.md: Game Rules and Mechanics

## Core concept

Players listen to a song and try to place it correctly on their personal chronological timeline. The game ends at the end of the round in which someone completes their timeline, and every player tied on the most cards wins.

## Groups

Anyone can create a group; whoever creates one becomes its admin. A group is shared over an invite link or a 4-letter join code, both work at any time, capped at 8 members, and a player can only belong to one group at a time. The admin is visually marked, a crown icon, and can voluntarily promote another member to admin at any time, not just when leaving.

On joining, a player is prompted to set a per-group display name and avatar, defaulting to their account's own, but editable and private to that group: other members only ever see the per-group identity, never the player's real profile. There's no friends concept.

Chat and voice are open from the moment the group exists, voice is a standing room anyone can join or leave freely, not a call someone starts. Only the admin can configure the game settings, playlist(s), DJ mode, win-condition card count, everyone else sees them update live but can't change them.

Only the admin can start a game session. Once started, the group is locked to new members. A group can play more than one game session over its lifetime, the admin can start another once one finishes.

A group doesn't last forever: it's deleted if the admin doesn't start a game session within 30 minutes of creating it, or within 30 minutes of the previous one ending. If the admin leaves the group outright, another member takes over as admin; if no one's left, the group is deleted. Closing the app doesn't remove anyone, only leaving on purpose does, reopening the app points a player back to their still-active group if they have one.

## Setup

- Each player starts with one card on their timeline, as a starting anchor, and two tokens.
- A playlist, or a combination of playlists, is selected by the group's admin.
- Each group has a DJ setting: fixed, meaning one person stays DJ all game, or rotating, meaning the role passes each round. Set by the admin before the game session starts, changeable anytime up to then.
- The admin sets how many cards a player needs to win: minimum 5 always, maximum 20 for a 2-3 player group, maximum 15 for a 4-8 player group.

## Roles

The DJ and the active player, whoever's turn it is, are separate roles.

- DJ: opens the real YouTube page or app and plays the song there. Holds no in-app controls beyond the "Open YouTube Link" action; playback, pausing, and closing the tab happen on YouTube itself. The round flow starts when the active player locks a placement, with no separate DJ trigger. The DJ does not guess or earn tokens.
- Active player: the player whose turn it is. Listens to the song and places their guess on their own timeline.

## Each turn

A round is one full pass through the players: every player in the active-player rotation takes one turn, and the round number only advances once the rotation comes back around. Each turn runs as follows.


1. The DJ plays the song, on the real YouTube page for remote sessions, or the real YouTube app for in-person sessions.
2. The active player places a guess: before, after, or between the cards already on their timeline. The guess is locked in, with a sound effect on lock-in.
3. The active player's remote-audio stream cuts off on lock-in, regardless of what is still playing on the DJ's end.
4. A 3-5 second countdown follows lock-in, giving other players a moment to get ready to bet.
5. A 15-second betting window opens: every player holding a token may bet, at the same time as each other, on a different gap in the ACTIVE PLAYER's own timeline than the one the active player just locked their own guess into, one bet per interval rather than one bet per round. A round can end up with zero, one, or several accepted bets, each at a distinct gap, since two bettors can never occupy the same gap and a bettor can never occupy the active player's own gap. A player still can't bet twice in the same round. If no player holds a token, this window is skipped entirely. A skip-betting button lets the group end the window early if no one wants to bet. Placing a bet is concurrency-safe: for any given gap, only the first successful bet on it is accepted, and a losing attempt doesn't cost the player their token.
6. Once the betting window closes, the song reveals automatically: artist, title, and year. Nobody triggers it manually, DJ included.
7. Scoring:
   - If the active player's placement is correct, they keep the card. This holds even when the new song shares a release year with an existing card on the timeline; either order counts as correct. Every accepted bet is lost regardless.
   - If the active player's placement was wrong, the gap that's objectively correct for the revealed release year is checked against every accepted bet on the active player's timeline. Since bets can never share a gap, at most one bettor can be sitting on the correct one.
   - If a bettor is sitting on the correct gap, they win the card, not the active player. It's inserted into that winning bettor's OWN timeline, computed automatically at wherever it objectively belongs there, not at the gap they bet on. Every other bettor loses their spent token.
   - If the active player's placement was wrong and no bet sits on the correct gap, whether because no one bet there or no one bet at all, the card is discarded. A winning bettor only ever receives the card, never a bonus token.
8. Next turn: once scoring resolves, the active player role passes to the next player automatically, no manual step from anyone. The DJ role stays fixed or rotates, per the group setting.

## Earning tokens

While the card is being placed, independent of their timeline placement, the active player can guess the song's title and artists in two fields that stay on screen next to the lock-in button. The title gets one guess. Artists are guessed one at a time: a correct artist locks in and the player may try another credited artist, a wrong artist ends artist guessing for that turn, and an artist already guessed can't be guessed again. The title plus at least one artist earns a token, at most one per turn, spendable on a future bet. Other players (not the DJ) can guess under the same rules for the leaderboards, without earning tokens. Typo tolerance is decided: normalize both the guess and the canonical answer (lowercase, strip punctuation, strip diacritics, collapse whitespace) and compare with Damerau-Levenshtein edit distance, a flat budget of 1 regardless of title length, see [DECISIONS.md](DECISIONS.md).

## Winning

Reaching the required timeline length doesn't end the game on the spot: the current round plays out so every player still due a turn in it gets one, and the game ends at the end of that round. Every player with the most cards at that point wins, sharing first place. Required length is the win-condition card count the admin set before starting, see Setup. The results rank players by cards, artists guessed, and titles guessed, with equal values sharing a place.

## Reconnecting and leaving

A player disconnecting, closed tab, network drop, is marked disconnected but stays in the session exactly as they were: timeline, tokens, and turn order unchanged. Reopening the app prompts them to rejoin their active session, the same pattern as rejoining an active group.

If the active player is disconnected when their turn comes, or disconnects mid-turn, the game doesn't wait for them indefinitely: after 90 seconds without reconnecting, their turn is auto-skipped and they're marked `Left`, the same status an explicit leave produces. A player marked `Left` stays visible in the game with a distinct marker, is excluded from future turns and DJ rotation, but the cards already on their timeline still count toward the final results.

## Playing while away

A player can use the rest of the app while a group or game session is active, creating a playlist or editing a song, without leaving the session. The session minimizes to a small persistent widget rather than locking the player into the game screen. The widget shows the active player, round number, and the player's own token count. A countdown appears only when the current phase has a server deadline. If it is the player's turn while they use another app page, a sound plays once for that turn and a clickable banner returns them to the game. Reconnecting does not repeat the alert. Browser sound requires a prior keyboard or pointer interaction.

## Interaction and animation

Timeline placement is drag-and-drop: dragging a card between two existing cards animates the gap opening to make room, with no overlap, and the layout animates back into place once the card is placed. The artist/title guess box gives immediate animated feedback on submission, a newly awarded token animates dropping into the active player's token pile. A correct partial guess or a spectator's correct guess has success feedback without a token animation. An incorrect guess has distinct feedback. Reduced-motion preferences disable the drop and shake animations.

Voice chat renders as a right-hand sidebar the same width as the left one, shown on the group lobby page and, once a player is in the call, everywhere until they leave it: vertically stacked circular avatars with names underneath, a speaking indicator ring, and mute/deafen icon overlays when applicable. The rail is 76px wide, has no collapse control, and shows the join-call button at the top when the player is outside the call. Avatars use each member's per-group picture, with an initial fallback. Speaking rings follow microphone activity, excluding shared song audio. Mute and deafen state synchronize over the existing voice peer connections and reset when a member leaves. A player leaving animates out, the remaining avatars animate into the gap. The sidebar stays available during the minimized "playing while away" state described above.

Text chat renders as a semi-transparent overlay in the bottom-left corner, toggled by a keybind or a clickable button rather than requiring a persistent input field, plain username-and-message lines, no threading.

## Online play

- The DJ opens the real YouTube page, a new browser tab, for remote sessions, or the real YouTube app for in-person sessions. Never an embedded player inside the game.
- For remote sessions, that browser tab is captured through WebRTC and streamed to the other players.
- Non-DJ players never see a YouTube embed or the YouTube app, only the game UI.
- Reveal happens automatically once the betting window closes; nobody, DJ included, triggers it by hand.
- Screen or system audio sharing for a remote session's WebRTC capture starts from the DJ's "Open YouTube Link" click itself, with a warning beforehand that tab or system audio will be broadcast. The same click opens YouTube in its own window so the capture picker stays visible in the game window.
- Players can voice chat and text chat with the rest of their group, available from group creation, not just during a game session. See [ARCHITECTURE.md](ARCHITECTURE.md) for how this works.

## Ads

Ads play unmodified, exactly as YouTube serves them, and the DJ has no in-app way to signal when they end: the active player listens and places their guess whenever they're ready, so the round's own timers, the betting countdown and window, start from lock-in rather than from playback starting.

## Song source quality

Playback uses the song's stored primary YouTube upload. Alternate uploads of the same recording map to the same catalog song. Automatic Topic-upload searches and upgrade suggestions are dropped.

## Data quality

An incorrect year on a card breaks the game for everyone at the table. Players can report a song they believe has the wrong year, along with a message, the year they believe is correct, and one or more sources. Exact agreement among MusicBrainz, Discogs, and Wikidata locks the year without Wikipedia extraction or year reconciliation. A combined LLM precheck has already identified and classified the submission. Wikipedia can also corroborate a verified year when at least three source years match or all available years, at least three, span at most one year. Otherwise answers land at `NEEDS_REVIEW`, or `MANUAL_ENTRY` when no source has an answer. Admin-seeded songs follow the same rules and never skip verification because of who submitted them.

A genuinely new fully verified song enters the shared catalog as soon as the pipeline finishes, including when a user only requests its details and never clicks Add. Playlist membership requires a separate explicit action. Unverified previews wait for submission; background imports and admin processing retain their own persistence rules. A recognized alternate upload reuses the existing song instead of creating another catalog entry.

## Future game mode ideas, not currently scoped

None of these have a story in `PROJECT_STATE.md`. They remain ideas rather than planned work. Genre enrichment and genre game modes are dropped, see [DECISIONS.md](DECISIONS.md).

- Decade Challenge: songs only from a specific decade.
- Underground Mode: only songs below a certain mainstream threshold.
- Speed Round: shorter clip, faster guessing timer.


## Group permissions and identifiers

Chat and voice belong to the group and remain available between games. Each player has a personal timeline. The admin can remove another member while the group is open; removal prevents that account from rejoining the same group and closes its sockets after commit. Admin transfer is separate from removal. Group join codes have four uppercase letters; playlist invite codes have eight uppercase letters, with older stored formats possible.

A skip-betting vote is available only to eligible bettors. The betting window closes early once every eligible bettor has bet or skipped; an arbitrary member cannot end it alone. Shared-song edits require an editable status, UNVERIFIED or MANUAL_ENTRY, and write access to every playlist containing that song.

## Session recovery and retained history

A connected active player has three minutes to lock a placement. An expired placement deadline skips that turn without marking the connected player as departed. The four-second countdown follows lock-in, betting lasts fifteen seconds, and the revealed card remains for six seconds before the next turn. Disconnected active players still use the separate ninety-second departure rule.

An exhausted song queue ends the game with the current standings, even when nobody reached the target. Equal top card counts share the win. After a backend restart, active sessions retain their cards, tokens, and turn state. Players begin disconnected until their sockets return, and timers resume from stored deadlines. Ten minutes with no connected players abandons the session.

Normal completion and abandonment save compact participant-only history before temporary session rows are removed. Interrupted games appear in history but never count as competitive wins. History survives group expiry and departure; later group members cannot read earlier games. The latest normal result per group also remains available for export and is replaced by that group's next normal completion.

Difficulty Confirm saves Easy, Medium, or Hard only. Start selects a hidden pool of connected players multiplied by the target card count multiplied by three. Selection uses prepared global scores in core and requires VERIFIED songs with at least five Wikidata sitelinks. The pool is shuffled and is not added as a playlist. An insufficient tier pool produces an error. Custom uses the selected playlists or pasted playlist source.
