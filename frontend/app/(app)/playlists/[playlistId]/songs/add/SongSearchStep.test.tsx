import { fireEvent, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SongSearchStep } from "./SongSearchStep";

const fetchNextPage = vi.fn();
const toggleQueued = vi.fn();

const recommendedSong = {
  id: 31,
  title: "Recommended One",
  artists: [{ name: "Artist One" }],
  releaseYear: 1999,
};

const searchHit = {
  id: 32,
  title: "Searched Hit",
  artists: [{ name: "Artist Two" }],
  releaseYear: 2001,
};

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={typeof href === "string" ? href : "#"}>{children}</a>
  ),
}));

vi.mock("@/hooks/generated/song-search/song-search", () => ({
  useSearchSongs: ({ query }: { query: string }) =>
    query.trim().length >= 2
      ? { data: [searchHit], isLoading: false }
      : { data: undefined, isLoading: false },
}));

vi.mock("@/hooks/use-recommended-songs", () => ({
  useRecommendedSongs: () => ({
    data: { pages: [{ songs: [recommendedSong], hasMore: true }], pageParams: [0] },
    isLoading: false,
    fetchNextPage,
    hasNextPage: true,
    isFetchingNextPage: false,
  }),
}));

describe("SongSearchStep recommendations", () => {
  beforeEach(() => {
    fetchNextPage.mockReset();
    toggleQueued.mockReset();
  });

  function renderStep() {
    return render(
      <SongSearchStep
        backPath="/playlists/7"
        queue={[]}
        onToggleQueued={toggleQueued}
        onSubmitQueue={vi.fn()}
        isSubmitting={false}
        onStartNewSong={vi.fn()}
      />,
    );
  }

  it("shows recommendations by default with a fetch-more action", () => {
    renderStep();

    expect(screen.getByText("Recommended One")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Fetch more recommendations" }));

    expect(fetchNextPage).toHaveBeenCalledOnce();
  });

  it("queues a recommended song and searches on input", () => {
    renderStep();

    fireEvent.click(screen.getByTitle("Add to queue"));

    expect(toggleQueued).toHaveBeenCalledWith(recommendedSong);

    fireEvent.change(screen.getByPlaceholderText("Search by title or artist..."), {
      target: { value: "hit" },
    });

    expect(screen.getByText("Searched Hit")).toBeVisible();
    expect(screen.queryByText("Recommended One")).toBeNull();
  });
});
