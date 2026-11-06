import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";

const GROUP_ID = 4;
const SESSION_ID = 9;
const FIRST_USER_ID = 11;
const SECOND_USER_ID = 12;
const FIRST_PLAYER_ID = 6;
const SECOND_PLAYER_ID = 7;
const ROUND_ID = 30;
const DESKTOP = { width: 1440, height: 900 };
const RECONNECT_TIMEOUT_MILLISECONDS = 10_000;
const AVATAR = "data:image/svg+xml;base64," + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#89b4fa"/><rect x="8" y="8" width="16" height="16" fill="#313244"/></svg>').toString("base64");
const MEMBERS = [
  { id: 1, userId: FIRST_USER_ID, displayName: "Alex", avatarUrl: AVATAR, isAdmin: true, isConnected: true },
  { id: 2, userId: SECOND_USER_ID, displayName: "Sam", isAdmin: false, isConnected: true },
];
const STARTING_TIMELINE = [
  { songId: 31, artist: "Sample artist", title: "First track", releaseYear: 1998, color: "89b4fa", position: 0 },
  { songId: 32, artist: "Another artist", title: "Second track", releaseYear: 2003, color: "a6e3a1", position: 1 },
];
const SESSION_TOPIC = `/topic/sessions/${SESSION_ID}/round`;
const HTTP_OK_STATUS = 200;
const HTTP_NO_CONTENT_STATUS = 204;
const HTTP_FORBIDDEN_STATUS = 403;
const HTTP_NOT_FOUND_STATUS = 404;
const STOMP_COMMAND = { connect: "CONNECT", stomp: "STOMP", subscribe: "SUBSCRIBE", send: "SEND" };
const TERMINATOR = "\0";
const HEADER_SEPARATOR = ":";
const BODY_SEPARATOR = "\n\n";
const SOCKET_CLOSE_CODE = 1011;

type Connection = { userId: number; socket: WebSocketRoute; subscriptions: Map<string, string> };

class ReconnectFixtureBroker {
  readonly connections = new Set<Connection>();
  activeGame = true;
  gameCompleted = false;
  isMemberRemoved = false;
  round = {
    id: ROUND_ID,
    turnNumber: 3,
    roundNumber: 2,
    activePlayerId: FIRST_PLAYER_ID,
    djPlayerId: SECOND_PLAYER_ID,
    status: "AWAITING_PLACEMENT",
    countdownEndsAt: undefined as string | undefined,
  };
  guessState = {
    roundId: ROUND_ID,
    artistCount: 2,
    correctArtistCount: 0,
    artistGuessingClosed: false,
    titleGuessed: false,
    titleCorrect: false,
    tokenEarned: false,
  };

  send(destination: string, payload: unknown) {
    for (const connection of this.connections) {
      const subscription = connection.subscriptions.get(destination);
      if (subscription) {
        connection.socket.send(`MESSAGE\ndestination:${destination}\nsubscription:${subscription}\nmessage-id:fixture\n\n${JSON.stringify(payload)}${TERMINATOR}`);
      }
    }
  }

  async setupRoutes(page: Page, userId: number) {
    await page.setViewportSize(DESKTOP);
    await page.context().addCookies([{ name: "session_hint", value: "reconnect-fixture", url: "http://localhost:3000" }]);

    await page.routeWebSocket("**/ws", (socket) => {
      const connection: Connection = { userId, socket, subscriptions: new Map() };
      this.connections.add(connection);
      socket.onClose(() => this.connections.delete(connection));
      socket.onMessage((message) => {
        const [headersBlock] = message.toString().split(BODY_SEPARATOR);
        const [command, ...headerLines] = headersBlock.split("\n");
        const headers = new Map(headerLines.map((header) => {
          const separatorIndex = header.indexOf(HEADER_SEPARATOR);
          return [header.slice(0, separatorIndex), header.slice(separatorIndex + HEADER_SEPARATOR.length)];
        }));
        if (command === STOMP_COMMAND.connect || command === STOMP_COMMAND.stomp) {
          socket.send(`CONNECTED\nversion:1.2\nheart-beat:0,0\n\n${TERMINATOR}`);
        }
        if (command === STOMP_COMMAND.subscribe) {
          connection.subscriptions.set(headers.get("destination") ?? "", headers.get("id") ?? "");
        }
      });
    });

    await page.route("**/api/**", async (route) => {
      const path = new URL(route.request().url()).pathname;
      let status = HTTP_OK_STATUS;
      let body: unknown = {};

      if (path === "/api/users/me") {
        body = { id: userId, username: "Alex", role: "USER" };
      } else if (path === "/api/groups/active") {
        if (this.isMemberRemoved) {
          status = HTTP_NO_CONTENT_STATUS;
          body = undefined;
        } else {
          body = { id: GROUP_ID, status: this.activeGame ? "LOCKED" : "OPEN", joinCode: "ABCD", playlists: [], members: MEMBERS };
        }
      } else if (path === `/api/groups/${GROUP_ID}`) {
        if (this.isMemberRemoved) {
          status = HTTP_FORBIDDEN_STATUS;
          body = { message: "Access denied" };
        } else {
          body = { id: GROUP_ID, status: this.activeGame ? "LOCKED" : "OPEN", joinCode: "ABCD", playlists: [], members: MEMBERS };
        }
      } else if (path === `/api/sessions/groups/${GROUP_ID}/active`) {
        if (this.activeGame && !this.gameCompleted) {
          body = { id: SESSION_ID };
        } else {
          status = HTTP_NO_CONTENT_STATUS;
          body = undefined;
        }
      } else if (path === `/api/sessions/${SESSION_ID}`) {
        if (this.gameCompleted) {
          status = HTTP_NOT_FOUND_STATUS;
          body = { message: "Session not found" };
        } else {
          body = {
            id: SESSION_ID,
            groupId: GROUP_ID,
            winConditionCardCount: 8,
            djMode: "ROTATING",
            currentRoundNumber: this.round.roundNumber,
            currentRound: this.round,
            players: [
              { id: FIRST_PLAYER_ID, userId: FIRST_USER_ID, displayName: "Alex", status: "ACTIVE", turnOrder: 0, timeline: STARTING_TIMELINE, tokenCount: 2 },
              { id: SECOND_PLAYER_ID, userId: SECOND_USER_ID, displayName: "Sam", status: "ACTIVE", turnOrder: 1, timeline: STARTING_TIMELINE, tokenCount: 2 },
            ],
          };
        }
      } else if (path.endsWith("/guess-state")) {
        body = this.guessState;
      } else if (path === `/api/sessions/groups/${GROUP_ID}/results`) {
        body = {
          groupId: GROUP_ID,
          cardCountRanking: [
            { playerId: FIRST_PLAYER_ID, displayName: "Alex", cardCount: 8, rank: 1 },
            { playerId: SECOND_PLAYER_ID, displayName: "Sam", cardCount: 4, rank: 2 },
          ],
          artistLeaderboard: [],
          titleLeaderboard: [],
        };
      } else if (path.includes("playlists") || path.includes("messages")) {
        body = [];
      } else if (path.includes("/import")) {
        status = HTTP_NO_CONTENT_STATUS;
        body = undefined;
      }

      await route.fulfill({
        status,
        contentType: "application/json",
        body: body === undefined ? undefined : JSON.stringify(body),
        headers: {
          "access-control-allow-origin": "http://localhost:3000",
          "access-control-allow-credentials": "true",
        },
      });
    });
  }
}

test.describe("Reconnect recovery", () => {
  test("recovers updated round phase across socket drop and reconnect", async ({ page }) => {
    const broker = new ReconnectFixtureBroker();
    await broker.setupRoutes(page, FIRST_USER_ID);

    await page.goto(`/sessions/${SESSION_ID}`);
    await page.addStyleTag({ content: "nextjs-portal { display: none; }" });

    await expect(page.getByText("Your turn", { exact: true })).toBeVisible();

    const activeConnection = [...broker.connections].find((connection) => connection.subscriptions.has(SESSION_TOPIC));
    if (!activeConnection) {
      throw new Error("Missing active session subscription");
    }

    // Drop the socket while the player is offline
    await activeConnection.socket.close({ code: SOCKET_CLOSE_CODE });

    // While offline, the round advances to betting phase
    broker.round = {
      ...broker.round,
      status: "BETTING",
      activePlayerId: SECOND_PLAYER_ID,
    };

    // Wait for the client to reconnect
    await expect.poll(
      () => [...broker.connections].some((connection) => connection !== activeConnection && connection.subscriptions.has(SESSION_TOPIC)),
      { timeout: RECONNECT_TIMEOUT_MILLISECONDS },
    ).toBe(true);

    // On reconnect, the session query was refetched and reflects the BETTING phase
    await expect(page.getByText("Betting open")).toBeVisible();
  });

  test("sends player to results when game completed while offline", async ({ page }) => {
    const broker = new ReconnectFixtureBroker();
    await broker.setupRoutes(page, FIRST_USER_ID);

    await page.goto(`/sessions/${SESSION_ID}`);
    await page.addStyleTag({ content: "nextjs-portal { display: none; }" });

    await expect(page.getByText("Your turn", { exact: true })).toBeVisible();

    const activeConnection = [...broker.connections].find((connection) => connection.subscriptions.has(SESSION_TOPIC));
    if (!activeConnection) {
      throw new Error("Missing active session subscription");
    }

    // Drop the socket
    await activeConnection.socket.close({ code: SOCKET_CLOSE_CODE });

    // While offline, game completed on backend and session was purged
    broker.gameCompleted = true;
    broker.activeGame = false;

    // Wait for reconnect
    await expect.poll(
      () => [...broker.connections].some((connection) => connection !== activeConnection),
      { timeout: RECONNECT_TIMEOUT_MILLISECONDS },
    ).toBe(true);

    // Reconnect refetches session (which is 404) and navigates to the results page
    await expect(page).toHaveURL(new RegExp(`/sessions/${SESSION_ID}/results`));
    await expect(page.getByText("Alex won!")).toBeVisible();
  });

  test("shows removed state when group removal occurred while offline", async ({ page }) => {
    const broker = new ReconnectFixtureBroker();
    broker.activeGame = false;
    await broker.setupRoutes(page, FIRST_USER_ID);

    await page.goto(`/groups/${GROUP_ID}`);
    await page.addStyleTag({ content: "nextjs-portal { display: none; }" });

    await expect(page.getByRole("heading", { name: "Group Lobby" })).toBeVisible();

    const activeConnection = [...broker.connections].find((connection) => connection.subscriptions.has(`/topic/groups/${GROUP_ID}/membership`));
    if (!activeConnection) {
      throw new Error("Missing group membership subscription");
    }

    // Drop the socket
    await activeConnection.socket.close({ code: SOCKET_CLOSE_CODE });

    // While offline, admin removed the player
    broker.isMemberRemoved = true;

    // Wait for reconnect
    await expect.poll(
      () => [...broker.connections].some((connection) => connection !== activeConnection),
      { timeout: RECONNECT_TIMEOUT_MILLISECONDS },
    ).toBe(true);

    // Reconnect refetches group, receives 403 / removed status, and shows unavailable screen
    await expect(page.getByRole("heading", { name: "Group unavailable" })).toBeVisible();
    await expect(page.getByText("This group may have ended, or this account no longer has access to it.")).toBeVisible();
  });
});
