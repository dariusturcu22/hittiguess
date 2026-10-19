import type { PlayerCardDTO } from "@/hooks/models/playerCardDTO";
import type { PlayerDTO } from "@/hooks/models/playerDTO";

export const AWAITING_PLACEMENT_STATUS = "AWAITING_PLACEMENT";
export const COUNTDOWN_STATUS = "COUNTDOWN";
export const BETTING_STATUS = "BETTING";
export const REVEALED_STATUS = "REVEALED";
export const SCORED_STATUS = "SCORED";
export const LOCK_IN_COUNTDOWN_MILLISECONDS = 4_000;
export const REVEAL_HOLD_MILLISECONDS = 6_000;
export const ACTIVE_PLAYER_STATUS = "ACTIVE";
export const FIXED_DJ_MODE = "FIXED";

const LOCKED_STATUSES = new Set([COUNTDOWN_STATUS, BETTING_STATUS, REVEALED_STATUS, SCORED_STATUS]);

export function isPlacementLocked(status: string | undefined): boolean {
  return LOCKED_STATUSES.has(status ?? "");
}

export function isRoundRevealed(status: string | undefined): boolean {
  return status === REVEALED_STATUS || status === SCORED_STATUS;
}

// One entry in the rendered timeline row. Gaps between cards are addressed by the same
// 0-based insertion index the backend uses for placements and bets: gap N sits before
// the Nth card of the active player's timeline, and the last gap sits after every card.
export type TimelineEntry =
  | { kind: "card"; card: PlayerCardDTO; cardIndex: number }
  | { kind: "gap"; gapIndex: number };

export function timelineEntries(cards: PlayerCardDTO[]): TimelineEntry[] {
  const entries: TimelineEntry[] = [];
  cards.forEach((card, cardIndex) => {
    entries.push({ kind: "gap", gapIndex: cardIndex });
    entries.push({ kind: "card", card, cardIndex });
  });
  entries.push({ kind: "gap", gapIndex: cards.length });
  return entries;
}

// The gap a pointer at a horizontal position falls into, given each card's horizontal
// center in the same coordinate space, in timeline order.
export function gapIndexForPointer(pointerX: number, cardCenters: number[]): number {
  return cardCenters.filter((cardCenter) => cardCenter < pointerX).length;
}

export interface GapNeighbors {
  before?: PlayerCardDTO;
  after?: PlayerCardDTO;
}

export function gapNeighbors(cards: PlayerCardDTO[], gapIndex: number): GapNeighbors {
  const previousCardIndex = gapIndex - 1;
  return { before: cards.at(previousCardIndex >= 0 ? previousCardIndex : cards.length), after: cards.at(gapIndex) };
}

export function describeGap(cards: PlayerCardDTO[], gapIndex: number): { before?: string; beforeYear?: number; after?: string; afterYear?: number } {
  const { before, after } = gapNeighbors(cards, gapIndex);
  return { before: before?.title, beforeYear: before?.releaseYear, after: after?.title, afterYear: after?.releaseYear };
}

// How far a card leans away from the open gap while a card hovers over it: the closer
// the card, the less it tilts, so the row fans open around the gap.
const TILT_DEGREES_BY_DISTANCE = [1.5, 3, 5, 7, 9];
const SHIFT_PIXELS_BY_DISTANCE = [2, 5, 11, 18, 26];

export function cardTiltAwayFromGap(cardIndex: number, gapIndex: number): { rotateDegrees: number; shiftPixels: number } {
  const isBeforeGap = cardIndex < gapIndex;
  const distance = isBeforeGap ? gapIndex - cardIndex : cardIndex - gapIndex + 1;
  const tableIndex = Math.min(distance, TILT_DEGREES_BY_DISTANCE.length) - 1;
  const direction = isBeforeGap ? -1 : 1;
  return {
    rotateDegrees: direction * (TILT_DEGREES_BY_DISTANCE.at(tableIndex) ?? 0),
    shiftPixels: direction * (SHIFT_PIXELS_BY_DISTANCE.at(tableIndex) ?? 0),
  };
}

// The same rule the backend scores with: a year fits a gap when it's no earlier than
// the card before it and no later than the card after it, so ties count either way.
export function isGapCorrectForYear(cards: PlayerCardDTO[], gapIndex: number, releaseYear: number | undefined): boolean {
  if (releaseYear === undefined) return false;
  const previousCard = gapIndex > 0 ? cards.at(gapIndex - 1) : undefined;
  const nextCard = gapIndex < cards.length ? cards.at(gapIndex) : undefined;
  const fitsAfterPrevious = !previousCard || (previousCard.releaseYear ?? 0) <= releaseYear;
  const fitsBeforeNext = !nextCard || releaseYear <= (nextCard.releaseYear ?? 0);
  return fitsAfterPrevious && fitsBeforeNext;
}

export function isGapOpenForBet(gapIndex: number, placedPosition: number | undefined, takenPositions: number[]): boolean {
  return gapIndex !== placedPosition && !takenPositions.includes(gapIndex);
}

// Mirrors GameSessionService.advanceRound so the reveal can say who's up next before
// the next round exists: the next active player in turn order, and the DJ either fixed
// or the next player after them.
export function predictNextTurn(
  players: PlayerDTO[],
  activePlayerId: number | undefined,
  djPlayerId: number | undefined,
  djMode: string | undefined,
): { nextActive?: PlayerDTO; nextDj?: PlayerDTO } {
  const eligiblePlayers = players
    .filter((player) => player.status === ACTIVE_PLAYER_STATUS)
    .sort((first, second) => (first.turnOrder ?? 0) - (second.turnOrder ?? 0));
  const activePlayer = players.find((player) => player.id === activePlayerId);
  const nextAfter = (turnOrder: number, excludedIds: Set<number | undefined>) =>
    eligiblePlayers.find((player) => (player.turnOrder ?? 0) > turnOrder && !excludedIds.has(player.id))
    ?? eligiblePlayers.find((player) => !excludedIds.has(player.id));
  const fixedDj = djMode === FIXED_DJ_MODE ? eligiblePlayers.find((player) => player.id === djPlayerId) : undefined;
  const nextActive = nextAfter(activePlayer?.turnOrder ?? 0, new Set(fixedDj ? [fixedDj.id] : []));
  const nextDj = fixedDj ?? (nextActive ? nextAfter(nextActive.turnOrder ?? 0, new Set([nextActive.id])) : undefined);
  return { nextActive, nextDj };
}

export function secondsUntil(deadline: string | undefined, now: number): number {
  if (!deadline) return 0;
  return Math.max(0, Math.ceil((Date.parse(deadline) - now) / 1000));
}

export function fractionRemaining(deadline: string | undefined, totalMilliseconds: number, now: number): number {
  if (!deadline) return 0;
  return Math.min(1, Math.max(0, (Date.parse(deadline) - now) / totalMilliseconds));
}

// The session is purged once it ends, so the results screen takes its group from the URL.
export const RESULTS_GROUP_SEARCH_PARAM = "group";

export function resultsPath(sessionId: number, groupId: number): string {
  return `/sessions/${sessionId}/results?${RESULTS_GROUP_SEARCH_PARAM}=${groupId}`;
}
