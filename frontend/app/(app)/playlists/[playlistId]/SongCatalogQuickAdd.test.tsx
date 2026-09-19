import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import { SongDTO } from "@/hooks/models";
import { SongCatalogQuickAdd } from "./SongCatalogQuickAdd";

const mockCreateSong = vi.fn();

const searchResult: SongDTO = {
  id: 7,
  artists: [{ name: "Catalog Artist" }],
  title: "Catalog Song",
  releaseYear: 2001,
  youtubeId: "xyz789",
  verificationStatus: "VERIFIED",
  needsUserAttention: false,
};

vi.mock("@/hooks/generated/song-search/song-search", () => ({
  useSearchSongs: () => ({ data: [searchResult], isLoading: false }),
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  getGetPlaylistQueryKey: (playlistId: number) => ["playlist", playlistId],
  useCreateSong: () => ({ mutate: mockCreateSong, isPending: false }),
}));

function renderWithProviders() {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <SongCatalogQuickAdd playlistId={1} />
    </QueryClientProvider>,
  );
}

describe("SongCatalogQuickAdd", () => {
  it("searches the catalog and adds a matching song", async () => {
    renderWithProviders();

    fireEvent.change(screen.getByPlaceholderText("Search songs..."), {
      target: { value: "catalog" },
    });

    await waitFor(() => {
      expect(screen.getByText("Catalog Song")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /add/i }));

    expect(mockCreateSong).toHaveBeenCalledWith(
      expect.objectContaining({
        playlistId: 1,
        data: expect.objectContaining({
          youtubeId: "xyz789",
          title: "Catalog Song",
          artist: "Catalog Artist",
          releaseYear: 2001,
        }),
      }),
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });
});
