"use client";

import { useMutation, type UseMutationOptions } from "@tanstack/react-query";

import { customInstance } from "@/lib/axios-instance";
import type { GroupDetailDTO } from "@/hooks/models/groupDetailDTO";

export type DifficultyTier = "EASY" | "MEDIUM" | "HARD";

export interface GeneratedSongPreviewDTO {
  id: number;
  title: string;
  artists: string[];
  releaseYear: number;
}

export interface GenerateDifficultySetRequest {
  tier: DifficultyTier;
  targetCardCount: number;
}

export interface StartSessionWithSongsRequest {
  songIds: number[];
}

export interface StartCustomSessionRequest {
  playlistId?: number;
  playlistLink?: string;
}

// Hand-written until the next orval run can generate these from the live backend:
// orval reads http://localhost:8080/v3/api-docs, unreachable without a running stack,
// so these mirror the generated mutation shapes (path params plus a data body) exactly
// to make the later swap mechanical.
export function useGenerateDifficultySet(
  options?: UseMutationOptions<
    GeneratedSongPreviewDTO[],
    unknown,
    { groupId: number; data: GenerateDifficultySetRequest }
  >,
) {
  return useMutation({
    ...options,
    mutationFn: ({ groupId, data }: { groupId: number; data: GenerateDifficultySetRequest }) =>
      customInstance<GeneratedSongPreviewDTO[]>({
        url: `/api/groups/${groupId}/session/generate`,
        method: "POST",
        data,
      }),
  });
}

export function useStartSessionWithSongs(
  options?: UseMutationOptions<
    GroupDetailDTO,
    unknown,
    { groupId: number; data: StartSessionWithSongsRequest }
  >,
) {
  return useMutation({
    ...options,
    mutationFn: ({ groupId, data }: { groupId: number; data: StartSessionWithSongsRequest }) =>
      customInstance<GroupDetailDTO>({
        url: `/api/groups/${groupId}/session/start-with-songs`,
        method: "POST",
        data,
      }),
  });
}

export function useStartCustomSession(
  options?: UseMutationOptions<
    GroupDetailDTO,
    unknown,
    { groupId: number; data: StartCustomSessionRequest }
  >,
) {
  return useMutation({
    ...options,
    mutationFn: ({ groupId, data }: { groupId: number; data: StartCustomSessionRequest }) =>
      customInstance<GroupDetailDTO>({
        url: `/api/groups/${groupId}/session/start-custom`,
        method: "POST",
        data,
      }),
  });
}
