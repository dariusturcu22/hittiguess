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
}));

vi.mock("@/hooks/use-bulk-import-realtime", () => ({
  useBulkImportRealtime: () => ({ events: [], isConnected: true, reset: vi.fn() }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock("sonner", () => ({
  toast: { loading: vi.fn(), success: vi.fn(), error: vi.fn() },
}));

async function renderPage() {
  const queryClient = new QueryClient();
  await act(async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <ImportYoutubePage params={YOUTUBE_PARAMS} />
      </QueryClientProvider>,
    );
  });
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
  });

  it("starts a background job and returns to the playlist", async () => {
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
    expect(routerPush).toHaveBeenCalledWith("/playlists/7");
  });
});
