"use client";

import { useInfiniteQuery } from "@tanstack/react-query";

import { customInstance } from "@/lib/axios-instance";
import type { SongDTO } from "@/hooks/models";

export interface RecommendedSongsResult {
  songs: SongDTO[];
  hasMore: boolean;
}

const FIRST_PAGE = 0;

// Hand-written until the next orval run can generate it from the live backend:
// orval reads http://localhost:8080/v3/api-docs, unreachable without a running
// stack, so this mirrors the generated query shapes exactly to make the later
// swap mechanical.
export function useRecommendedSongs(size = 20) {
  return useInfiniteQuery({
    queryKey: ["/api/songs/recommended", size],
    queryFn: ({ pageParam }) =>
      customInstance<RecommendedSongsResult>({
        url: "/api/songs/recommended",
        method: "GET",
        params: { page: pageParam, size },
      }),
    initialPageParam: FIRST_PAGE,
    getNextPageParam: (lastPage, allPages) => (lastPage.hasMore ? allPages.length : undefined),
  });
}
