# 05. "Start session" on a playlist only works if you are already in a group

Branch: `fix/start-session-from-playlist`. Frontend only. Files:
`frontend/app/(app)/playlists/[playlistId]/PlaylistContent.tsx` (handler near line 267, button near
line 334), `frontend/components/app-sidebar.tsx` (has the existing create-group flow around line 141,
`useCreateGroup`), and the lobby page `frontend/app/(app)/groups/[groupId]/page.tsx` (it already
accepts `?playlist=<id>` and opens the custom picker with that playlist preselected).

## What is wrong

```ts
const handleStartSession = () => {
  const groupId = activeMembershipQuery.data?.id;
  if (!groupId) { toast.error("Join or create a group to start a session."); return; }
  router.push(`/groups/${groupId}?playlist=${playlistId}`);
};
```

Not in a group: it only shows a toast. In a group: it silently sends the user to that group.

## What to build

- Not in a group: create a group with the same call and defaults the sidebar uses (read how
  `app-sidebar.tsx` calls `useCreateGroup` and invalidates the active membership query, and reuse it;
  extract a small shared hook such as `useCreateGroupAndOpen()` if that avoids duplication), then go to
  `/groups/<newId>?playlist=<playlistId>`. Show a loading state on the button while it runs.
- Already in a group: open a dialog (shadcn `AlertDialog`, as used elsewhere) titled
  "You're already in a group" with two actions:
  - "Use it in my group": go to `/groups/<currentId>?playlist=<playlistId>`.
  - "Leave and create a new group": leave the current group (`useLeaveGroup`), then create a new one
    as above. If the current group is in a running game (status `LOCKED`), disable this action and say
    "Your group is in a game right now."
  - Cancel.
- Errors from create or leave show the server's message in a toast.

## Tests

`PlaylistContent` tests (create the test file if missing): no group, creates and navigates; in a
group, the dialog appears and each action calls the right mutations and navigates; locked group
disables leaving.
