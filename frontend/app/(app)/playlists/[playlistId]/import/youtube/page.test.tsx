import { act, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ImportYoutubePage from "./page";

const YOUTUBE_PARAMS = Promise.resolve({ playlistId: "7" });
const expandMutate = vi.fn();
const startImportMutate = vi.fn();
const routerPush = vi.fn();

vi.mock("@/hooks/generated/bulk-import/bulk-import", () => ({
  useExpandPlaylist: () => ({ mutate: expandMutate, isPending: false, isError: false }),
}));

vi.mock("@/hooks/generated/playlist-import-jobs/playlist-import-jobs", () => ({
  useStartImport: () => ({ mutate: startImportMutate, isPending: false, isError: false }),
  useActiveImport: () => activeImportState,
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  useGetPlaylist: () => ({ data: { name: "Late Night Coding Mix" } }),
}));

interface ActiveImportItem {
  youtubeId: string;
  status: string;
  songId?: number;
  rawTitle?: string;
  rawChannelTitle?: string;
  resolvedTitle?: string;
  resolvedArtists?: string;
  resolvedReleaseYear?: number;
  resolvedColor?: string;
}

let activeImportState: {
  data?: { items: ActiveImportItem[] };
  isError: boolean;
  error?: unknown;
} = {
  isError: false,
  error: null,
  data: {
    items: [
      {
        youtubeId: "video-1",
        status: "RESOLVED",
        songId: 101,
        resolvedTitle: "Chasing Cars",
        resolvedArtists: "Snow Patrol",
        resolvedReleaseYear: 2006,
        resolvedColor: "cba6f7",
      },
      { youtubeId: "video-2", status: "PENDING", rawTitle: "midnight drive (official video)", rawChannelTitle: "Nocturne Records" },
    ],
  },
};

function notFoundError(): unknown {
  return { response: { status: 404 } };
}

function transientError(): unknown {
  return { message: "Network Error" };
}

vi.mock("@/hooks/use-bulk-import-realtime", () => ({
  useBulkImportRealtime: () => ({ events: [], isConnected: false, reset: vi.fn() }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock("sonner", () => ({
  toast: { loading: vi.fn(), success: vi.fn(), error: vi.fn() },
}));

async function renderPage() {
  const queryClient = new QueryClient();
  let renderResult: { unmount: () => void } | undefined;
  await act(async () => {
    renderResult = render(
      <QueryClientProvider client={queryClient}>
        <ImportYoutubePage params={YOUTUBE_PARAMS} />
      </QueryClientProvider>,
    );
  });
  return renderResult!;
}

describe("ImportYoutubePage background import", () => {
  beforeEach(() => {
    expandMutate.mockReset();
    startImportMutate.mockReset();
    routerPush.mockReset();
    window.localStorage.clear();
    expandMutate.mockImplementation((_args, options) =>
      options?.onSuccess?.(["video-1", "video-2"]),
    );
    startImportMutate.mockImplementation((_args, options) =>
      options?.onSuccess?.({ importJobId: "job-1" }),
    );
    activeImportState = {
      isError: false,
      error: null,
      data: {
        items: [
          {
            youtubeId: "video-1",
            status: "RESOLVED",
            songId: 101,
            resolvedTitle: "Chasing Cars",
            resolvedArtists: "Snow Patrol",
            resolvedReleaseYear: 2006,
            resolvedColor: "cba6f7",
          },
          { youtubeId: "video-2", status: "PENDING", rawTitle: "midnight drive (official video)", rawChannelTitle: "Nocturne Records" },
        ],
      },
    };
  });

  it("starts a background job and shows progress instead of redirecting", async () => {
    await renderPage();

    fireEvent.change(screen.getByLabelText("YouTube playlist link"), {
      target: { value: "https://youtube.com/playlist?list=abc" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Fetch playlist" }));

    expect(await screen.findByText("2 songs found. Import them?")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Import 2 songs" }));

    expect(startImportMutate).toHaveBeenCalledWith(
      {
        playlistId: 7,
        data: { videoIdsOrLinks: ["video-1", "video-2"] },
      },
      expect.anything(),
    );
    expect(window.localStorage.getItem("hittiguess-active-playlist-import")).toBe(
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    expect(routerPush).not.toHaveBeenCalled();

    expect(await screen.findByText("1 of 2 processed")).toBeVisible();
    expect(screen.getByText("Chasing Cars")).toBeVisible();
    expect(screen.getByText("Waiting")).toBeVisible();
    expect(
      screen.getByText("You can close this page. The import keeps running in the background."),
    ).toBeVisible();
  });

  it("separates songs being worked on from those still waiting", async () => {
    activeImportState = {
      isError: false,
      error: null,
      data: {
        items: [
          { youtubeId: "video-1", status: "IDENTIFYING", rawTitle: "first upload" },
          { youtubeId: "video-2", status: "DATING", rawTitle: "second upload" },
          { youtubeId: "video-3", status: "PENDING", rawTitle: "third upload" },
          { youtubeId: "video-4", status: "RESOLVED", resolvedTitle: "Chasing Cars", resolvedArtists: "Snow Patrol", resolvedReleaseYear: 2006 },
        ],
      },
    };
    window.localStorage.setItem("hittiguess-active-playlist-import", JSON.stringify({ importJobId: "job-1", playlistId: 7 }));
    await renderPage();

    expect(await screen.findByText("Identifying the song...")).toBeVisible();
    expect(screen.getByText("Finding the year...")).toBeVisible();
    expect(screen.getByText("Waiting")).toBeVisible();
    expect(screen.getByText("2 working")).toBeVisible();
    expect(screen.getByText("1 waiting")).toBeVisible();
    expect(screen.getByText("1 added")).toBeVisible();
    expect(screen.getByText("1 of 4 processed")).toBeVisible();
  });

  it("shows the done state with a path back once every song finishes", async () => {
    activeImportState = {
      isError: false,
      error: null,
      data: {
        items: [
          {
            youtubeId: "video-1",
            status: "RESOLVED",
            songId: 101,
            resolvedTitle: "Chasing Cars",
            resolvedArtists: "Snow Patrol",
            resolvedReleaseYear: 2006,
            resolvedColor: "cba6f7",
          },
          { youtubeId: "video-2", status: "ALREADY_KNOWN", songId: 102 },
        ],
      },
    };
    await renderPage();

    fireEvent.change(screen.getByLabelText("YouTube playlist link"), {
      target: { value: "https://youtube.com/playlist?list=abc" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Fetch playlist" }));
    fireEvent.click(await screen.findByRole("button", { name: "Import 2 songs" }));

    expect(await screen.findByText("2 of 2 added")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "View playlist" }));
    expect(routerPush).toHaveBeenCalledWith("/playlists/7");
  });

  it("resumes the progress view from a stored job without starting over", async () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    await renderPage();

    expect(await screen.findByText("1 of 2 processed")).toBeVisible();
    expect(screen.queryByLabelText("YouTube playlist link")).toBeNull();
    expect(startImportMutate).not.toHaveBeenCalled();
  });

  it("ignores a stored job for a different playlist", async () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-9", playlistId: 9 }),
    );
    await renderPage();

    expect(screen.getByLabelText("YouTube playlist link")).toBeVisible();
    expect(screen.queryByText("1 of 2 processed")).toBeNull();
  });

  it("treats a missing job as complete but keeps retrying transient errors", async () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    activeImportState = { isError: true, error: transientError(), data: undefined };
    const renderResult = await renderPage();

    expect(await screen.findByText("Connection hiccup. Retrying...")).toBeVisible();
    expect(screen.queryByText(/added$/)).toBeNull();
    renderResult.unmount();

    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    activeImportState = { isError: true, error: notFoundError(), data: undefined };
    await renderPage();

    expect(await screen.findByText("0 of 0 added")).toBeVisible();
  });

  it("shows the playlist name in the header once the import is running", async () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    await renderPage();

    expect(screen.getByText("Importing playlist")).toBeVisible();
    expect(screen.getByText(/Late Night Coding Mix/)).toBeVisible();
  });

  it("shows a resolved item's title, artist, and release year with a checkmark", async () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    await renderPage();

    expect(await screen.findByText("Chasing Cars")).toBeVisible();
    expect(screen.getByText("Snow Patrol")).toBeVisible();
    expect(screen.getByText("2006")).toBeVisible();
  });

  it("shows a still-processing item's raw video title and channel while fetching", async () => {
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    await renderPage();

    expect(await screen.findByText("midnight drive (official video)")).toBeVisible();
    expect(screen.getByText("Uploaded by Nocturne Records")).toBeVisible();
  });

  it("falls back to the youtube id when a pending item has no raw title yet", async () => {
    activeImportState = {
      isError: false,
      error: null,
      data: { items: [{ youtubeId: "video-3", status: "PENDING" }] },
    };
    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    await renderPage();

    expect(await screen.findByText("video-3")).toBeVisible();
  });
  async function fetchAndImport() {
    fireEvent.change(screen.getByLabelText("YouTube playlist link"), {
      target: { value: "https://youtube.com/playlist?list=abc" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Fetch playlist" }));
    fireEvent.click(await screen.findByRole("button", { name: "Import 2 songs" }));
  }

  it("shows the server's reason when the daily new-song limit refuses the import", async () => {
    const quotaMessage = "This import has 2 songs that aren't in the catalog yet, and only 1 more can be looked up today.";
    startImportMutate.mockImplementation((_args, options) =>
      options?.onError?.({ response: { status: 429, data: { message: quotaMessage } } }),
    );
    await renderPage();

    await fetchAndImport();

    expect(await screen.findByText(quotaMessage)).toBeVisible();
    expect(screen.queryByText("Importing playlist")).not.toBeInTheDocument();
  });

  it("falls back to a generic message when the import fails without an explanation", async () => {
    startImportMutate.mockImplementation((_args, options) =>
      options?.onError?.({ response: { status: 500, data: { message: "internal detail" } } }),
    );
    await renderPage();

    await fetchAndImport();

    expect(await screen.findByText("Import failed to start. Check the link and try again.")).toBeVisible();
    expect(screen.queryByText("internal detail")).not.toBeInTheDocument();
  });
});
