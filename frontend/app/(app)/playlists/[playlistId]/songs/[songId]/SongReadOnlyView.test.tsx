import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import { SongDTO, SongDTOVerificationStatus } from "@/hooks/models";
import { SongReadOnlyView } from "./SongReadOnlyView";

const mockDeleteSong = vi.fn();
const mockSubmitConfirmation = vi.fn();
const mockSubmitReport = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/hooks/generated/playlist-management/playlist-management", () => ({
  getGetPlaylistQueryKey: (playlistId: number) => ["playlist", playlistId],
  getGetSongQueryKey: (playlistId: number, songId: number) => [
    "song",
    playlistId,
    songId,
  ],
  useDeleteSong: () => ({ mutate: mockDeleteSong, isPending: false }),
}));

vi.mock(
  "@/hooks/generated/community-song-reports/community-song-reports",
  () => ({
    useSubmitConfirmation: () => ({
      mutate: mockSubmitConfirmation,
      isPending: false,
    }),
    useSubmitReport: () => ({ mutate: mockSubmitReport, isPending: false }),
  }),
);

function buildSong(
  verificationStatus: SongDTOVerificationStatus = SongDTOVerificationStatus.NEEDS_REVIEW,
  confidence = "low",
): SongDTO {
  return {
    id: 1,
    artists: [{ name: "Test Artist" }],
    title: "Test Song",
    releaseYear: 1999,
    youtubeId: "abc123",
    verificationStatus,
    confidence,
    needsUserAttention: verificationStatus === SongDTOVerificationStatus.NEEDS_REVIEW && confidence === "low",
  };
}

function renderWithProviders(song: SongDTO) {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <SongReadOnlyView song={song} playlistId={1} backPath="/playlists/1" />
    </QueryClientProvider>,
  );
}

describe("SongReadOnlyView community actions", () => {
  it("shows the confirm affordance for a needs-review song", () => {
    renderWithProviders(buildSong(SongDTOVerificationStatus.NEEDS_REVIEW));

    expect(
      screen.getByRole("button", { name: /is this correct/i }),
    ).toBeInTheDocument();
  });

  it("hides the confirm affordance once a song is verified", () => {
    renderWithProviders(buildSong(SongDTOVerificationStatus.VERIFIED));

    expect(
      screen.queryByRole("button", { name: /is this correct/i }),
    ).not.toBeInTheDocument();
  });

  it("hides the confirm affordance for a medium-confidence pipeline result", () => {
    renderWithProviders(
      buildSong(SongDTOVerificationStatus.NEEDS_REVIEW, "medium"),
    );

    expect(
      screen.queryByRole("button", { name: /is this correct/i }),
    ).not.toBeInTheDocument();
  });

  it("submits a confirmation when the confirm button is clicked", () => {
    renderWithProviders(buildSong(SongDTOVerificationStatus.NEEDS_REVIEW));

    fireEvent.click(screen.getByRole("button", { name: /is this correct/i }));

    expect(mockSubmitConfirmation).toHaveBeenCalledWith(
      { songId: 1 },
      expect.objectContaining({ onSuccess: expect.any(Function) }),
    );
  });
});
