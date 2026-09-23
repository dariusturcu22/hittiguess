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

let activeImportState: {
  data?: { items: Array<{ youtubeId: string; status: string }> };
  isError: boolean;
  error?: unknown;
} = {
  isError: false,
  error: null,
  data: {
    items: [
      { youtubeId: "video-1", status: "RESOLVED" },
      { youtubeId: "video-2", status: "PENDING" },
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
          { youtubeId: "video-1", status: "RESOLVED" },
          { youtubeId: "video-2", status: "PENDING" },
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
    expect(screen.getByText("Added")).toBeVisible();
    expect(screen.getByText("Resolving...")).toBeVisible();
    expect(
      screen.getByText("You can close this page. The import keeps running in the background."),
    ).toBeVisible();
  });

  it("shows the done state with a path back once every song finishes", async () => {
    activeImportState = {
      isError: false,
      error: null,
      data: {
        items: [
          { youtubeId: "video-1", status: "RESOLVED" },
          { youtubeId: "video-2", status: "ALREADY_KNOWN" },
        ],
      },
    };
    await renderPage();

    fireEvent.change(screen.getByLabelText("YouTube playlist link"), {
      target: { value: "https://youtube.com/playlist?list=abc" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Fetch playlist" }));
    fireEvent.click(await screen.findByRole("button", { name: "Import 2 songs" }));

    expect(await screen.findByText("Import complete. 2 songs added.")).toBeVisible();

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
    expect(screen.queryByText(/Import complete/)).toBeNull();
    renderResult.unmount();

    window.localStorage.setItem(
      "hittiguess-active-playlist-import",
      JSON.stringify({ importJobId: "job-1", playlistId: 7 }),
    );
    activeImportState = { isError: true, error: notFoundError(), data: undefined };
    await renderPage();

    expect(await screen.findByText("Import complete. 0 songs added.")).toBeVisible();
  });
});
