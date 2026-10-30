import { describe, expect, it } from "vitest";

import type { GroupDetailDTO } from "@/hooks/models/groupDetailDTO";
import type { MemberDTO } from "@/hooks/models/memberDTO";
import {
  advanceAudioTest,
  AUDIO_TEST_LEVEL_THRESHOLD,
  AUDIO_TEST_REQUIRED_MILLISECONDS,
  audioCheckView,
  fixedDjCandidates,
  INITIAL_AUDIO_TEST_STATE,
  meterFill,
  rootMeanSquare,
} from "./audio-check";

const LOUD_LEVEL = AUDIO_TEST_LEVEL_THRESHOLD * 10;
const QUIET_LEVEL = AUDIO_TEST_LEVEL_THRESHOLD / 2;
const FRAME_MILLISECONDS = 50;

function runFrames(levels: number[]) {
  return levels.reduce((state, level, frameIndex) => advanceAudioTest(state, level, frameIndex * FRAME_MILLISECONDS), INITIAL_AUDIO_TEST_STATE);
}

function framesFor(milliseconds: number, level: number): number[] {
  return Array.from({ length: milliseconds / FRAME_MILLISECONDS + 1 }, () => level);
}

describe("advanceAudioTest", () => {
  it("passes once the level has stayed above the threshold for half a second", () => {
    expect(runFrames(framesFor(AUDIO_TEST_REQUIRED_MILLISECONDS, LOUD_LEVEL)).hasPassed).toBe(true);
  });

  it("does not pass on a shorter burst of sound", () => {
    expect(runFrames(framesFor(AUDIO_TEST_REQUIRED_MILLISECONDS - FRAME_MILLISECONDS, LOUD_LEVEL)).hasPassed).toBe(false);
  });

  it("never passes on a share below the threshold", () => {
    expect(runFrames(framesFor(10_000, QUIET_LEVEL)).hasPassed).toBe(false);
  });

  it("starts over when the level drops below the threshold", () => {
    const halfway = framesFor(AUDIO_TEST_REQUIRED_MILLISECONDS / 2, LOUD_LEVEL);
    expect(runFrames([...halfway, QUIET_LEVEL, ...halfway]).hasPassed).toBe(false);
  });

  it("stays passed once passed", () => {
    const passed = runFrames(framesFor(AUDIO_TEST_REQUIRED_MILLISECONDS, LOUD_LEVEL));
    expect(advanceAudioTest(passed, 0, 99_999).hasPassed).toBe(true);
  });
});

describe("rootMeanSquare and meterFill", () => {
  it("measures a square wave at its amplitude", () => {
    expect(rootMeanSquare(new Float32Array([0.5, -0.5, 0.5, -0.5]))).toBeCloseTo(0.5);
    expect(rootMeanSquare(new Float32Array())).toBe(0);
  });

  it("maps silence to an empty meter and full scale to a full one", () => {
    expect(meterFill(0)).toBe(0);
    expect(meterFill(1)).toBe(1);
    expect(meterFill(0.01)).toBeGreaterThan(0);
    expect(meterFill(0.01)).toBeLessThan(1);
  });
});

function member(id: number, status: string, overrides: Record<string, unknown> = {}): MemberDTO {
  return { id, userId: id + 100, displayName: `Player ${id}`, isConnected: true, joinedAt: `2026-10-30T10:00:0${id}Z`, audioCheckStatus: status, ...overrides } as MemberDTO;
}

function group(overrides: Partial<GroupDetailDTO>): GroupDetailDTO {
  return { id: 1, status: "AUDIO_CHECK", djMode: "ROTATING", members: [], audioCheck: { requiredMemberIds: [], readyCount: 0, canStart: false, nobodyCanShare: false }, ...overrides } as GroupDetailDTO;
}

describe("audioCheckView", () => {
  it("shows a required member who has not passed their own card", () => {
    const rotating = group({ members: [member(1, "TESTING"), member(2, "OK")], audioCheck: { requiredMemberIds: [1, 2], readyCount: 1 } });
    expect(audioCheckView(rotating, 1)).toBe("own-card");
    expect(audioCheckView(rotating, 2)).toBe("list");
  });

  it("shows a member whose browser cannot share the list with the fix", () => {
    const rotating = group({ members: [member(1, "CANNOT_SHARE"), member(2, "OK")], audioCheck: { requiredMemberIds: [1, 2], readyCount: 1 } });
    expect(audioCheckView(rotating, 1)).toBe("list");
  });

  it("shows everyone but the fixed DJ the waiting screen", () => {
    const fixed = group({ djMode: "FIXED", members: [member(1, "NOT_SHARED"), member(2, "TESTING")], audioCheck: { requiredMemberIds: [2], readyCount: 0 } });
    expect(audioCheckView(fixed, 1)).toBe("fixed");
    expect(audioCheckView(fixed, 2)).toBe("own-card");
  });

  it("says so when nobody can share", () => {
    const nobody = group({ members: [member(1, "CANNOT_SHARE"), member(2, "CANNOT_SHARE")], audioCheck: { requiredMemberIds: [1, 2], nobodyCanShare: true } });
    expect(audioCheckView(nobody, 1)).toBe("nobody-can-share");
  });
});

describe("fixedDjCandidates", () => {
  it("offers only connected members whose share passed", () => {
    const rotating = group({ members: [member(1, "OK"), member(2, "TESTING"), member(3, "OK", { isConnected: false })] });
    expect(fixedDjCandidates(rotating).map((candidate) => candidate.id)).toEqual([1]);
  });
});
