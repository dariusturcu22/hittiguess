import type { GroupDetailDTO } from "@/hooks/models/groupDetailDTO";
import type { MemberDTO } from "@/hooks/models/memberDTO";

// A share passes once its level stays above the threshold for half a second without a
// break. The threshold is a root-mean-square amplitude of about -40 dBFS: quiet music
// clears it, a silent share or a hiss floor does not.
export const AUDIO_TEST_LEVEL_THRESHOLD = 0.01;
export const AUDIO_TEST_REQUIRED_MILLISECONDS = 500;
// A share that has carried no sound for this long is reported as silent, so the host sees
// it; the test keeps listening and can still pass afterwards.
export const SILENT_SHARE_TIMEOUT_MILLISECONDS = 20_000;
export const AUDIO_METER_BAR_COUNT = 32;

const METER_FLOOR_DECIBELS = -60;
const DECIBELS_PER_AMPLITUDE_DECADE = 20;

export const OK_STATUS = "OK";
export const TESTING_STATUS = "TESTING";
export const NOT_SHARED_STATUS = "NOT_SHARED";
export const FAILED_STATUS = "FAILED";
export const CANNOT_SHARE_STATUS = "CANNOT_SHARE";
export const AUDIO_CHECK_GROUP_STATUS = "AUDIO_CHECK";
export const FIXED_DJ_MODE = "FIXED";

export type AudioShareStatus = typeof OK_STATUS | typeof TESTING_STATUS | typeof NOT_SHARED_STATUS | typeof FAILED_STATUS | typeof CANNOT_SHARE_STATUS;
export type ReportableAudioShareStatus = Exclude<AudioShareStatus, typeof CANNOT_SHARE_STATUS>;

export const BROWSER_FIX_MESSAGE = "Open the game in Chrome or Edge on a Windows computer or a Mac.";

export interface AudioTestState {
  aboveSinceMilliseconds: number | null;
  hasPassed: boolean;
}

export const INITIAL_AUDIO_TEST_STATE: AudioTestState = { aboveSinceMilliseconds: null, hasPassed: false };

export function rootMeanSquare(samples: Float32Array): number {
  if (samples.length === 0) return 0;
  let sumOfSquares = 0;
  for (const sample of samples) sumOfSquares += sample * sample;
  return Math.sqrt(sumOfSquares / samples.length);
}

// One analyser frame folded into the test. Passing is final: a share that once carried
// sound for long enough stays passed even when the song goes quiet afterwards.
export function advanceAudioTest(state: AudioTestState, level: number, nowMilliseconds: number): AudioTestState {
  if (state.hasPassed) return state;
  if (level < AUDIO_TEST_LEVEL_THRESHOLD) return INITIAL_AUDIO_TEST_STATE;
  const aboveSinceMilliseconds = state.aboveSinceMilliseconds ?? nowMilliseconds;
  return {
    aboveSinceMilliseconds,
    hasPassed: nowMilliseconds - aboveSinceMilliseconds >= AUDIO_TEST_REQUIRED_MILLISECONDS,
  };
}

// The meter's fill from 0 to 1, on a decibel scale so quiet sound still moves it.
export function meterFill(level: number): number {
  if (level <= 0) return 0;
  const decibels = DECIBELS_PER_AMPLITUDE_DECADE * Math.log10(level);
  return Math.min(1, Math.max(0, (decibels - METER_FLOOR_DECIBELS) / -METER_FLOOR_DECIBELS));
}

export function memberAudioStatus(member: MemberDTO | undefined): AudioShareStatus {
  return (member?.audioCheckStatus as AudioShareStatus | undefined) ?? NOT_SHARED_STATUS;
}

export type AudioCheckView = "nobody-can-share" | "own-card" | "fixed" | "list";

// Which of the designed screens a member sees. A member who still has to share sees their
// own card; in a fixed game everyone else sees the DJ; otherwise the group list, which is
// also where a member whose browser cannot share sees the fix.
export function audioCheckView(group: GroupDetailDTO, currentMemberId: number | undefined): AudioCheckView {
  if (group.audioCheck?.nobodyCanShare) return "nobody-can-share";
  const requiredMemberIds = group.audioCheck?.requiredMemberIds ?? [];
  const currentMember = group.members?.find((member) => member.id === currentMemberId);
  const currentStatus = memberAudioStatus(currentMember);
  const isRequired = currentMemberId !== undefined && requiredMemberIds.includes(currentMemberId);
  if (isRequired && currentStatus !== OK_STATUS && currentStatus !== CANNOT_SHARE_STATUS) return "own-card";
  if (group.djMode === FIXED_DJ_MODE) return "fixed";
  return "list";
}

export function requiredMembers(group: GroupDetailDTO): MemberDTO[] {
  const requiredMemberIds = group.audioCheck?.requiredMemberIds ?? [];
  return requiredMemberIds.flatMap((memberId) => group.members?.filter((member) => member.id === memberId) ?? []);
}

export function connectedMembersByJoinTime(group: GroupDetailDTO): MemberDTO[] {
  return [...(group.members ?? [])]
    .filter((member) => member.isConnected !== false)
    .sort((first, second) => Date.parse(first.joinedAt ?? "") - Date.parse(second.joinedAt ?? ""));
}

// Members the host can make the fixed DJ: connected, with a share that passed.
export function fixedDjCandidates(group: GroupDetailDTO): MemberDTO[] {
  return connectedMembersByJoinTime(group).filter((member) => memberAudioStatus(member) === OK_STATUS);
}
