"use client";

import { useInfiniteQuery } from "@tanstack/react-query";

import {
  getRecommendSongsQueryKey,
  recommendSongs,
} from "@/hooks/generated/song-search/song-search";

const FIRST_PAGE = 0;

// The generated client emits single-page queries only, so fetch-more
// accumulation lives in this thin infinite wrapper over its fetcher.
export function useRecommendedSongs(size = 20) {
  return useInfiniteQuery({
    queryKey: [...getRecommendSongsQueryKey(), size],
    queryFn: ({ pageParam }) => recommendSongs({ page: pageParam, size }),
    initialPageParam: FIRST_PAGE,
    getNextPageParam: (lastPage, allPages) => (lastPage.hasMore === true ? allPages.length : undefined),
  });
}
