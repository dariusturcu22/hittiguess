import { describe, expect, test, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SongForm } from "./SongForm";
import { SongDTOVerificationStatus } from "@/hooks/models/songDTOVerificationStatus";
import type { SongDTO } from "@/hooks/models";

const updateSongMock = vi.fn();
const pushMock = vi.fn();
const invalidateQueriesMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: invalidateQueriesMock }),
}));

vi.mock(
  "@/hooks/generated/playlist-management/playlist-management",
  () => ({
    useUpdateSong: () => ({ mutate: updateSongMock, isPending: false }),
    getGetPlaylistQueryKey: (playlistId: number) => ["playlist", playlistId],
    getGetSongQueryKey: (playlistId: number, songId: number) => [
      "song",
      playlistId,
      songId,
    ],
  }),
);

function baseSong(): SongDTO {
  return {
    id: 1,
    artists: [{ name: "Original Artist", role: "MAIN" }],
    title: "Original Title",
    releaseYear: 2000,
    youtubeId: "dQw4w9WgXcQ",
    gradientColor1: "8B5CF6",
    gradientColor2: "EC4899",
    tags: [],
    verificationStatus: SongDTOVerificationStatus.UNVERIFIED,
    addedBy: { id: 1, username: "someone" },
  };
}

function renderForm(song: SongDTO = baseSong()) {
  render(<SongForm song={song} backPath="/playlists/1" playlistId={1} />);
}

beforeEach(() => {
  updateSongMock.mockReset();
  pushMock.mockReset();
  invalidateQueriesMock.mockReset();
});

describe("SongForm validation", () => {
  test("rejects a blank required field without calling the update mutation", () => {
    renderForm();

    fireEvent.change(screen.getByLabelText("Title"), {
      target: { value: "" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(
      screen.getByText("Fill in the YouTube ID, title, and artist."),
    ).toBeInTheDocument();
    expect(updateSongMock).not.toHaveBeenCalled();
  });

  test("rejects a YouTube ID that isn't 11 characters", () => {
    renderForm();

    fireEvent.change(screen.getByLabelText("YouTube ID"), {
      target: { value: "too-short" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(
      screen.getByText("YouTube ID must be 11 characters."),
    ).toBeInTheDocument();
    expect(updateSongMock).not.toHaveBeenCalled();
  });

  test("rejects a release year before the minimum year", () => {
    renderForm();

    fireEvent.change(screen.getByLabelText("Release Year"), {
      target: { value: "999" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(updateSongMock).not.toHaveBeenCalled();
    expect(screen.getByText(/Release year must be between/)).toBeInTheDocument();
  });

  test("rejects a release year after the current year", () => {
    renderForm();

    const nextYear = new Date().getFullYear() + 1;
    fireEvent.change(screen.getByLabelText("Release Year"), {
      target: { value: String(nextYear) },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(updateSongMock).not.toHaveBeenCalled();
  });

  test("rejects a gradient color that is not a 6-character hex value", () => {
    renderForm();

    const [gradientColorTextInput] = screen.getAllByDisplayValue("#8B5CF6");
    fireEvent.change(gradientColorTextInput, {
      target: { value: "not-a-hex-color" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(
      screen.getByText("Both gradient colors must be a 6-character hex value."),
    ).toBeInTheDocument();
    expect(updateSongMock).not.toHaveBeenCalled();
  });

  test("submits the update with the gradient colors stripped of their leading hash", () => {
    renderForm();

    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }));

    expect(updateSongMock).toHaveBeenCalledWith(
      expect.objectContaining({
        playlistId: 1,
        songId: 1,
        data: expect.objectContaining({
          gradientColor1: "8B5CF6",
          gradientColor2: "EC4899",
        }),
      }),
      expect.anything(),
    );
  });
});
