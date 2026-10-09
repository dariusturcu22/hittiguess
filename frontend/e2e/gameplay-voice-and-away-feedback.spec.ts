import { expect, test, type Page, type WebSocketRoute } from "@playwright/test";

const GROUP_ID = 4;
const SESSION_ID = 9;
const FIRST_USER_ID = 11;
const SECOND_USER_ID = 12;
const FIRST_PLAYER_ID = 6;
const SECOND_PLAYER_ID = 7;
const ROUND_ID = 30;
const DESKTOP = { width: 1440, height: 900 };
const PHASE_DURATION_MILLISECONDS = 10_000;
const RECONNECT_TIMEOUT_MILLISECONDS = 8_000;
const AVATAR = "data:image/svg+xml;base64," + Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#89b4fa"/><rect x="8" y="8" width="16" height="16" fill="#313244"/></svg>').toString("base64");
const MEMBERS = [{ id: 1, userId: FIRST_USER_ID, displayName: "Alex", avatarUrl: AVATAR, isAdmin: true, isConnected: true }, { id: 2, userId: SECOND_USER_ID, displayName: "Sam", isAdmin: false, isConnected: true }];
const STARTING_TIMELINE = [{ songId: 31, artist: "Sample artist", title: "First track", releaseYear: 1998, color: "89b4fa", position: 0 }, { songId: 32, artist: "Another artist", title: "Second track", releaseYear: 2003, color: "a6e3a1", position: 1 }];
const SESSION_TOPIC = `/topic/sessions/${SESSION_ID}/round`;
const GUESS_RESULT_QUEUE = `/user/queue/sessions/${SESSION_ID}/guess-result`;
const VOICE_TOPIC = `/topic/groups/${GROUP_ID}/voice`;
const SIGNAL_QUEUE = `/user/queue/groups/${GROUP_ID}/voice-signal`;
const HTTP_OK_STATUS = 200;
const HTTP_NO_CONTENT_STATUS = 204;
const STOMP_COMMAND = { connect: "CONNECT", stomp: "STOMP", subscribe: "SUBSCRIBE", send: "SEND" };
const TERMINATOR = "\0";
const HEADER_SEPARATOR = ":";
const BODY_SEPARATOR = "\n\n";
const SOCKET_CLOSE_CODE = 1011;
const MICROPHONE_TONE_HERTZ = 440;
const MICROPHONE_TONE_GAIN = 0.1;
type Connection = { userId: number; socket: WebSocketRoute; subscriptions: Map<string, string> };

class FixtureBroker {
  readonly connections = new Set<Connection>();
  readonly voiceUserIds = new Set<number>();
  activeGame = false;
  ownTokenCount = 3;
  guessState = { roundId: ROUND_ID, artistCount: 2, correctArtistCount: 0, artistGuessingClosed: false, titleGuessed: false, titleCorrect: false, tokenEarned: false };
  round = { id: ROUND_ID, turnNumber: 3, roundNumber: 2, activePlayerId: SECOND_PLAYER_ID, djPlayerId: SECOND_PLAYER_ID, status: "AWAITING_PLACEMENT", countdownEndsAt: undefined as string | undefined };
  send(destination: string, payload: unknown, targetUserId?: number) {
    for (const connection of this.connections) {
      if (targetUserId !== undefined && connection.userId !== targetUserId) continue;
      const subscription = connection.subscriptions.get(destination);
      if (subscription) connection.socket.send(`MESSAGE\ndestination:${destination}\nsubscription:${subscription}\nmessage-id:fixture\n\n${JSON.stringify(payload)}${TERMINATOR}`);
    }
  }
  async fixture(page: Page, userId: number, theme: string) {
    await page.setViewportSize(DESKTOP);
    await page.context().addCookies([{ name: "session_hint", value: "gameplay-feedback-fixture", url: "http://localhost:3000" }]);
    await page.addInitScript(({ selectedTheme, frequency, gainValue }) => {
      localStorage.setItem("theme", selectedTheme);
      const counters = window as unknown as Window & { turnSoundCount: number; microphoneContexts: AudioContext[] };
      counters.turnSoundCount = 0;
      counters.microphoneContexts = [];
      const originalStart = OscillatorNode.prototype.start;
      OscillatorNode.prototype.start = function (...argumentsList) { counters.turnSoundCount += 1; originalStart.apply(this, argumentsList); };
      Object.defineProperty(navigator.mediaDevices, "getUserMedia", { value: async () => {
        const context = new AudioContext();
        counters.microphoneContexts.push(context);
        await context.resume();
        const oscillator = context.createOscillator();
        const gain = context.createGain();
        const destination = context.createMediaStreamDestination();
        oscillator.frequency.value = frequency;
        gain.gain.value = gainValue;
        oscillator.connect(gain);
        gain.connect(destination);
        oscillator.start();
        return destination.stream;
      } });
    }, { selectedTheme: theme, frequency: MICROPHONE_TONE_HERTZ, gainValue: MICROPHONE_TONE_GAIN });
    await page.routeWebSocket("**/ws", (socket) => {
      const connection: Connection = { userId, socket, subscriptions: new Map() };
      this.connections.add(connection);
      socket.onClose(() => this.connections.delete(connection));
      socket.onMessage((message) => {
        const [headersBlock, payloadBlock = ""] = message.toString().split(BODY_SEPARATOR);
        const [command, ...headerLines] = headersBlock.split("\n");
        const headers = new Map(headerLines.map((header) => { const separator = header.indexOf(HEADER_SEPARATOR); return [header.slice(0, separator), header.slice(separator + HEADER_SEPARATOR.length)]; }));
        if (command === STOMP_COMMAND.connect || command === STOMP_COMMAND.stomp) socket.send(`CONNECTED\nversion:1.2\nheart-beat:0,0\n\n${TERMINATOR}`);
        if (command === STOMP_COMMAND.subscribe) connection.subscriptions.set(headers.get("destination") ?? "", headers.get("id") ?? "");
        if (command === STOMP_COMMAND.send && headers.get("destination")?.endsWith("/voice/signal")) {
          const signal = JSON.parse(payloadBlock.replaceAll(TERMINATOR, ""));
          this.send(SIGNAL_QUEUE, { ...signal, senderUserId: userId }, signal.targetMemberUserId);
        }
      });
    });
    await page.route("**/api/**", async (route) => {
      const path = new URL(route.request().url()).pathname;
      let status = HTTP_OK_STATUS;
      let body: unknown = {};
      const group = () => ({ id: GROUP_ID, status: this.activeGame ? "LOCKED" : "OPEN", joinCode: "ABCD", djMode: "ROTATING", winConditionCardCount: 5, playlists: [], members: MEMBERS.map((member) => ({ ...member, isInVoice: this.voiceUserIds.has(member.userId) })) });
      if (path.endsWith("/voice/join")) { this.voiceUserIds.add(userId); body = group(); this.send(VOICE_TOPIC, { type: "VOICE_PRESENCE_CHANGED" }); }
      else if (path.endsWith("/voice/leave")) { this.voiceUserIds.delete(userId); body = group(); this.send(VOICE_TOPIC, { type: "VOICE_PRESENCE_CHANGED" }); }
      else if (path.includes("/voice/turn")) body = { iceServers: [] };
      else if (path === "/api/users/me") body = { id: userId, username: userId === FIRST_USER_ID ? "Alex" : "Sam", role: "USER" };
      else if (path === "/api/groups/active" || path === `/api/groups/${GROUP_ID}`) body = group();
      else if (path === `/api/sessions/groups/${GROUP_ID}/active`) { body = this.activeGame ? { id: SESSION_ID } : undefined; status = this.activeGame ? HTTP_OK_STATUS : HTTP_NO_CONTENT_STATUS; }
      else if (path === `/api/sessions/${SESSION_ID}`) body = { id: SESSION_ID, groupId: GROUP_ID, winConditionCardCount: 8, djMode: "ROTATING", currentRoundNumber: this.round.roundNumber, currentRound: this.round, players: [{ id: FIRST_PLAYER_ID, userId: FIRST_USER_ID, displayName: "Alex", status: "ACTIVE", turnOrder: 0, timeline: STARTING_TIMELINE, tokenCount: this.ownTokenCount }, { id: SECOND_PLAYER_ID, userId: SECOND_USER_ID, displayName: "Sam", status: "ACTIVE", turnOrder: 1, timeline: STARTING_TIMELINE, tokenCount: 2 }] };
      else if (path.endsWith("/guess-state")) body = this.guessState;
      else if (path.includes("playlists") || path.includes("messages")) body = [];
      else if (path.includes("/import")) { status = HTTP_NO_CONTENT_STATUS; body = undefined; }
      await route.fulfill({ status, contentType: "application/json", body: body === undefined ? undefined : JSON.stringify(body), headers: { "access-control-allow-origin": "http://localhost:3000", "access-control-allow-credentials": "true" } });
    });
  }
}

for (const theme of ["dark", "light"]) {
  test(`${theme} desktop: away turn alert, deadline and reconnect`, async ({ page }, testInfo) => {
    const broker = new FixtureBroker();
    broker.activeGame = true;
    await broker.fixture(page, FIRST_USER_ID, theme);
    await page.goto("/playlists");
    await page.addStyleTag({ content: "nextjs-portal { display: none; }" });
    await expect(page.getByRole("link", { name: "Return to game in progress" })).toContainText("Your tokens: 3");
    await page.getByRole("heading", { name: "Your playlists" }).click();
    broker.round = { ...broker.round, activePlayerId: FIRST_PLAYER_ID };
    broker.send(SESSION_TOPIC, { type: "ROUND_STARTED", sessionId: SESSION_ID, payload: broker.round });
    await expect(page.getByRole("status")).toContainText("Your turn!");
    await expect.poll(() => page.evaluate(() => (window as unknown as Window & { turnSoundCount: number }).turnSoundCount)).toBe(1);
    await page.screenshot({ path: testInfo.outputPath("away-turn.png") });
    const ownConnection = [...broker.connections].find((connection) => connection.subscriptions.has(SESSION_TOPIC));
    await ownConnection?.socket.close({ code: SOCKET_CLOSE_CODE });
    await expect.poll(() => [...broker.connections].filter((connection) => connection.subscriptions.has(SESSION_TOPIC)).length, { timeout: RECONNECT_TIMEOUT_MILLISECONDS }).toBe(1);
    expect(await page.evaluate(() => (window as unknown as Window & { turnSoundCount: number }).turnSoundCount)).toBe(1);
    broker.round = { ...broker.round, status: "COUNTDOWN", countdownEndsAt: new Date(Date.now() + PHASE_DURATION_MILLISECONDS).toISOString() };
    broker.send(SESSION_TOPIC, { type: "GUESS_LOCKED", sessionId: SESSION_ID, payload: broker.round });
    await expect(page.getByRole("status")).not.toBeVisible();
    await expect(page.getByText(/Phase ends in \d+s/)).toBeVisible();
    await expect(page.getByText("Alex locked their placement")).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath("away-countdown.png") });
    broker.round = { ...broker.round, status: "AWAITING_PLACEMENT" };
    await page.getByRole("link", { name: "Return to game in progress" }).click();
    await expect.poll(() => [...broker.connections].some((connection) => connection.subscriptions.has(GUESS_RESULT_QUEUE))).toBe(true);
    await expect(page.getByText("Your turn", { exact: true })).toBeVisible();
    await page.getByRole("textbox", { name: "Guess the artist" }).fill("Sample artist");
    await page.getByRole("button", { name: "Submit guess the artist" }).click();
    broker.guessState = { ...broker.guessState, correctArtistCount: 1 };
    broker.send(GUESS_RESULT_QUEUE, { roundId: ROUND_ID, artistCorrect: true, tokenAwarded: false, state: broker.guessState }, FIRST_USER_ID);
    await expect(page.getByText("You got an artist. 1 more credited.")).toBeVisible();
    await expect(page.locator(".token-earned-drop")).toHaveCount(0);
    broker.guessState = { ...broker.guessState, correctArtistCount: 1, titleGuessed: true, titleCorrect: true, tokenEarned: true };
    broker.ownTokenCount += 1;
    await page.getByRole("textbox", { name: "Guess the title" }).fill("Sample title");
    await page.getByRole("button", { name: "Submit guess the title" }).click();
    broker.send(GUESS_RESULT_QUEUE, { roundId: ROUND_ID, titleCorrect: true, tokenAwarded: true, state: broker.guessState }, FIRST_USER_ID);
    broker.send(SESSION_TOPIC, { type: "GUESS_CORRECT", sessionId: SESSION_ID });
    await expect(page.getByText("Title and artist in. You earned a token!")).toBeVisible();
    await expect(page.getByLabel("Your tokens: 4")).toBeVisible();
    await expect(page.locator(".token-earned-drop")).toHaveCSS("animation-name", "token-earned-drop");
    await page.emulateMedia({ reducedMotion: "reduce" });
    await expect(page.locator(".token-earned-drop")).toHaveCSS("display", "none");
    await page.screenshot({ path: testInfo.outputPath("token-earned.png") });
    await page.getByRole("textbox", { name: "Guess the artist" }).fill("Wrong artist");
    await page.getByRole("button", { name: "Submit guess the artist" }).click();
    broker.guessState = { ...broker.guessState, artistGuessingClosed: true };
    broker.send(GUESS_RESULT_QUEUE, { roundId: ROUND_ID, artistCorrect: false, titleCorrect: false, tokenAwarded: false, state: broker.guessState }, FIRST_USER_ID);
    await expect(page.getByText("Wrong artist. No more artist guesses this round.")).toBeVisible();
    await expect(page.locator(".incorrect-guess-feedback")).toHaveCSS("animation-name", "none");
    await page.screenshot({ path: testInfo.outputPath("incorrect-guess.png") });
  });
  test(`${theme} desktop: two-client voice status and member departure`, async ({ browser }, testInfo) => {
    const firstContext = await browser.newContext();
    const secondContext = await browser.newContext();
    const firstPage = await firstContext.newPage();
    const secondPage = await secondContext.newPage();
    const broker = new FixtureBroker();
    try {
      await broker.fixture(firstPage, FIRST_USER_ID, theme);
      await broker.fixture(secondPage, SECOND_USER_ID, theme);
      await firstPage.goto(`/groups/${GROUP_ID}`);
      await secondPage.goto(`/groups/${GROUP_ID}`);
      await firstPage.addStyleTag({ content: "nextjs-portal { display: none; }" });
      await secondPage.addStyleTag({ content: "nextjs-portal { display: none; }" });
      await firstPage.getByRole("button", { name: "Join call", exact: true }).click();
      await secondPage.getByRole("button", { name: "Join call", exact: true }).click();
      await expect(firstPage.getByLabel("Sam, microphone on, listening, speaking")).toBeVisible();
      await expect(secondPage.getByLabel("Alex, microphone on, listening, speaking")).toBeVisible();
      await firstPage.getByRole("button", { name: "Mute", exact: true }).click();
      await firstPage.getByRole("button", { name: "Deafen", exact: true }).click();
      await expect(secondPage.getByLabel("Alex, muted, deafened")).toBeVisible();
      await expect(secondPage.locator('aside[aria-label="Voice sidebar"] img')).toBeVisible();
      await secondPage.screenshot({ path: testInfo.outputPath("voice-status.png") });
      await firstPage.getByRole("button", { name: "Mute", exact: true }).click();
      await expect(secondPage.getByLabel("Alex, microphone on, deafened, speaking")).toBeVisible();
      await firstPage.getByRole("button", { name: "Leave voice", exact: true }).click();
      await expect(secondPage.locator('.voice-member').filter({ hasText: "Alex" })).toHaveCount(0);
      await expect(firstPage.getByRole("button", { name: "Join call", exact: true })).toBeVisible();
      await firstPage.emulateMedia({ reducedMotion: "reduce" });
      await firstPage.screenshot({ path: testInfo.outputPath("voice-left.png") });
    } finally { await Promise.allSettled([firstContext.close(), secondContext.close()]); }
  });
}
