# 06. Abrupt page transitions

Branch: `fix/page-transitions`. Frontend only.

## 1. Logged-in visit to `/` shows the landing page, then jumps to playlists

`frontend/app/(marketing)/(landing)/landing-auth-redirect.tsx` redirects in the browser after the
current-user request resolves, so the landing page renders first. `frontend/proxy.ts` (this Next
version's middleware) already reads the `session_hint` cookie for protected routes.

Build: in `proxy.ts`, when the path is exactly `/` and `session_hint` is present, return
`NextResponse.redirect(new URL("/playlists", request.url))`. Keep `LandingAuthRedirect` as a fallback
for an expired session (the cookie can outlive the session; then `/playlists` sends the user to
`/login` anyway, which is fine). Check `frontend/node_modules/next/dist/docs/` for the proxy API before
editing.

## 2. A finished game shows "This game has ended / Back to lobby", then jumps to results

In `frontend/app/(app)/sessions/[sessionId]/page.tsx`: when the session is purged, the session query
errors and the page renders the "This game has ended" screen (around line 445), while an effect (around
line 324: `if (hasEnded && fallbackGroupId !== undefined) router.replace(resultsPath(...))`) redirects a
moment later. Build: while `hasEnded && fallbackGroupId !== undefined`, render the same full-height
loader the page uses while loading (around line 444) instead of the ended screen. The ended screen stays
only when there is no group to send the user to.

## 3. Starting a game flashes the lobby

`frontend/app/(app)/groups/[groupId]/page.tsx` redirects to the session in an effect once the group is
`LOCKED` and the active session query resolves (around line 270). Until then the lobby renders. Build:
when the group status is `LOCKED`, render the lobby's loading state (or the same loader as the session
page) instead of the lobby, until the redirect happens.

## 4. Check the rest

Search the app for other `router.replace`/`router.push` calls inside effects that run after a page has
already rendered its normal content (results page "back to lobby", group page after leaving, join
page). Fix any with the same pattern: show a loader while a redirect is pending. List what you found in
the PR body.

## Tests

- `proxy` unit test: `/` with the cookie redirects, without it passes through.
- Session page: purged session with a known group renders a loader, not the ended text.
- Lobby page: `LOCKED` group renders the loader.
