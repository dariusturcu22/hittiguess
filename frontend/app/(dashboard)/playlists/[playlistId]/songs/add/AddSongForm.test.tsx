import { describe, expect, test, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { AddSongForm } from "./AddSongForm";

const addSongMock = vi.fn();
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
    useCreateSong: () => ({ mutate: addSongMock, isPending: false }),
    getGetPlaylistQueryKey: (playlistId: number) => ["playlist", playlistId],
  }),
);

function renderForm() {
  render(<AddSongForm backPath="/playlists/1" playlistId={1} />);
}

function checkYoutubeLink(value: string) {
  fireEvent.change(screen.getByLabelText("YouTube Link"), {
    target: { value },
  });
  fireEvent.click(screen.getByRole("button", { name: "Check" }));
}

function advanceToDetailsStep() {
  checkYoutubeLink("https://youtube.com/watch?v=dQw4w9WgXcQ");
  fireEvent.click(screen.getByRole("button", { name: "Enter manually" }));
}

beforeEach(() => {
  addSongMock.mockReset();
  pushMock.mockReset();
  invalidateQueriesMock.mockReset();
});

describe("AddSongForm YouTube link parsing", () => {
  test("rejects text with no extractable video ID", () => {
    renderForm();

    checkYoutubeLink("not a youtube link at all");

    expect(
      screen.getByText(/Couldn't extract a YouTube ID from that link/),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText("Title")).not.toBeInTheDocument();
  });

  test("extracts the video ID from a full watch URL", () => {
    renderForm();

    checkYoutubeLink("https://youtube.com/watch?v=dQw4w9WgXcQ");

    expect(
      screen.getByRole("button", { name: "Get Details with AI" }),
    ).toBeInTheDocument();
  });

  test("extracts the video ID from a youtu.be short link", () => {
    renderForm();

    checkYoutubeLink("https://youtu.be/dQw4w9WgXcQ");

    expect(
      screen.getByRole("button", { name: "Get Details with AI" }),
    ).toBeInTheDocument();
  });

  test("accepts a bare 11-character video ID with no URL wrapper", () => {
    renderForm();

    checkYoutubeLink("dQw4w9WgXcQ");

    expect(
      screen.getByRole("button", { name: "Get Details with AI" }),
    ).toBeInTheDocument();
  });
});

describe("AddSongForm submission validation", () => {
  test("rejects a blank required field without calling the create mutation", () => {
    renderForm();
    advanceToDetailsStep();

    fireEvent.click(screen.getByRole("button", { name: "Add Song" }));

    expect(
      screen.getByText("Fill in the YouTube link, title, and artist."),
    ).toBeInTheDocument();
    expect(addSongMock).not.toHaveBeenCalled();
  });

  test("rejects a release year before the minimum year", () => {
    renderForm();
    advanceToDetailsStep();

    fireEvent.change(screen.getByLabelText("Title"), {
      target: { value: "Some Title" },
    });
    fireEvent.change(screen.getByLabelText("Artist"), {
      target: { value: "Some Artist" },
    });
    fireEvent.change(screen.getByLabelText("Release Year"), {
      target: { value: "999" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add Song" }));

    expect(addSongMock).not.toHaveBeenCalled();
    expect(screen.getByText(/Release year must be between/)).toBeInTheDocument();
  });

  test("rejects a gradient color that is not a 6-character hex value", () => {
    renderForm();
    advanceToDetailsStep();

    fireEvent.change(screen.getByLabelText("Title"), {
      target: { value: "Some Title" },
    });
    fireEvent.change(screen.getByLabelText("Artist"), {
      target: { value: "Some Artist" },
    });
    fireEvent.change(screen.getByLabelText("Release Year"), {
      target: { value: "2000" },
    });
    const [gradientColorTextInput] = screen.getAllByDisplayValue("#8B5CF6");
    fireEvent.change(gradientColorTextInput, {
      target: { value: "not-a-hex-color" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add Song" }));

    expect(
      screen.getByText("Both gradient colors must be a 6-character hex value."),
    ).toBeInTheDocument();
    expect(addSongMock).not.toHaveBeenCalled();
  });

  test("submits the new song with the gradient colors stripped of their leading hash", () => {
    renderForm();
    advanceToDetailsStep();

    fireEvent.change(screen.getByLabelText("Title"), {
      target: { value: "Some Title" },
    });
    fireEvent.change(screen.getByLabelText("Artist"), {
      target: { value: "Some Artist" },
    });
    fireEvent.change(screen.getByLabelText("Release Year"), {
      target: { value: "2000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Add Song" }));

    expect(addSongMock).toHaveBeenCalledWith(
      expect.objectContaining({
        playlistId: 1,
        data: expect.objectContaining({
          youtubeId: "dQw4w9WgXcQ",
          title: "Some Title",
          artist: "Some Artist",
          releaseYear: 2000,
          gradientColor1: "8B5CF6",
          gradientColor2: "EC4899",
        }),
      }),
      expect.anything(),
    );
  });
});
