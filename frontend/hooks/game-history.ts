"use client";

import { useQuery } from "@tanstack/react-query";
import { customInstance } from "@/lib/axios-instance";

export const HISTORY_PAGE_SIZE = 20;
export const HISTORY_QUERY_KEY = ["game-history"] as const;
export const INTERRUPTED_GAME = "INTERRUPTED";
export const CUSTOM_MODE = "CUSTOM";
export const FIRST_PLACE = 1;
export const PERCENT_SCALE = 100;

export interface GameParticipantSummary {
  id: number;
  userId: number | null;
  displayName: string;
  participationStatus: string;
  finalCardCount: number;
  cardRank: number;
  artistRank: number;
  titleRank: number;
  isWinner: boolean;
  placementAttempts: number;
  correctPlacements: number;
  titleAttempts: number;
  correctTitles: number;
  artistAttempts: number;
  correctArtists: number;
  betsPlaced: number;
  betsWon: number;
}

export interface GameSummary {
  id: number;
  groupName: string;
  startedAt: string;
  endedAt: string;
  mode: string;
  difficultyTier: "EASY" | "MEDIUM" | "HARD" | null;
  winTargetCards: number;
  participantCount: number;
  turnsPlayed: number;
  endingReason: string;
  rulesVersion: string;
  participants: GameParticipantSummary[];
}

export interface GameHistoryPage {
  items: GameSummary[];
  page: number;
  pageSize: number;
  total: number;
}

export interface PlayerHistoryStatistics {
  gamesPlayed: number;
  wins: number;
  interruptedGames: number;
  placementAttempts: number;
  correctPlacements: number;
  correctTitles: number;
  correctArtists: number;
}

export interface SongPlayObservation {
  eventId: string;
  occurredAt: string;
  songId: number;
  researchPlayerId: string | null;
  gameCorrelationId: string;
  placementOutcome: string;
  timelineCardCount: number;
  validInsertionSlotCount: number;
  titleAttempted: boolean;
  titleCorrect: boolean;
  artistAttempts: number;
  correctArtists: number;
  requestedDifficultyTier: "EASY" | "MEDIUM" | "HARD" | null;
  rulesVersion: string;
}

export function useGameHistory(page: number) {
  return useQuery({
    queryKey: [...HISTORY_QUERY_KEY, page],
    queryFn: ({ signal }) => customInstance<GameHistoryPage>({ url: "/api/users/me/history", signal,
      params: { page, pageSize: HISTORY_PAGE_SIZE } }),
  });
}

export function useGameHistoryDetail(summaryId: number) {
  return useQuery({
    queryKey: [...HISTORY_QUERY_KEY, "detail", summaryId],
    enabled: Number.isSafeInteger(summaryId) && summaryId > 0,
    queryFn: ({ signal }) => customInstance<GameSummary>({ url: `/api/users/me/history/${summaryId}`, signal }),
  });
}

export function useHistoryStatistics(enabled = true) {
  return useQuery({
    queryKey: [...HISTORY_QUERY_KEY, "statistics"],
    enabled,
    queryFn: ({ signal }) => customInstance<PlayerHistoryStatistics>({ url: "/api/users/me/statistics", signal }),
  });
}

export function gameModeLabel(summary: GameSummary) {
  if (summary.mode === CUSTOM_MODE || !summary.difficultyTier) return "Custom";
  return summary.difficultyTier.charAt(0) + summary.difficultyTier.slice(1).toLowerCase();
}

const MILLISECONDS_PER_MINUTE = 60_000;
export function gameDurationMinutes(summary: GameSummary) {
  return Math.max(0, Math.round((Date.parse(summary.endedAt) - Date.parse(summary.startedAt)) / MILLISECONDS_PER_MINUTE));
}
