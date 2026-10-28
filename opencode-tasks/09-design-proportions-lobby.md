# 09. The group lobby does not match its design proportions

Branch: `fix/lobby-design-proportions`. Frontend only. Requires task 08 (the lobby renders inside the
1288x900 `GameStage`).

## Get the design

```
python opencode-tasks/tools/extract_artboard.py GroupLobbyDark GroupLobbyEightPlayersDark GroupLobbyTwoPlayersDark GroupLobbyChatDark GroupLobbySettingsDark GroupLobbyTierPickerDark
```

This writes small HTML files into `.design-artboards/`, which git ignores. Each artboard is 1440x900 and includes both 76px rails, so the lobby content is
the 1288px between them. Read one artboard at a time; each is 13 to 24 KB.

## What is wrong

After story 53 the lobby's text is either too big or too small compared with the design, elements sit
too close together, and some are not aligned. The cause was per-element shrinking (task 08 removes it);
this task makes each element's size and spacing match the artboards exactly.

## What to build

Work section by section in `frontend/app/(app)/groups/[groupId]/page.tsx` and
`frontend/components/group-chat-overlay.tsx`, comparing with the artboard CSS:

1. Header: "Group Lobby" title size and position, the playlist chip (height, padding, font size).
2. Center: "Enter code to join" label, the join code (font, size, letter spacing), "Copy invite link".
3. Member avatars: size (the design uses 108px), ring, name label, crown, the orbit positions for 1 to 8
   members (compare `ORBIT_POSITIONS` with GroupLobbyEightPlayersDark and GroupLobbyTwoPlayersDark).
4. Footer: Start game, Chat, Settings, Leave lobby button sizes, gaps, and baseline alignment.
5. Chat panel and Settings panel: width, height, padding, gap above the footer (both 12px above it).
6. Playlist options popup: the compact row from GroupLobbyTierPickerDark.

Use Tailwind arbitrary values with the design's exact pixel numbers where Tailwind's scale does not
match. Keep the existing colors and tokens.

## Tests

- Playwright: screenshot the lobby with 2 and 8 members, and with chat and settings open, at 1440x900;
  put the screenshots and the matching artboard renders side by side in the PR description.
- Update existing lobby unit tests that assert class strings you changed.
