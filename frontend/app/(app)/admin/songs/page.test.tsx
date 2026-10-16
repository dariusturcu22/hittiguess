import {
  act,
  fireEvent,
  render as renderComponent,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

function render(component: React.ReactNode) {
  return renderComponent(
    <QueryClientProvider client={new QueryClient()}>
      {component}
    </QueryClientProvider>,
  );
}

import SongCatalogPage from "./page";
import {
  CATALOG_PAGE_SIZE,
  SEARCH_DEBOUNCE_MILLISECONDS,
} from "./song-catalog-presentation";

interface CatalogParams {
  query?: string;
  status?: string;
  page?: number;
  size?: number;
}

const catalogHook = vi.hoisted(() => ({
  calls: [] as CatalogParams[],
  options: [] as unknown[],
  result: {} as Record<string, unknown>,
  refetch: vi.fn(),
}));

const detailHook = vi.hoisted(() => ({
  result: {} as Record<string, unknown>,
  songIds: [] as number[],
}));

vi.mock("@/hooks/generated/admin-song-catalog/admin-song-catalog", () => ({
  useSearchCatalog: (params: CatalogParams, options: unknown) => {
    catalogHook.calls.push(params);
    catalogHook.options.push(options);
    return { ...catalogHook.result, refetch: catalogHook.refetch };
  },
  useSongDetail: (songId: number) => {
    detailHook.songIds.push(songId);
    return { ...detailHook.result, refetch: vi.fn() };
  },
}));

const COUNTS = {
  all: 60,
  verified: 40,
  needsReview: 12,
  inQueue: 5,
  processing: 2,
  error: 1,
};
const TOTAL_SONGS = 60;
const TOTAL_PAGES = 3;
const SONG_ID = 12;
const SHORT_WAIT_MILLISECONDS = 100;

const VERIFIED_SONG = {
  id: "song:12",
  songId: SONG_ID,
  youtubeId: "zimi-yt-id",
  title: "Zi-mi",
  artists: ["Loredana"],
  releaseYear: 2019,
  status: "NEEDS_REVIEW",
  pipeline: "FAST",
  createdAt: "2026-10-15T12:00:00Z",
  addedBy: "mara.k",
  addedByKind: "USER",
};

const QUEUED_ITEM = {
  id: "import:34",
  youtubeId: "pQ9sXw1LmB4",
  artists: [],
  status: "IN_QUEUE",
  pipeline: "FULL",
  createdAt: null,
  addedByKind: "ADMIN_SEED",
};

function catalogPage(items: unknown[], page = 0) {
  return {
    items,
    page,
    size: CATALOG_PAGE_SIZE,
    totalElements: TOTAL_SONGS,
    totalPages: TOTAL_PAGES,
    counts: COUNTS,
  };
}

function lastCatalogParams(): CatalogParams {
  return catalogHook.calls[catalogHook.calls.length - 1];
}

function typeSearch(text: string) {
  fireEvent.change(screen.getByLabelText("Search title or artist"), {
    target: { value: text },
  });
}

describe("SongCatalogPage", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    catalogHook.calls = [];
    catalogHook.options = [];
    catalogHook.refetch.mockReset();
    catalogHook.result = {
      data: catalogPage([VERIFIED_SONG, QUEUED_ITEM]),
      isLoading: false,
      isError: false,
    };
    detailHook.songIds = [];
    detailHook.result = { data: undefined, isLoading: true, isError: false };
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("requests the first unfiltered page and keeps previous data while typing", () => {
    render(<SongCatalogPage />);

    expect(lastCatalogParams()).toEqual({
      query: undefined,
      status: undefined,
      page: 0,
      size: CATALOG_PAGE_SIZE,
    });
    expect(catalogHook.options[0]).toEqual({
      query: { placeholderData: expect.any(Function) },
    });
    expect(
      screen.getByText(/Search runs after 3 characters and a short pause/),
    ).toBeInTheDocument();
  });

  it("renders song rows and queue items with their fallbacks", () => {
    render(<SongCatalogPage />);

    expect(screen.getByText("Zi-mi")).toBeInTheDocument();
    expect(screen.getByText("NEEDS REVIEW")).toBeInTheDocument();
    expect(screen.getByText("Fast")).toBeInTheDocument();
    expect(screen.getByText("Oct 15, 2026")).toBeInTheDocument();
    expect(screen.getByText("mara.k")).toBeInTheDocument();
    expect(screen.getByText("yt: pQ9sXw1LmB4")).toBeInTheDocument();
    expect(screen.getByText("title not resolved yet")).toBeInTheDocument();
    expect(screen.getByText("pending")).toBeInTheDocument();
    expect(screen.getByText("Unknown")).toBeInTheDocument();
    expect(screen.getByText("Admin seed")).toBeInTheDocument();
    expect(screen.getByText("Showing 1 to 2 of 60 songs")).toBeInTheDocument();
  });

  it("shows each status chip with its count", () => {
    render(<SongCatalogPage />);

    const chips = within(
      screen.getByRole("group", { name: "Filter by status" }),
    );
    expect(chips.getByRole("button", { name: "All 60" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(
      chips.getByRole("button", { name: "Verified 40" }),
    ).toBeInTheDocument();
    expect(
      chips.getByRole("button", { name: "Needs review 12" }),
    ).toBeInTheDocument();
    expect(
      chips.getByRole("button", { name: "In queue 5" }),
    ).toBeInTheDocument();
    expect(
      chips.getByRole("button", { name: "Processing 2" }),
    ).toBeInTheDocument();
    expect(chips.getByRole("button", { name: "Error 1" })).toBeInTheDocument();
  });

  it("searches only after the pause and only with at least three characters", () => {
    render(<SongCatalogPage />);

    typeSearch("zi");
    act(() => {
      vi.advanceTimersByTime(SEARCH_DEBOUNCE_MILLISECONDS);
    });
    expect(lastCatalogParams().query).toBeUndefined();

    typeSearch("  zim");
    act(() => {
      vi.advanceTimersByTime(SHORT_WAIT_MILLISECONDS);
    });
    expect(lastCatalogParams().query).toBeUndefined();

    act(() => {
      vi.advanceTimersByTime(SEARCH_DEBOUNCE_MILLISECONDS);
    });
    expect(lastCatalogParams().query).toBe("zim");
  });

  it("drops back to the unfiltered list when the search gets shorter than three characters", () => {
    render(<SongCatalogPage />);

    typeSearch("zim");
    act(() => {
      vi.advanceTimersByTime(SEARCH_DEBOUNCE_MILLISECONDS);
    });
    typeSearch("zi");
    act(() => {
      vi.advanceTimersByTime(SEARCH_DEBOUNCE_MILLISECONDS);
    });

    expect(lastCatalogParams().query).toBeUndefined();
  });

  it("filters by status and goes back to the first page", () => {
    render(<SongCatalogPage />);

    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(lastCatalogParams().page).toBe(1);

    fireEvent.click(screen.getByRole("button", { name: "Needs review 12" }));

    expect(lastCatalogParams()).toMatchObject({
      status: "NEEDS_REVIEW",
      page: 0,
    });
    expect(
      screen.getByRole("button", { name: "Needs review 12" }),
    ).toHaveAttribute("aria-pressed", "true");
  });

  it("resets to the first page when the search changes", () => {
    render(<SongCatalogPage />);

    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    typeSearch("brubeck");
    act(() => {
      vi.advanceTimersByTime(SEARCH_DEBOUNCE_MILLISECONDS);
    });

    expect(lastCatalogParams()).toMatchObject({ query: "brubeck", page: 0 });
  });

  it("pages with Previous and Next within the total pages", () => {
    render(<SongCatalogPage />);

    expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    fireEvent.click(screen.getByRole("button", { name: "Next" }));
    expect(lastCatalogParams().page).toBe(2);
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: "Previous" }));
    expect(lastCatalogParams().page).toBe(1);
  });

  it("opens the detail drawer for a song row and closes it", () => {
    detailHook.result = {
      data: {
        songId: SONG_ID,
        youtubeId: "zimi-yt-id",
        title: "Zi-mi",
        artists: ["Loredana"],
        releaseYear: 2019,
        status: "NEEDS_REVIEW",
        pipeline: "FAST",
        createdAt: "2026-10-15T12:00:00Z",
        addedBy: "mara.k",
        addedByKind: "USER",
        verificationStatus: "NEEDS_REVIEW",
        confidence: "medium",
        openReportCount: 2,
        queueHistory: [
          {
            id: 1,
            origin: "FAST_TIER_RECHECK",
            status: "FAILED",
            enqueuedAt: "2026-10-15T12:00:00Z",
            failureReason: "No usable metadata",
          },
        ],
      },
      isLoading: false,
      isError: false,
    };
    render(<SongCatalogPage />);

    fireEvent.click(screen.getByText("Zi-mi"));

    const drawer = within(screen.getByRole("dialog", { name: "Song detail" }));
    expect(detailHook.songIds).toContain(SONG_ID);
    expect(
      drawer.getByText(
        "Year 2019, fast pipeline, added Oct 15, 2026 by mara.k",
      ),
    ).toBeInTheDocument();
    expect(
      drawer.getByText(/Fast-tier import recheck, failed\. No usable metadata/),
    ).toBeInTheDocument();
    expect(drawer.getByText("medium")).toBeInTheDocument();
    expect(
      drawer.queryByText("What each source found"),
    ).not.toBeInTheDocument();

    fireEvent.click(drawer.getByRole("button", { name: "Close song detail" }));
    expect(
      screen.queryByRole("dialog", { name: "Song detail" }),
    ).not.toBeInTheDocument();
  });

  it("does not open a drawer for a queue item without a song", () => {
    render(<SongCatalogPage />);

    fireEvent.click(screen.getByText("yt: pQ9sXw1LmB4"));

    expect(
      screen.queryByRole("dialog", { name: "Song detail" }),
    ).not.toBeInTheDocument();
  });

  it("shows an empty state when nothing matches", () => {
    catalogHook.result = {
      data: { ...catalogPage([]), totalElements: 0, totalPages: 0 },
      isLoading: false,
      isError: false,
    };
    render(<SongCatalogPage />);

    expect(screen.getByText("No songs match")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
  });

  it("shows a skeleton while the first page loads", () => {
    catalogHook.result = { data: undefined, isLoading: true, isError: false };
    render(<SongCatalogPage />);

    expect(screen.getByTestId("song-catalog-skeleton")).toBeInTheDocument();
  });

  it("shows an error with a retry", () => {
    catalogHook.result = { data: undefined, isLoading: false, isError: true };
    render(<SongCatalogPage />);

    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect(
      screen.getByText("Couldn't load the song catalog."),
    ).toBeInTheDocument();
    expect(catalogHook.refetch).toHaveBeenCalled();
  });
});
