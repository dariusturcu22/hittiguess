# Frontend implementation guide

## The rule

`docs/design/source/*.dc.html` is the literal spec for every frontend page and screen. Not a mood board, not a starting point for inspiration: the literal structure, spacing, and styling to build. If the current app's layout differs from a mockup's layout, the mockup wins and the app gets rebuilt to match it. This is true regardless of how much existing code already covers that page.

This supersedes any instinct to preserve an existing page's structure and only change its classes or tokens. A restyle that keeps the old DOM structure and reapplies new colors/fonts/spacing on top of it produces a page that looks like the old page with a skin, not like the mockup, whenever the mockup's actual layout differs from what already exists. Retheme-only is only correct when the mockup and the existing page already share the same structure; check that per page, don't assume it.

## What's already frozen

`frontend/app/globals.css` holds the full design token set: every color (Catppuccin Mocha for dark, Latte for light), the font stack (Bungee for display text, Baloo 2 for the wordmark, Space Grotesk for body/UI), the hard-offset shadow scale, and the card/input radii. These values are already copied directly from the mockups and are correct. Building a page is a layout task against these existing tokens, not a color-picking task. Don't add new tokens unless a mockup uses a value genuinely not covered by the existing set (this has happened once already, for the headline text-shadow color, which needed its own token because it couldn't reuse `--card` or `--background` without vanishing against one theme).

## Per-page workflow

For each page in the mapping table below:

1. Open every mockup variant listed for that page (dark and light at minimum; desktop and mobile where both exist).
2. Rebuild the page's actual JSX layout to mirror the mockup's structure: same element hierarchy, same spacing, same sizing, same breakpoint behavior. Don't start from whatever the current page already renders and adjust it; start from the mockup and build toward it. shadcn primitives (`components/shadcn/*`) are used where they're the natural fit for an interactive control (a button is a `Button`, a text field is an `Input`, a dialog is a `Dialog`), not as a constraint on how the page is composed.
3. Wire the mockup's placeholder content (sample playlist names, sample songs, sample usernames) to the real data already available through the generated API hooks in `frontend/hooks/generated`.
4. Render the page and screenshot it at every breakpoint/theme combination the mockup covers, with the corresponding mockup file open side by side. A page is not done until every one of those combinations matches. Reading the code and confirming the right class names are present is not verification; the font-family bug that shipped in an earlier pass had exactly the right class names everywhere and still rendered in the browser default font app-wide, because the underlying CSS variable was broken. Only a rendered comparison catches that class of bug.
5. Update the page's row in `docs/TASKS.md`'s story 28 section once step 4 passes for every variant.

## Mapping: mockup files to routes

Grouped by where each maps in the app. "Route" is the real or planned path under `frontend/app`. A route marked "not built" has no code yet; the page needs to be created, not edited.

### Auth and landing (routes exist)

| Route | Mockup files |
| --- | --- |
| `/` (marketing landing) | `LandingDesktopDark.dc.html`, `LandingDesktopLight.dc.html`, `LandingMobileDark.dc.html`, `LandingMobileLight.dc.html` |
| `/login` | `LoginDark.dc.html`, `LoginLight.dc.html` |
| `/register` | `RegisterDark.dc.html`, `RegisterLight.dc.html` |
| `/forgot-password` | `ForgotPasswordDark.dc.html`, `ForgotPasswordLight.dc.html` |
| `/oauth2/redirect` | `OAuth2RedirectDark.dc.html`, `OAuth2RedirectLight.dc.html` |

### App shell and playlists (routes exist)

| Route | Mockup files |
| --- | --- |
| Shared shell (`frontend/components/app-sidebar.tsx` and the `(app)` layout, used by every route below) | `AppShellDark.dc.html`, `AppShellLight.dc.html` |
| `/playlists` (playlist list) | `YourPlaylistsDark.dc.html`, `YourPlaylistsLight.dc.html` |
| `/playlists/[playlistId]` | `PlaylistDetailDark.dc.html`, `PlaylistDetailLight.dc.html` |
| `/playlists/[playlistId]/songs/[songId]` | `SongDetailDark.dc.html`, `SongDetailLight.dc.html`, `SongDetailNeedsReviewDark.dc.html`, `SongDetailNeedsReviewLight.dc.html`, `EditSongDetailsDark.dc.html`, `EditSongDetailsLight.dc.html` (edit state) |
| `/playlists/[playlistId]/songs/add` | `AddSongDark.dc.html`, `AddSongLight.dc.html`, `AddNewSongLinkDark.dc.html`, `AddNewSongLinkLight.dc.html` (paste-a-link step), `AddNewSongReviewEditableDark.dc.html`, `AddNewSongReviewEditableLight.dc.html` (confirm step, editable), `AddNewSongReviewLockedDark.dc.html`, `AddNewSongReviewLockedLight.dc.html` (confirm step, a verified song's locked fields) |
| `/playlists/join/[inviteCode]` | `JoinInviteDark.dc.html`, `JoinInviteLight.dc.html` |

### Planned playlist screens (routes not built, story 30/40/45/46)

| Route (proposed) | Mockup files |
| --- | --- |
| `/playlists/explore` | `ExplorePlaylistsDark.dc.html`, `ExplorePlaylistsLight.dc.html` |
| `/playlists/[playlistId]/edit` | `EditPlaylistDark.dc.html`, `EditPlaylistLight.dc.html` |
| Import flow, exact route structure TBD when story 40/45 frontend tasks are scoped | `ImportChooseSourceDark.dc.html`, `ImportChooseSourceLight.dc.html`, `ImportPlaylistLinkDark.dc.html`, `ImportPlaylistLinkLight.dc.html`, `ImportFromPlaylistSelectDark.dc.html`, `ImportFromPlaylistSelectLight.dc.html`, `ImportFromPlaylistConfirmDark.dc.html`, `ImportFromPlaylistConfirmLight.dc.html`, `ImportPlaylistProcessingDark.dc.html`, `ImportPlaylistProcessingLight.dc.html`, `ImportProgressDark.dc.html`, `ImportProgressLight.dc.html` |

### Admin (routes not built, story 17/40, gated on the `ADMIN` role)

| Route (proposed) | Mockup files |
| --- | --- |
| `/admin/catalog` | `AdminCatalogBacklogDark.dc.html`, `AdminCatalogBacklogLight.dc.html` |
| `/admin/reports` | `AdminReportQueueDark.dc.html`, `AdminReportQueueLight.dc.html` |

### Gameplay (routes not built, stories 9/10/11/12/13/39)

| Route (proposed) | Mockup files |
| --- | --- |
| Group lobby | `GroupLobbyDark.dc.html`, `GroupLobbyLight.dc.html`, `GroupLobbyTwoPlayersDark.dc.html`, `GroupLobbyTwoPlayersLight.dc.html`, `GroupLobbyEightPlayersDark.dc.html`, `GroupLobbyEightPlayersLight.dc.html`, `GroupLobbyChatDark.dc.html`, `GroupLobbyChatLight.dc.html`, `GroupLobbySettingsDark.dc.html`, `GroupLobbySettingsLight.dc.html` |
| Game session/timeline | `GameSessionRoundIntroDark.dc.html`, `GameSessionRoundIntroLight.dc.html`, `GameSessionDJDark.dc.html`, `GameSessionDJLight.dc.html`, `GameSessionPlayerDark.dc.html`, `GameSessionPlayerLight.dc.html`, `GameSessionCardDraggingDark.dc.html`, `GameSessionCardDraggingLight.dc.html`, `GameSessionCardDraggingSpectatorDark.dc.html`, `GameSessionCardDraggingSpectatorLight.dc.html`, `GameSessionCardDroppedDark.dc.html`, `GameSessionCardDroppedLight.dc.html`, `GameSessionCardLockedDark.dc.html`, `GameSessionCardLockedLight.dc.html`, `GameSessionBettingPrepDark.dc.html`, `GameSessionBettingPrepLight.dc.html`, `GameSessionBettingPrepTokenHolderDark.dc.html`, `GameSessionBettingPrepTokenHolderLight.dc.html`, `GameSessionBettingActiveDark.dc.html`, `GameSessionBettingActiveLight.dc.html`, `GameSessionBettingActiveTokenHolderDark.dc.html`, `GameSessionBettingActiveTokenHolderLight.dc.html`, `GameSessionRevealDark.dc.html`, `GameSessionRevealLight.dc.html` |
| In-session overlays (not separate routes, components within the game session screen) | `TurnNotificationDark.dc.html`, `TurnNotificationLight.dc.html`, `AwayWidgetDark.dc.html`, `AwayWidgetLight.dc.html`, `TextChatOverlayDark.dc.html`, `TextChatOverlayLight.dc.html` |
| Results/leaderboard | `ResultsDark.dc.html`, `ResultsLight.dc.html`, `ResultsTwoPlayersDark.dc.html`, `ResultsTwoPlayersLight.dc.html`, `ResultsEightPlayersDark.dc.html`, `ResultsEightPlayersLight.dc.html` |

### Reference sheets, not pages

These are component-variant exploration boards (flat gray canvas, not a themed app page), used as reference when building the component they cover, not implemented as a route themselves.

| File | Covers |
| --- | --- |
| `CardOptions.dc.html` | Song card visual variants |
| `NotificationOptions.dc.html` | Toast/notification visual variants |
| `TokenPileOptions.dc.html` | Betting token pile visual variants |
| `PlayfulCatppuccinDark.dc.html`, `PlayfulCatppuccinLight.dc.html` | Early palette/style exploration, superseded by the tokens in `globals.css` |
