# 19. Leaving a group and creating or joining another can leave the user with no group

Branch: `fix/replace-group-atomically`. Backend and frontend. Files:
`backend/src/main/java/org/dariusturcu/backend/service/GroupService.java` (`createGroup` around line 79,
`joinGroup`, `leaveGroup` around line 244), `backend/.../controller/GroupController.java`, the create and
join request records, `frontend/hooks/use-create-group-and-open.ts`,
`frontend/app/(app)/playlists/[playlistId]/PlaylistContent.tsx`,
`frontend/app/(join)/groups/join/[inviteCode]/page.tsx`, the regenerated Orval hooks and models, and the
tests next to each.

## What is wrong

Two flows replace the user's current group with two separate HTTP requests from the browser:

1. "Leave and create a new group" on a playlist (task 05): `useCreateGroupAndOpen.leaveGroupAndCreateGroup`
   calls `POST /api/groups/{groupId}/leave`, and only in its `onSuccess` calls `POST /api/groups`.
2. "Leave and join" on an invite link: `handleLeaveAndRetry` in the join page calls the leave endpoint,
   then `submitJoin()`.

If the second request fails (network drop, closed tab, rate limit, a full group, an expired invite, a
server error), the first one has already committed: the user has left their group, gets an error
toast, and ends up in no group. When they were the admin, the admin role has already passed to someone
else, or the group was deleted.

## What to build

1. Backend: one transaction does both steps. Add an optional `replaceCurrentGroup` boolean to the create
   and join request bodies (default false, so existing callers keep today's behavior). When it is true
   and the caller has an active membership, `createGroup` and `joinGroup` first run the same leave logic
   as `leaveGroup` (extract it into a private method that takes the group and member, so both paths share
   it), then create or join, all inside the one `@Transactional` method. Any failure rolls back the
   leave as well.
2. Validate before leaving: for a join, check the invite or join code, capacity, and group status first,
   so a join that is bound to fail does not leave at all. A group whose game is running cannot be left
   this way: reject with the same error the leave endpoint gives today (task 05 disables the button in
   that case, but the server must enforce it too).
3. Broadcasts: the leave's `MEMBER_LEFT` and `ADMIN_CHANGED` events must only go out if the whole
   transaction commits. `GroupBroadcastListener` is already a `@TransactionalEventListener`; check that
   every event published on this path goes through it, and that nothing is sent on rollback.
4. Regenerate the Orval client after the request records change (see `docs/` or `CONTRIBUTING.md` for the
   generate command).
5. Frontend: `leaveGroupAndCreateGroup` becomes one `createGroup` call with `replaceCurrentGroup: true`,
   and the join page's `handleLeaveAndRetry` becomes one join call with `replaceCurrentGroup: true`.
   Remove the `useLeaveGroup` calls from both flows. Keep the pending and error states the dialogs show
   today.
6. Append a dated entry to `docs/DECISIONS.md` (replacing a group is one server transaction, never two
   client requests) and update the group endpoints in `docs/SYSTEM_REFERENCE.md`.

## Tests

- Backend service tests: create with `replaceCurrentGroup` moves the caller to the new group; when
  creating fails after the leave step (force it with a mocked repository error), the caller is still in
  the old group and no `MEMBER_LEFT` event was broadcast.
- Join with `replaceCurrentGroup` to a full group or with an invalid code leaves the caller in the old
  group.
- The admin role passes to the next member only when the replacement commits.
- A running game blocks the replacement.
- Frontend: the playlist dialog and the join page each make exactly one request, with
  `replaceCurrentGroup: true`, and never call the leave endpoint.
