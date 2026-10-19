import { describe, expect, it } from "vitest";

import {
  cardTiltAwayFromGap,
  describeGap,
  gapIndexForPointer,
  isGapCorrectForYear,
  isGapOpenForBet,
  predictNextTurn,
  timelineEntries,
} from "./gameplay-round";

const CARDS = [
  { songId: 1, title: "Torn", releaseYear: 1998 },
  { songId: 2, title: "Hey Ya!", releaseYear: 2003 },
  { songId: 3, title: "Rolling in the Deep", releaseYear: 2011 },
];

const PLAYERS = [
  { id: 1, displayName: "Ana", turnOrder: 0, status: "ACTIVE" },
  { id: 2, displayName: "Ben", turnOrder: 1, status: "ACTIVE" },
  { id: 3, displayName: "Cy", turnOrder: 2, status: "LEFT" },
  { id: 4, displayName: "Di", turnOrder: 3, status: "ACTIVE" },
] as const;

describe("timelineEntries", () => {
  it("puts a gap before, between, and after every card", () => {
    const entries = timelineEntries(CARDS);

    expect(entries.filter((entry) => entry.kind === "gap").map((entry) => entry.kind === "gap" && entry.gapIndex)).toEqual([0, 1, 2, 3]);
    expect(entries.filter((entry) => entry.kind === "card")).toHaveLength(CARDS.length);
  });
});

describe("gapIndexForPointer", () => {
  it("counts the cards whose center sits left of the pointer", () => {
    expect(gapIndexForPointer(50, [100, 300, 500])).toBe(0);
    expect(gapIndexForPointer(301, [100, 300, 500])).toBe(2);
    expect(gapIndexForPointer(900, [100, 300, 500])).toBe(3);
  });
});

describe("describeGap", () => {
  it("names both neighbors of an inner gap and one neighbor at either end", () => {
    expect(describeGap(CARDS, 1)).toEqual({ before: "Torn", beforeYear: 1998, after: "Hey Ya!", afterYear: 2003 });
    expect(describeGap(CARDS, 0)).toEqual({ before: undefined, beforeYear: undefined, after: "Torn", afterYear: 1998 });
    expect(describeGap(CARDS, 3)).toEqual({ before: "Rolling in the Deep", beforeYear: 2011, after: undefined, afterYear: undefined });
  });
});

describe("cardTiltAwayFromGap", () => {
  it("leans cards away from the gap, more the further they are", () => {
    const adjacentLeft = cardTiltAwayFromGap(1, 2);
    const farLeft = cardTiltAwayFromGap(0, 2);
    const adjacentRight = cardTiltAwayFromGap(2, 2);

    expect(adjacentLeft.rotateDegrees).toBeLessThan(0);
    expect(Math.abs(farLeft.rotateDegrees)).toBeGreaterThan(Math.abs(adjacentLeft.rotateDegrees));
    expect(adjacentRight.rotateDegrees).toBeGreaterThan(0);
  });
});

describe("isGapCorrectForYear", () => {
  it("accepts a year between its neighbors and counts ties either way", () => {
    expect(isGapCorrectForYear(CARDS, 2, 2005)).toBe(true);
    expect(isGapCorrectForYear(CARDS, 1, 2003)).toBe(true);
    expect(isGapCorrectForYear(CARDS, 2, 2003)).toBe(true);
    expect(isGapCorrectForYear(CARDS, 0, 2005)).toBe(false);
    expect(isGapCorrectForYear(CARDS, 3, 2020)).toBe(true);
  });
});

describe("isGapOpenForBet", () => {
  it("rejects the active player's gap and gaps another bettor already took", () => {
    expect(isGapOpenForBet(1, 1, [])).toBe(false);
    expect(isGapOpenForBet(2, 1, [2])).toBe(false);
    expect(isGapOpenForBet(0, 1, [2])).toBe(true);
  });
});

describe("predictNextTurn", () => {
  it("rotates the turn and the DJ past players who left", () => {
    const { nextActive, nextDj } = predictNextTurn([...PLAYERS], 2, 1, "ROTATING");

    expect(nextActive?.displayName).toBe("Di");
    expect(nextDj?.displayName).toBe("Ana");
  });

  it("keeps a fixed DJ and skips them for the turn", () => {
    const { nextActive, nextDj } = predictNextTurn([...PLAYERS], 4, 2, "FIXED");

    expect(nextActive?.displayName).toBe("Ana");
    expect(nextDj?.displayName).toBe("Ben");
  });
});
