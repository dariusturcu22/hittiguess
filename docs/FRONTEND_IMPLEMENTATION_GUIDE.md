# Frontend implementation guide

## The rule

Approved product requirements take precedence over older mockups. A later approved review or playtest requirement first updates the affected mockups and content specification. The updated design source files then define the layout, spacing, styling, and states to build and verify. Existing implementation alone does not establish a new requirement.

The approved library and lobby requirements retain library and Explore filters, playlist member popups, import inside Add song, and the separate lobby playlist picker. Older layouts that omit these controls are superseded. The approved gameplay requirements retain automatic reveal after betting, full-pass rounds, joint winners, and guessing that closes at placement lock-in. Voice uses a 76px rail without collapse, with join at the top outside a call. Away-turn notifications, phase deadlines, and actual token-award feedback follow the content specification. The documentation audit records the reviewed resolutions and the explicitly deferred account/legal scope. App screens have desktop design scope; only the landing page has mobile variants.

Library filter states use `YourPlaylistsOwned*`, `YourPlaylistsJoined*`, and `YourPlaylistsSaved*`. The playlist add menu uses `PlaylistDetailAddMenu*`. Lobby picker, custom selection, and admin transfer use `GroupLobbyTierPicker*`, `GroupLobbyCustomPicker*`, and `GroupLobbyAdminTransfer*`. Each state has dark and light source files alongside its base screen.

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

Grouped by where each maps in the app. "Route" is the real or planned path under `frontend/app`. A route marked "planned" has no code yet; the page needs to be created, not edited.

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

### Playlist screens (routes implemented)

| Route (proposed) | Mockup files |
| --- | --- |
| `/explore` | `ExplorePlaylistsDark.dc.html`, `ExplorePlaylistsLight.dc.html` |
| `/playlists/[playlistId]/edit` | `EditPlaylistDark.dc.html`, `EditPlaylistLight.dc.html` |
| Import flow: `/playlists/[playlistId]/import`, `/playlists/[playlistId]/import/youtube`, `/playlists/[playlistId]/import/from-playlist`, `/playlists/[playlistId]/import/from-playlist/[sourceId]` | `ImportChooseSourceDark.dc.html`, `ImportChooseSourceLight.dc.html`, `ImportPlaylistLinkDark.dc.html`, `ImportPlaylistLinkLight.dc.html`, `ImportFromPlaylistSelectDark.dc.html`, `ImportFromPlaylistSelectLight.dc.html`, `ImportFromPlaylistConfirmDark.dc.html`, `ImportFromPlaylistConfirmLight.dc.html`, `ImportPlaylistProcessingDark.dc.html`, `ImportPlaylistProcessingLight.dc.html`, `ImportProgressDark.dc.html`, `ImportProgressLight.dc.html` |

### Admin (routes implemented, story 17/40, gated on the `ADMIN` role)

| Route (proposed) | Mockup files |
| --- | --- |
| `/admin/catalog-backlog` | `AdminCatalogBacklogDark.dc.html`, `AdminCatalogBacklogLight.dc.html` |
| `/admin/reports` | `AdminReportQueueDark.dc.html`, `AdminReportQueueLight.dc.html` |

### Gameplay (routes implemented, stories 9/10/11/12/13/39)

| Route (proposed) | Mockup files |
| --- | --- |
| Group lobby | `GroupLobbyDark.dc.html`, `GroupLobbyLight.dc.html`, `GroupLobbyTwoPlayersDark.dc.html`, `GroupLobbyTwoPlayersLight.dc.html`, `GroupLobbyEightPlayersDark.dc.html`, `GroupLobbyEightPlayersLight.dc.html`, `GroupLobbyChatDark.dc.html`, `GroupLobbyChatLight.dc.html`, `GroupLobbySettingsDark.dc.html`, `GroupLobbySettingsLight.dc.html` |
| Game session/timeline | `GameSessionRoundIntroDark.dc.html`, `GameSessionRoundIntroLight.dc.html`, `GameSessionDJDark.dc.html`, `GameSessionDJLight.dc.html`, `GameSessionPlayerDark.dc.html`, `GameSessionPlayerLight.dc.html`, `GameSessionCardDraggingDark.dc.html`, `GameSessionCardDraggingLight.dc.html`, `GameSessionCardDraggingSpectatorDark.dc.html`, `GameSessionCardDraggingSpectatorLight.dc.html`, `GameSessionCardDroppedDark.dc.html`, `GameSessionCardDroppedLight.dc.html`, `GameSessionCardLockedDark.dc.html`, `GameSessionCardLockedLight.dc.html`, `GameSessionBettingPrepDark.dc.html`, `GameSessionBettingPrepLight.dc.html`, `GameSessionBettingPrepTokenHolderDark.dc.html`, `GameSessionBettingPrepTokenHolderLight.dc.html`, `GameSessionBettingActiveDark.dc.html`, `GameSessionBettingActiveLight.dc.html`, `GameSessionBettingActiveTokenHolderDark.dc.html`, `GameSessionBettingActiveTokenHolderLight.dc.html`, `GameSessionRevealDark.dc.html`, `GameSessionRevealLight.dc.html`, `GameSessionTokenEarnedDark.dc.html`, `GameSessionTokenEarnedLight.dc.html`, `GameSessionIncorrectGuessDark.dc.html`, `GameSessionIncorrectGuessLight.dc.html` |
| Gameplay overlays and app-wide away state (not separate routes) | `TurnNotificationDark.dc.html`, `TurnNotificationLight.dc.html`, `AwayWidgetDark.dc.html`, `AwayWidgetLight.dc.html`, `TextChatOverlayDark.dc.html`, `TextChatOverlayLight.dc.html` |
| Results/leaderboard | `ResultsDark.dc.html`, `ResultsLight.dc.html`, `ResultsTwoPlayersDark.dc.html`, `ResultsTwoPlayersLight.dc.html`, `ResultsEightPlayersDark.dc.html`, `ResultsEightPlayersLight.dc.html` |

The current `dev` frontend implements the routes and gameplay wiring for Batches A through E. Choose-source and existing-playlist import states and colocated route/state tests exist. The full desktop rendered matrix, remaining contrast/keyboard/motion checks, and real HTTPS multiplayer media acceptance remain open. App screens are checked on desktop; only the landing page has mobile scope. Live Playwright validation of the group and gameplay flows requires the backend services to run from this same checkout.

### Reference sheets, not pages

These are component-variant exploration boards (flat gray canvas, not a themed app page), used as reference when building the component they cover, not implemented as a route themselves.

| File | Covers |
| --- | --- |
| `CardOptions.dc.html` | Song card visual variants |
| `NotificationOptions.dc.html` | Toast/notification visual variants |
| `TokenPileOptions.dc.html` | Betting token pile visual variants |
| `PlayfulCatppuccinDark.dc.html`, `PlayfulCatppuccinLight.dc.html` | Early palette/style exploration, superseded by the tokens in `globals.css` |

## Game history

`/history` uses GameHistoryDark.dc.html and GameHistoryLight.dc.html. `/history/[summaryId]` uses GameHistoryDetailDark.dc.html and GameHistoryDetailLight.dc.html. The history list has core-backed totals, a paginated table, and explicit loading, error, and empty states. Detail shows participant-only results, shared ranks, departed/deleted players, and interruptions. The generated-song review state is superseded: Confirm stores a tier and Start selects the hidden pool.
