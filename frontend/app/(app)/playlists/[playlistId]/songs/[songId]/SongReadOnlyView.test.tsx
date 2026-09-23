import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

import { SongDTO, SongDTOVerificationStatus } from "@/hooks/models";
import { SongReadOnlyView, validateReportFields } from "./SongReadOnlyView";

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

  it("rejects a report with too little detail", () => {
    renderWithProviders(buildSong(SongDTOVerificationStatus.NEEDS_REVIEW));

    fireEvent.click(screen.getByRole("button", { name: "Report" }));
    fireEvent.change(screen.getByLabelText("What's wrong"), { target: { value: "Bad" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit report" }));

    expect(screen.getByText(/at least 10 characters/)).toBeVisible();
    expect(mockSubmitReport).not.toHaveBeenCalled();
  });

  it("rejects a report with an implausible suggested year", () => {
    renderWithProviders(buildSong(SongDTOVerificationStatus.NEEDS_REVIEW));

    fireEvent.click(screen.getByRole("button", { name: "Report" }));
    fireEvent.change(screen.getByLabelText("What's wrong"), {
      target: { value: "The release year looks wrong" },
    });
    fireEvent.change(screen.getByLabelText(/Suggested correct year/), {
      target: { value: "3000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Submit report" }));

    expect(screen.getByText("Enter a plausible year for the correction.")).toBeVisible();
    expect(mockSubmitReport).not.toHaveBeenCalled();
  });

  it("submits a report with enough detail and a plausible year", () => {
    renderWithProviders(buildSong(SongDTOVerificationStatus.NEEDS_REVIEW));

    fireEvent.click(screen.getByRole("button", { name: "Report" }));
    fireEvent.change(screen.getByLabelText("What's wrong"), {
      target: { value: "The release year looks wrong" },
    });
    fireEvent.change(screen.getByLabelText(/Suggested correct year/), {
      target: { value: "1985" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Submit report" }));

    expect(mockSubmitReport).toHaveBeenCalledWith(
      {
        songId: 1,
        data: {
          message: "The release year looks wrong",
          suggestedCorrectYear: 1985,
          sources: undefined,
        },
      },
      expect.anything(),
    );
  });
});

describe("validateReportFields", () => {
  it("rejects a description under the minimum length", () => {
    expect(validateReportFields("Bad", "")).toMatch(/at least 10 characters/);
  });

  it("rejects a malformed year that parses to NaN", () => {
    expect(validateReportFields("The release year looks wrong", "not-a-year")).toBe(
      "Enter a plausible year for the correction.",
    );
  });

  it("rejects an out-of-range year", () => {
    expect(validateReportFields("The release year looks wrong", "3000")).toBe(
      "Enter a plausible year for the correction.",
    );
  });

  it("accepts a detailed report with no year", () => {
    expect(validateReportFields("The release year looks wrong", "")).toBeNull();
  });

  it("accepts a detailed report with a plausible year", () => {
    expect(validateReportFields("The release year looks wrong", "1985")).toBeNull();
  });
});
