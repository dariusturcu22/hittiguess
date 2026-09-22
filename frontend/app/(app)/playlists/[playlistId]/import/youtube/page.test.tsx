import { act, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

import ImportYoutubePage from "./page";

const YOUTUBE_PARAMS = Promise.resolve({ playlistId: "7" });
const expandMutate = vi.fn();
const importMutate = vi.fn();

vi.mock("@/hooks/generated/bulk-import/bulk-import", () => ({
  useExpandPlaylist: () => ({ mutate: expandMutate, isPending: false, isError: false }),
  useImportImmediately: () => ({
    mutate: importMutate,
    isPending: false,
    isError: false,
    data: undefined,
  }),
}));

vi.mock("@/hooks/use-bulk-import-realtime", () => ({
  useBulkImportRealtime: () => ({ events: [], isConnected: true, reset: vi.fn() }),
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

describe("ImportYoutubePage expand review", () => {
  beforeEach(() => {
    expandMutate.mockReset();
    importMutate.mockReset();
    expandMutate.mockImplementation((_args, options) =>
      options?.onSuccess?.(["video-1", "video-2"]),
    );
  });

  it("reviews expanded videos before importing", async () => {
    await renderPage();

    fireEvent.change(screen.getByLabelText("YouTube playlist link"), {
      target: { value: "https://youtube.com/playlist?list=abc" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Fetch playlist" }));

    expect(expandMutate).toHaveBeenCalledWith(
      { data: { playlistLink: "https://youtube.com/playlist?list=abc" } },
      expect.anything(),
    );
    expect(await screen.findByText("2 songs found. Import them?")).toBeVisible();

    fireEvent.click(screen.getByRole("button", { name: "Import 2 songs" }));

    expect(importMutate).toHaveBeenCalledWith(
      {
        data: {
          videoIdsOrLinks: ["video-1", "video-2"],
          targetPlaylistId: 7,
          importJobId: expect.any(String),
        },
      },
      expect.anything(),
    );
    expect(importMutate).not.toHaveBeenCalledBefore(expandMutate);
  });
});
